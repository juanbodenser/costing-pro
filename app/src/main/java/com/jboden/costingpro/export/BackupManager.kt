package com.jboden.costingpro.export

import android.content.Context
import android.net.Uri
import com.jboden.costingpro.data.BackupDao
import com.jboden.costingpro.data.PhotoStore
import com.jboden.costingpro.data.SettingsStore
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Exports all the data of the app to a .zip and restores it, through files the user picks
 * with the Storage Access Framework.
 */
class BackupManager(
    private val context: Context,
    private val dao: BackupDao,
    private val photos: PhotoStore,
    private val settings: SettingsStore,
    private val now: () -> Long = System::currentTimeMillis,
) {

    /** Writes the backup into [target]. @throws IOException if it cannot be written. */
    suspend fun export(target: Uri) = withContext(Dispatchers.IO) {
        val data = BackupData(
            exportedAt = now(),
            defaultVatPct = settings.defaultVatPct.value,
            ingredients = dao.ingredients().map { it.toRow() },
            recipes = dao.recipes().map { it.toRow() },
            lines = dao.lines().map { it.toRow() },
            extras = dao.extras().map { it.toRow() },
        )
        val output = context.contentResolver.openOutputStream(target, "wt")
            ?: throw IOException("Cannot open $target")
        output.use { BackupArchive.write(it, data, photos::file) }
    }

    /**
     * Imports the backup in [source]. The file is read and checked completely before anything
     * is touched.
     *
     * - [ImportMode.REPLACE]: all the data, photos and default VAT become those of the backup.
     * - [ImportMode.ADD]: the ingredients and recipes of the backup are added to the current
     *   ones, which stay as they are. An ingredient with a name that already exists is not
     *   added: the imported recipes use the existing one. The default VAT does not change.
     *
     * @throws InvalidBackupException if it is not a valid backup; nothing changes then.
     */
    suspend fun import(source: Uri, mode: ImportMode = ImportMode.REPLACE) = withContext(Dispatchers.IO) {
        val staging = File(context.cacheDir, "restore").apply { deleteRecursively() }
        try {
            val input = try {
                context.contentResolver.openInputStream(source)
            } catch (e: IOException) {
                throw InvalidBackupException("Cannot open $source", e)
            } catch (e: SecurityException) {
                throw InvalidBackupException("Cannot open $source", e)
            } ?: throw InvalidBackupException("Cannot open $source")
            val data = input.use { BackupArchive.read(it, staging) }
            when (mode) {
                ImportMode.REPLACE -> replace(data, staging)
                ImportMode.ADD -> add(data, staging)
            }
        } finally {
            staging.deleteRecursively()
        }
    }

    private suspend fun replace(data: BackupData, staging: File) {
        writeToDatabase {
            dao.replaceAll(
                ingredients = data.ingredients.map { it.toEntity() },
                recipes = data.recipes.map { it.toEntity() },
                lines = data.lines.map { it.toEntity() },
                extras = data.extras.map { it.toEntity() },
            )
        }
        settings.setDefaultVatPct(data.defaultVatPct)
        replacePhotos(File(staging, PhotoStore.DIR))
    }

    /**
     * Each imported photo is moved in under a new name first, so it never overwrites a photo
     * already there. If the data cannot be added, the moved photos are removed again.
     */
    private suspend fun add(data: BackupData, staging: File) {
        val moved = mutableListOf<File>()
        val recipes = data.recipes.map { row ->
            val newPath = row.photoPath?.let { movePhoto(File(staging, it)) }
            newPath?.let { moved += photos.file(it) }
            row.toEntity().copy(photoPath = newPath)
        }
        try {
            writeToDatabase {
                dao.addAll(
                    ingredients = data.ingredients.map { it.toEntity() },
                    recipes = recipes,
                    lines = data.lines.map { it.toEntity() },
                    extras = data.extras.map { it.toEntity() },
                )
            }
        } catch (e: InvalidBackupException) {
            moved.forEach { it.delete() }
            throw e
        }
    }

    /** Moves a photo of the backup into the photo folder under a new name. Null if it cannot be. */
    private fun movePhoto(source: File): String? {
        val path = photos.newPath()
        val target = photos.file(path)
        target.parentFile?.mkdirs()
        if (source.renameTo(target)) return path
        return try {
            source.copyTo(target)
            path
        } catch (e: IOException) {
            target.delete()
            null
        }
    }

    private suspend fun writeToDatabase(write: suspend () -> Unit) {
        try {
            write()
        } catch (e: android.database.SQLException) {
            // A constraint the checks did not catch: the transaction rolled back.
            throw InvalidBackupException("The data does not fit the database", e)
        }
    }

    /** The photos of the backup take the place of the current ones. */
    private fun replacePhotos(restored: File) {
        val current = photos.directory()
        current.deleteRecursively()
        if (!restored.isDirectory) return
        if (!restored.renameTo(current)) {
            // cacheDir and filesDir may be on different volumes: copy instead.
            // A photo that cannot be copied is skipped: its recipe simply shows no photo,
            // and the data, already restored, stays as it is.
            restored.copyRecursively(current, overwrite = true) { _, _ -> OnErrorAction.SKIP }
        }
    }
}
