package com.jboden.costingpro.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jboden.costingpro.AppContainer
import com.jboden.costingpro.data.Ingredient
import com.jboden.costingpro.data.PhotoStore
import com.jboden.costingpro.data.SampleData
import com.jboden.costingpro.data.SettingsStore
import com.jboden.costingpro.ui.InMemoryDatabaseRule
import com.jboden.costingpro.ui.recipes.RecipeSummaryLogic
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Phase 9: export, wipe, import and everything comes back, photos included; and the recipe PDF. */
@RunWith(AndroidJUnit4::class)
class BackupAndPdfTest {

    @get:Rule
    val databaseRule = InMemoryDatabaseRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var container: AppContainer
    private lateinit var settings: SettingsStore
    private val photoBytes = ByteArray(2048) { (it % 251).toByte() }

    @Before
    fun setUp() {
        settings = SettingsStore(initialDefaultVatPct = 21.0)
        container = AppContainer({ databaseRule.database }, { PhotoStore(context) }, { settings })
        container.photoStore.directory().deleteRecursively()
        runBlocking { SampleData.insertInto(databaseRule.database) }
    }

    private fun cacheUri(name: String): Pair<File, Uri> {
        val file = File(context.cacheDir, name).apply { delete() }
        return file to Uri.fromFile(file)
    }

    @Test
    fun exportWipeImportBringsEverythingBackWithPhotos() = runBlocking<Unit> {
        val recipes = container.recipeRepository
        val cookies = recipes.getAll().first { it.recipe.name == SampleData.COOKIES_NAME }
        val photoPath = container.photoStore.newPath()
        container.photoStore.file(photoPath).apply { parentFile?.mkdirs() }.writeBytes(photoBytes)
        recipes.setPhoto(cookies.recipe.id, photoPath)

        val dao = databaseRule.database.backupDao()
        val before = listOf(dao.ingredients(), dao.recipes(), dao.lines(), dao.extras())

        val (file, uri) = cacheUri("backup-test.zip")
        container.backupManager.export(uri)
        assertTrue(file.length() > 0)

        // Wipe: no data, no photos, another VAT.
        dao.replaceAll(emptyList(), emptyList(), emptyList(), emptyList())
        container.photoStore.directory().deleteRecursively()
        settings.setDefaultVatPct(4.0)
        assertTrue(recipes.getAll().isEmpty())

        container.backupManager.import(uri)

        assertEquals(before, listOf(dao.ingredients(), dao.recipes(), dao.lines(), dao.extras()))
        assertEquals(21.0, settings.defaultVatPct.value, 0.0)
        val restored = recipes.get(cookies.recipe.id)!!
        assertEquals(photoPath, restored.recipe.photoPath)
        assertArrayEquals(photoBytes, container.photoStore.file(photoPath).readBytes())
    }

    @Test
    fun anInvalidFileChangesNothing() = runBlocking<Unit> {
        val dao = databaseRule.database.backupDao()
        val before = dao.ingredients()
        val (file, uri) = cacheUri("not-a-backup.zip")
        file.writeText("hello")

        try {
            container.backupManager.import(uri)
            fail("Expected an invalid backup")
        } catch (e: InvalidBackupException) {
            // Expected.
        }

        assertEquals(before, dao.ingredients())
        assertEquals(21.0, settings.defaultVatPct.value, 0.0)
    }

    @Test
    fun theRecipePdfIsOneA4PageWithContent() = runBlocking<Unit> {
        val all = container.recipeRepository.getAll()
        val catalogue: Map<Long, Ingredient> = databaseRule.database.backupDao().ingredients().associateBy { it.id }
        val cookies = all.first { it.recipe.name == SampleData.COOKIES_NAME }
        val photoPath = container.photoStore.newPath()
        writeTestPhoto(container.photoStore.file(photoPath))
        container.recipeRepository.setPhoto(cookies.recipe.id, photoPath)
        val withPhoto = container.recipeRepository.get(cookies.recipe.id)!!
        val summary = RecipeSummaryLogic.summarize(withPhoto, catalogue, all.associateBy { it.recipe.id })

        val (file, uri) = cacheUri("recipe-test.pdf")
        container.recipePdf.export(summary, uri)

        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                assertEquals(1, renderer.pageCount)
                renderer.openPage(0).use { page ->
                    assertEquals(595, page.width)
                    assertEquals(842, page.height)
                    val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    assertFalse("The page is blank", isBlank(bitmap))
                    // Kept for a look by hand: adb shell run-as com.jboden.costingpro cat cache/recipe-test.png
                    File(context.cacheDir, "recipe-test.png").outputStream().use {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                }
            }
        }
    }

    /** A gradient image, so the photo is visible in the rendered page. */
    private fun writeTestPhoto(target: File) {
        target.parentFile?.mkdirs()
        val bitmap = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
        for (x in 0 until 800) for (y in 0 until 600 step 4) {
            val color = Color.rgb(200 - x / 8, 120 + y / 10, 60 + x / 10)
            for (dy in 0 until 4) bitmap.setPixel(x, y + dy, color)
        }
        target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
    }

    private fun isBlank(bitmap: Bitmap): Boolean {
        for (x in 0 until bitmap.width step 10) for (y in 0 until bitmap.height step 10) {
            if (bitmap.getPixel(x, y) != Color.WHITE) return false
        }
        return true
    }
}
