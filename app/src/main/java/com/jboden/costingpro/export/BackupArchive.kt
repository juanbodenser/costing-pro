package com.jboden.costingpro.export

import com.jboden.costingpro.domain.RecipeGraph
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** The file chosen is not a backup of this app, or it is damaged. Nothing has been changed. */
class InvalidBackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * The .zip of a backup: `backup.json` with all the data, and each recipe photo under the
 * same relative path it has in the files directory (`photos/<name>.jpg`).
 *
 * Pure JVM: it only deals with streams and files, so it is tested without a device.
 */
object BackupArchive {

    const val DATA_ENTRY = "backup.json"

    private const val PHOTO_DIR = "photos/"

    /** A photo path we accept from a backup: one plain file name inside photos/, nothing that climbs out. */
    private val safePhotoPath = Regex("""photos/[A-Za-z0-9_-][A-Za-z0-9._-]*""")

    /** Larger than any real backup.json; guards against a hostile or broken file. */
    private const val MAX_DATA_BYTES = 32L * 1024 * 1024

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Writes the backup to [output] (which is not closed). Each photo path of a recipe is looked
     * up with [photoFile]; a missing file is left out and the recipe is restored without a photo.
     */
    fun write(output: OutputStream, data: BackupData, photoFile: (String) -> File) {
        val zip = ZipOutputStream(output)
        zip.putNextEntry(ZipEntry(DATA_ENTRY))
        zip.write(json.encodeToString(BackupData.serializer(), data).toByteArray(Charsets.UTF_8))
        zip.closeEntry()
        data.recipes.mapNotNull { it.photoPath }
            .distinct()
            .filter { safePhotoPath.matches(it) }
            .forEach { path ->
                val file = photoFile(path)
                if (file.isFile) {
                    zip.putNextEntry(ZipEntry(path))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        zip.finish()
    }

    /**
     * Reads and checks a backup from [input] (which is not closed). The photos are extracted into
     * [photoTargetDir], which plays the part of the files directory: a photo ends up at
     * `photoTargetDir/photos/<name>`. A recipe whose photo is not in the archive gets no photo.
     *
     * @throws InvalidBackupException if it is not a valid backup.
     */
    fun read(input: InputStream, photoTargetDir: File): BackupData {
        var data: BackupData? = null
        val photos = mutableSetOf<String>()
        try {
            val zip = ZipInputStream(input)
            while (true) {
                val entry = zip.nextEntry ?: break
                when {
                    entry.isDirectory -> Unit
                    entry.name == DATA_ENTRY -> data = parse(zip.readLimited())
                    safePhotoPath.matches(entry.name) -> {
                        val target = File(photoTargetDir, entry.name)
                        target.parentFile?.mkdirs()
                        target.outputStream().use { zip.copyTo(it) }
                        photos += entry.name
                    }
                    // Anything else is not ours: ignored, never written to disk.
                    else -> Unit
                }
                zip.closeEntry()
            }
        } catch (e: IOException) {
            throw InvalidBackupException("The file is not a readable .zip", e)
        } catch (e: IllegalArgumentException) {
            // ZipInputStream throws it for malformed entry names.
            throw InvalidBackupException("The file is not a readable .zip", e)
        }
        val read = data ?: throw InvalidBackupException("$DATA_ENTRY is missing")
        validate(read)
        return read.copy(recipes = read.recipes.map { if (it.photoPath in photos) it else it.copy(photoPath = null) })
    }

    private fun parse(bytes: ByteArray): BackupData = try {
        json.decodeFromString(BackupData.serializer(), bytes.toString(Charsets.UTF_8))
    } catch (e: SerializationException) {
        throw InvalidBackupException("$DATA_ENTRY cannot be read", e)
    } catch (e: IllegalArgumentException) {
        throw InvalidBackupException("$DATA_ENTRY cannot be read", e)
    }

    private fun InputStream.readLimited(): ByteArray {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(8 * 1024)
        var total = 0L
        while (true) {
            val count = read(chunk)
            if (count < 0) break
            total += count
            if (total > MAX_DATA_BYTES) throw InvalidBackupException("$DATA_ENTRY is too large")
            buffer.write(chunk, 0, count)
        }
        return buffer.toByteArray()
    }

    /** The checks the database would make, done first so that a bad file gives a clear error. */
    private fun validate(data: BackupData) {
        fun check(condition: Boolean, message: String) {
            if (!condition) throw InvalidBackupException(message)
        }
        check(data.format in 1..BackupData.CURRENT_FORMAT, "Unsupported backup format ${data.format}")
        check(data.defaultVatPct in 0.0..100.0, "Default VAT out of range")
        val ingredientIds = data.ingredients.map { it.id }
        val recipeIds = data.recipes.map { it.id }
        check(ingredientIds.size == ingredientIds.toSet().size, "Repeated ingredient id")
        check(recipeIds.size == recipeIds.toSet().size, "Repeated recipe id")
        check(
            data.ingredients.map { it.name.lowercase() }.toSet().size == data.ingredients.size,
            "Repeated ingredient name",
        )
        val ingredients = ingredientIds.toSet()
        val recipes = recipeIds.toSet()
        data.lines.forEach { line ->
            check(line.recipeId in recipes, "Line of a missing recipe")
            check((line.ingredientId == null) != (line.subRecipeId == null), "Line without exactly one source")
            line.ingredientId?.let { check(it in ingredients, "Line with a missing ingredient") }
            line.subRecipeId?.let { check(it in recipes && it != line.recipeId, "Line with a wrong sub-recipe") }
        }
        data.extras.forEach { check(it.recipeId in recipes, "Extra of a missing recipe") }
        check((ingredientIds + recipeIds).all { it > 0 }, "Id out of range")
        // A recipe that contains itself, directly or not, would make its cost endless.
        val subRecipesOf = data.lines
            .filter { it.subRecipeId != null }
            .groupBy({ it.recipeId }, { it.subRecipeId!! })
            .mapValues { it.value.toSet() }
        subRecipesOf.forEach { (recipe, subs) ->
            subs.forEach { sub -> check(!RecipeGraph.wouldCreateCycle(recipe, sub, subRecipesOf), "Sub-recipe loop") }
        }
    }
}
