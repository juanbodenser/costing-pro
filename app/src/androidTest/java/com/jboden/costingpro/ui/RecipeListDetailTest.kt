package com.jboden.costingpro.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jboden.costingpro.AppContainer
import com.jboden.costingpro.data.CostingDatabase
import com.jboden.costingpro.data.ExtraCost
import com.jboden.costingpro.data.Ingredient
import com.jboden.costingpro.data.PhotoStore
import com.jboden.costingpro.data.Recipe
import com.jboden.costingpro.data.RecipeLine
import com.jboden.costingpro.domain.ExtraCostPer
import com.jboden.costingpro.domain.MeasureUnit
import com.jboden.costingpro.domain.PricingMode
import com.jboden.costingpro.ui.recipes.RecipeDetailViewModel
import com.jboden.costingpro.ui.theme.CostingProTheme
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.Test
import org.junit.runner.RunWith

/** Phase 5: the recipe list, the detail screen and the photo, over an in-memory database and the real photo store. */
@RunWith(AndroidJUnit4::class)
class RecipeListDetailTest {

    private val databaseRule = InMemoryDatabaseRule()
    private val compose = createComposeRule()

    // The database is closed last, after the screens and the view models have stopped using it.
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(databaseRule).around(compose)

    private val database get() = databaseRule.database

    private val appContext = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var container: AppContainer
    private lateinit var photos: PhotoStore
    private var cookiesId = 0L

    @Before
    fun setUp() {
        photos = PhotoStore(appContext)
        container = AppContainer(createDatabase = { database }, createPhotoStore = { photos })
        runBlocking {
            fun ingredient(name: String, price: Double, quantity: Double, unit: MeasureUnit) =
                Ingredient(name = name, purchasePrice = price, purchaseQuantity = quantity, purchaseUnit = unit)
            val ids = database.ingredientDao().insertAll(
                listOf(
                    ingredient("Harina", 22.50, 25.0, MeasureUnit.KG),
                    ingredient("Mantequilla", 8.00, 1.0, MeasureUnit.KG),
                    ingredient("Azúcar", 1.20, 1.0, MeasureUnit.KG),
                    ingredient("Chocolate", 10.00, 1.0, MeasureUnit.KG),
                    ingredient("Huevos", 3.00, 12.0, MeasureUnit.UD),
                ),
            )
            fun line(index: Int, quantity: Double, unit: MeasureUnit) =
                RecipeLine(ingredientId = ids[index], quantity = quantity, unit = unit)
            cookiesId = container.recipeRepository.save(
                recipe = Recipe(
                    name = "Galletas de chocolate",
                    yieldQuantity = 40.0,
                    yieldUnit = MeasureUnit.UD,
                    pricingMode = PricingMode.PRICE,
                    pricingValue = 1.0,
                ),
                lines = listOf(
                    line(0, 500.0, MeasureUnit.G),
                    line(1, 250.0, MeasureUnit.G),
                    line(2, 200.0, MeasureUnit.G),
                    line(3, 200.0, MeasureUnit.G),
                    line(4, 2.0, MeasureUnit.UD),
                ),
                extras = listOf(ExtraCost(name = "Bolsa", amount = 0.05, per = ExtraCostPer.UNIT)),
            )
        }
        compose.setContent {
            CostingProTheme { CostingProApp(container) }
        }
    }

    @After
    fun tearDown() {
        File(appContext.filesDir, "photos").deleteRecursively()
    }

    private fun waitForText(text: String) {
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun openCookies() {
        waitForText("Galletas de chocolate")
        compose.onNodeWithText("Galletas de chocolate").performClick()
        waitForText("Desglose")
    }

    /** A small image file the photo picker could have returned. */
    private fun pickedImage(): Uri {
        val file = File(appContext.cacheDir, "picked-${System.nanoTime()}.png")
        val bitmap = Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFFC98A2B.toInt()) }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return Uri.fromFile(file)
    }

    private fun detailViewModel() =
        databaseRule.track(RecipeDetailViewModel(container.recipeRepository, container.ingredientRepository, photos, cookiesId))

    private fun storedPhotoPath(): String? =
        runBlocking { container.recipeRepository.get(cookiesId) }?.recipe?.photoPath

    private fun waitForPhotoPath() {
        compose.waitUntil(5_000) { storedPhotoPath() != null }
    }

    // ---------- List ----------

    @Test
    fun listShowsCostPriceAndMarginOfEachRecipe() {
        waitForText("Galletas de chocolate")

        compose.onNodeWithText("Coste 0,18 € · Precio 1,00 €").assertIsDisplayed()
        compose.onNodeWithText("82,0 %").assertIsDisplayed()
        compose.onNodeWithText("Bueno").assertIsDisplayed()
    }

    @Test
    fun searchFindsRecipesIgnoringCaseAndAccents() {
        runBlocking {
            val flour = database.ingredientDao().getByName("Harina")!!
            container.recipeRepository.save(
                recipe = Recipe(name = "Crema de limón", yieldQuantity = 1.0, yieldUnit = MeasureUnit.L),
                lines = listOf(RecipeLine(ingredientId = flour.id, quantity = 10.0, unit = MeasureUnit.G)),
                extras = emptyList(),
            )
        }
        waitForText("Crema de limón")

        compose.onNodeWithText("Buscar escandallo").performTextInput("LIMON")

        compose.waitUntil(5_000) { compose.onAllNodesWithText("Galletas de chocolate").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("Crema de limón").assertIsDisplayed()
    }

    // ---------- Detail ----------

    @Test
    fun detailShowsTheSummaryAndTheBreakdownWithShares() {
        openCookies()

        compose.onNodeWithText("1,10 €").assertIsDisplayed() // PVP con IVA
        compose.onNodeWithText("0,82 €").assertIsDisplayed() // profit per unit
        compose.onNodeWithText("Harina").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("0,45 €").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("500 g").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Bolsa").performScrollTo().assertIsDisplayed()
        // Mantequilla, Chocolate and the bag each cost 2,00 € of 7,19 €.
        assertEquals(3, compose.onAllNodesWithText("27,8 %").fetchSemanticsNodes().size)
    }

    @Test
    fun editFromTheDetailOpensTheEditorWithTheRecipe() {
        openCookies()

        compose.onNodeWithContentDescription("Editar").performClick()

        waitForText("Editar escandallo")
        compose.onNodeWithText("Galletas de chocolate").assertIsDisplayed()
    }

    @Test
    fun duplicateMakesACopyWithoutPhotoAndOpensIt() {
        openCookies()

        compose.onNodeWithContentDescription("Más acciones").performClick()
        compose.onNodeWithText("Duplicar").performClick()

        waitForText("Galletas de chocolate (copia)")
        val all = runBlocking { container.recipeRepository.getAll() }
        assertEquals(2, all.size)
        val copy = all.single { it.recipe.name == "Galletas de chocolate (copia)" }
        assertEquals(5, copy.lines.size)
        assertEquals("Bolsa", copy.extras.single().name)
        assertNull(copy.recipe.photoPath)
    }

    @Test
    fun deleteAsksForConfirmationAndRemovesTheRecipeAndItsPhoto() {
        val viewModel = detailViewModel()
        viewModel.onGalleryPicked(pickedImage())
        waitForPhotoPath()
        val photoFile = photos.file(storedPhotoPath()!!)
        assertTrue(photoFile.exists())
        openCookies()

        compose.onNodeWithContentDescription("Más acciones").performClick()
        compose.onNodeWithText("Borrar").performClick()
        waitForText("¿Borrar escandallo?")
        compose.onAllNodesWithText("Borrar").onLast().performClick()

        waitForText("Aún no hay escandallos. Pulsa + para crear el primero.")
        assertTrue(runBlocking { container.recipeRepository.getAll() }.isEmpty())
        assertFalse(photoFile.exists())
    }

    // ---------- Photo ----------

    /** The criterion of the phase in PLAN.md: the photo is still there when the app is reopened. */
    @Test
    fun aPickedPhotoIsStoredInternallyAndSurvivesReopeningTheApp() {
        val viewModel = detailViewModel()

        viewModel.onGalleryPicked(pickedImage())
        waitForPhotoPath()

        // The database keeps a path relative to the files directory, not an absolute one.
        val path = storedPhotoPath()!!
        assertTrue(path, path.startsWith("photos/"))
        assertTrue(photos.file(path).startsWith(appContext.filesDir))

        // "Reopening": a new photo store and a new activity read the same path.
        val reopened = PhotoStore(appContext)
        assertTrue(reopened.hasPhoto(path))
        val bitmap = BitmapFactory.decodeFile(reopened.file(path).path)
        assertNotNull(bitmap)
        assertEquals(64, bitmap.width)
        assertEquals(48, bitmap.height)
    }

    @Test
    fun theDetailShowsThePhotoInsteadOfTheAddButton() {
        openCookies()
        compose.onNodeWithContentDescription("Más acciones").performClick()
        compose.onNodeWithText("Añadir foto").assertIsDisplayed()
        compose.onNodeWithText("Añadir foto").performClick()
        compose.onNodeWithText("Cancelar").performClick()

        detailViewModel().onGalleryPicked(pickedImage())

        compose.waitUntil(5_000) {
            compose.onAllNodes(hasContentDescription("Foto de «Galletas de chocolate»")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Foto de «Galletas de chocolate»").assertIsDisplayed()
        compose.onNodeWithContentDescription("Más acciones").performClick()
        assertTrue(compose.onAllNodesWithText("Añadir foto").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithText("Cambiar foto").assertIsDisplayed()
    }

    @Test
    fun changingThePhotoDeletesThePreviousFile() {
        val viewModel = detailViewModel()
        viewModel.onGalleryPicked(pickedImage())
        waitForPhotoPath()
        val first = storedPhotoPath()!!

        viewModel.onGalleryPicked(pickedImage())
        compose.waitUntil(5_000) { storedPhotoPath() != first }

        assertNotEquals(first, storedPhotoPath())
        assertTrue(photos.hasPhoto(storedPhotoPath()!!))
        assertFalse(photos.file(first).exists())
    }

    @Test
    fun removingThePhotoClearsThePathAndDeletesTheFile() {
        val viewModel = detailViewModel()
        viewModel.onGalleryPicked(pickedImage())
        waitForPhotoPath()
        val path = storedPhotoPath()!!

        viewModel.removePhoto()
        compose.waitUntil(5_000) { storedPhotoPath() == null }

        assertFalse(photos.file(path).exists())
    }

    @Test
    fun aPhotoTakenWithTheCameraIsStoredAndACancelledOneIsDiscarded() {
        val viewModel = detailViewModel()

        // Cancelled: the camera leaves the empty file we created, which must be removed.
        val (cancelledPath, cancelledUri) = viewModel.newCameraPhoto()
        assertTrue(photos.file(cancelledPath).exists())
        assertEquals("content", cancelledUri.scheme)
        viewModel.onCameraResult(cancelledPath, success = false)
        compose.waitUntil(5_000) { !photos.file(cancelledPath).exists() }
        assertNull(storedPhotoPath())

        // Taken: the camera has written the picture into the file.
        val (takenPath, _) = viewModel.newCameraPhoto()
        photos.file(takenPath).outputStream().use {
            Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 90, it)
        }
        viewModel.onCameraResult(takenPath, success = true)
        waitForPhotoPath()

        assertEquals(takenPath, storedPhotoPath())
        assertTrue(photos.hasPhoto(takenPath))
    }

    @Test
    fun anUnreadableImageLeavesTheRecipeWithoutPhoto() {
        val viewModel = detailViewModel()

        viewModel.onGalleryPicked(Uri.fromFile(File(appContext.cacheDir, "does-not-exist.png")))

        // Collecting the state is what makes it update.
        runBlocking { withTimeout(5_000) { viewModel.uiState.first { it.photoFailed } } }
        assertNull(storedPhotoPath())
        assertEquals(0, File(appContext.filesDir, "photos").listFiles().orEmpty().size)
    }
}
