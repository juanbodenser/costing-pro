package com.jboden.costingpro.export

import com.jboden.costingpro.domain.ExtraCostPer
import com.jboden.costingpro.domain.MeasureUnit
import com.jboden.costingpro.domain.PricingMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackupArchiveTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val data = BackupData(
        exportedAt = 1_000,
        defaultVatPct = 21.0,
        ingredients = listOf(
            IngredientRow(3, "Harina", 22.5, 25.0, MeasureUnit.KG, 0.0, 1, 10),
            IngredientRow(8, "Huevo", 3.0, 12.0, MeasureUnit.UD, 5.0, 4, 20),
        ),
        recipes = listOf(
            RecipeRow(5, "Masa", 10.0, MeasureUnit.UD, PricingMode.PRICE, 0.0, 10.0, null, null, 1, 2),
            RecipeRow(
                9, "Galletas", 40.0, MeasureUnit.UD, PricingMode.MARKUP, 300.0, 10.0,
                "photos/abc.jpg", "Horno a 180", 3, 4,
            ),
        ),
        lines = listOf(
            LineRow(1, 5, 0, ingredientId = 3, quantity = 500.0, unit = MeasureUnit.G, wastePct = 0.0),
            LineRow(2, 5, 1, ingredientId = 8, quantity = 2.0, unit = MeasureUnit.UD, wastePct = 0.0),
            LineRow(3, 9, 0, subRecipeId = 5, quantity = 10.0, unit = MeasureUnit.UD, wastePct = 10.0),
        ),
        extras = listOf(ExtraRow(4, 9, "Bolsa", 0.05, ExtraCostPer.UNIT)),
    )

    private val photoBytes = byteArrayOf(1, 2, 3, 4, 5)

    private fun export(data: BackupData = this.data): ByteArray {
        val files = folder.newFolder("files")
        File(files, "photos").mkdirs()
        File(files, "photos/abc.jpg").writeBytes(photoBytes)
        val out = ByteArrayOutputStream()
        BackupArchive.write(out, data) { File(files, it) }
        return out.toByteArray()
    }

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun entriesOf(bytes: ByteArray): List<String> {
        val names = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) names += (zip.nextEntry ?: break).name
        }
        return names
    }

    private fun assertInvalid(bytes: ByteArray) {
        try {
            BackupArchive.read(ByteArrayInputStream(bytes), folder.newFolder())
            fail("Expected an invalid backup")
        } catch (e: InvalidBackupException) {
            // Expected.
        }
    }

    @Test
    fun `a backup reads back exactly, with its photo`() {
        val bytes = export()
        assertEquals(listOf("backup.json", "photos/abc.jpg"), entriesOf(bytes))

        val target = folder.newFolder("restore")
        val read = BackupArchive.read(ByteArrayInputStream(bytes), target)

        assertEquals(data, read)
        assertArrayEquals(photoBytes, File(target, "photos/abc.jpg").readBytes())
    }

    @Test
    fun `a photo missing on disk is left out and the recipe comes back without photo`() {
        val bytes = export(data.copy(recipes = data.recipes.map { it.copy(photoPath = it.photoPath?.let { "photos/gone.jpg" }) }))
        assertEquals(listOf("backup.json"), entriesOf(bytes))

        val read = BackupArchive.read(ByteArrayInputStream(bytes), folder.newFolder())

        assertNull(read.recipes.single { it.id == 9L }.photoPath)
    }

    @Test
    fun `entries outside photos are never written`() {
        val json = entriesJson()
        val target = folder.newFolder("restore")
        val bytes = zip(
            "backup.json" to json,
            "../evil.txt" to byteArrayOf(9),
            "photos/../../evil2.txt" to byteArrayOf(9),
            "other/file.txt" to byteArrayOf(9),
        )

        BackupArchive.read(ByteArrayInputStream(bytes), target)

        assertFalse(File(target.parentFile, "evil.txt").exists())
        assertFalse(File(target.parentFile, "evil2.txt").exists())
        assertFalse(File(target, "other").exists())
    }

    @Test
    fun `unknown keys from a newer app are ignored`() {
        val json = entriesJson().toString(Charsets.UTF_8).replaceFirst("{", "{\"futureField\": 1,").toByteArray()
        val read = BackupArchive.read(ByteArrayInputStream(zip("backup.json" to json)), folder.newFolder())
        assertEquals(2, read.ingredients.size)
    }

    @Test
    fun `a file that is not a backup is rejected`() {
        assertInvalid("hello".toByteArray())
        assertInvalid(zip("photos/abc.jpg" to photoBytes))
        assertInvalid(zip("backup.json" to "{ not json".toByteArray()))
    }

    @Test
    fun `broken references are rejected`() {
        fun invalid(broken: BackupData) = assertInvalid(zip("backup.json" to jsonOf(broken)))

        invalid(data.copy(lines = data.lines + LineRow(9, 5, 2, ingredientId = 99, quantity = 1.0, unit = MeasureUnit.G, wastePct = 0.0)))
        invalid(data.copy(lines = data.lines + LineRow(9, 5, 2, quantity = 1.0, unit = MeasureUnit.G, wastePct = 0.0)))
        invalid(data.copy(lines = data.lines + LineRow(9, 5, 2, 3, 5, 1.0, MeasureUnit.G, 0.0)))
        invalid(data.copy(extras = data.extras + ExtraRow(9, 77, "X", 1.0, ExtraCostPer.BATCH)))
        invalid(data.copy(ingredients = data.ingredients + data.ingredients[0].copy(id = 30, name = "HARINA")))
        invalid(data.copy(format = BackupData.CURRENT_FORMAT + 1))
        invalid(data.copy(defaultVatPct = 150.0))
        // Masa (5) is used by 9; 9 inside Masa closes a loop.
        invalid(data.copy(lines = data.lines + LineRow(9, 5, 2, subRecipeId = 9, quantity = 1.0, unit = MeasureUnit.UD, wastePct = 0.0)))
    }

    @Test
    fun `the backup json is readable text`() {
        val text = entriesJson().toString(Charsets.UTF_8)
        assertTrue(text.contains("\"name\": \"Galletas\""))
        assertTrue(text.contains("\"purchaseUnit\": \"KG\""))
    }

    private fun jsonOf(data: BackupData): ByteArray {
        val bytes = ByteArrayOutputStream().also { BackupArchive.write(it, data) { File("none") } }.toByteArray()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == BackupArchive.DATA_ENTRY) return zip.readBytes()
            }
        }
        error("No data entry")
    }

    private fun entriesJson() = jsonOf(data)
}
