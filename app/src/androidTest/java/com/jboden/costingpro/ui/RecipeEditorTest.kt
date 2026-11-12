package com.jboden.costingpro.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jboden.costingpro.AppContainer
import com.jboden.costingpro.data.CostingDatabase
import com.jboden.costingpro.data.PhotoStore
import com.jboden.costingpro.data.ExtraCost
import com.jboden.costingpro.data.Ingredient
import com.jboden.costingpro.data.Recipe
import com.jboden.costingpro.data.RecipeLine
import com.jboden.costingpro.domain.ExtraCostPer
import com.jboden.costingpro.domain.MeasureUnit
import com.jboden.costingpro.domain.PricingMode
import com.jboden.costingpro.ui.recipes.RecipeEditViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Phase 4: the recipe editor, driven through the real screens over an in-memory database. */
@RunWith(AndroidJUnit4::class)
class RecipeEditorTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var database: CostingDatabase
    private lateinit var container: AppContainer

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            CostingDatabase::class.java,
        ).build()
        container = AppContainer(
            createDatabase = { database },
            createPhotoStore = { PhotoStore(ApplicationProvider.getApplicationContext()) },
        )
        runBlocking {
            fun ingredient(name: String, price: Double, quantity: Double, unit: MeasureUnit) =
                Ingredient(name = name, purchasePrice = price, purchaseQuantity = quantity, purchaseUnit = unit)
            database.ingredientDao().insertAll(
                listOf(
                    ingredient("Harina", 22.50, 25.0, MeasureUnit.KG),
                    ingredient("Mantequilla", 8.00, 1.0, MeasureUnit.KG),
                    ingredient("Azúcar", 1.20, 1.0, MeasureUnit.KG),
                    ingredient("Chocolate", 10.00, 1.0, MeasureUnit.KG),
                    ingredient("Huevos", 3.00, 12.0, MeasureUnit.UD),
                ),
            )
        }
        compose.setContent {
            com.jboden.costingpro.ui.theme.CostingProTheme { CostingProApp(container) }
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun waitForText(text: String) {
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun openEditor() {
        compose.onNodeWithContentDescription("Nuevo escandallo").performClick()
        waitForText("Título")
    }

    /** Fills row [row] (0-based) of the editor. */
    private fun fillRow(row: Int, ingredient: String, quantity: String) {
        compose.onAllNodesWithText("Ingrediente")[row].performScrollTo().performTextInput(ingredient)
        compose.onAllNodesWithText("Cantidad")[row].performScrollTo().performTextInput(quantity)
    }

    private fun buildCookies() {
        compose.onNodeWithText("Título").performTextInput("Galletas de chocolate")
        compose.onNodeWithText("Rendimiento").performTextInput("40")
        val rows = listOf(
            "Harina" to "500",
            "Mantequilla" to "250",
            "Azúcar" to "200",
            "Chocolate" to "200",
            "Huevos" to "2",
        )
        rows.forEachIndexed { index, (name, quantity) ->
            if (index > 0) compose.onNodeWithText("Añadir ingrediente").performScrollTo().performClick()
            fillRow(index, name, quantity)
        }
    }

    /** The criterion of the phase in PLAN.md. */
    @Test
    fun cookiesWithoutExtrasGive5_19PerBatchAnd0_13PerUnit() {
        openEditor()
        buildCookies()

        waitForText("5,19 €")
        compose.onNodeWithText("5,19 €").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("0,13 €").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theThreePriceFieldsAreLinked() {
        openEditor()
        buildCookies()
        waitForText("5,19 €")

        compose.onNodeWithText("Precio neto por unidad").performScrollTo().performTextInput("1")

        // Unit cost 0,12975: food cost 13,0 %, markup 670,7 %, margin 87,0 %.
        waitForText("670,7")
        compose.onNodeWithText("13,0").assertExists()
        compose.onNodeWithText("87,0 %").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Bueno").assertExists()
    }

    @Test
    fun savingStoresTheRecipeWithItsRows() {
        openEditor()
        buildCookies()
        compose.onNodeWithText("Precio neto por unidad").performScrollTo().performTextInput("1,00")

        compose.onNodeWithText("Guardar").performScrollTo().performClick()

        compose.waitUntil(5_000) { runBlocking { container.recipeRepository.getAll() }.isNotEmpty() }
        val saved = runBlocking { container.recipeRepository.getAll() }.single()
        assertEquals("Galletas de chocolate", saved.recipe.name)
        assertEquals(40.0, saved.recipe.yieldQuantity, 0.0)
        assertEquals(MeasureUnit.UD, saved.recipe.yieldUnit)
        assertEquals(PricingMode.PRICE, saved.recipe.pricingMode)
        assertEquals(1.0, saved.recipe.pricingValue, 0.0)
        assertEquals(listOf(500.0, 250.0, 200.0, 200.0, 2.0), saved.lines.map { it.quantity })
        assertEquals(listOf(MeasureUnit.G, MeasureUnit.G, MeasureUnit.G, MeasureUnit.G, MeasureUnit.UD), saved.lines.map { it.unit })
        // Back on the recipes tab.
        waitForText("Recetas")
    }

    @Test
    fun savingWithoutTitleOrRowsShowsTheErrors() {
        openEditor()

        compose.onNodeWithText("Guardar").performScrollTo().performClick()

        waitForText("Este dato es obligatorio")
        compose.onNodeWithText("Añade al menos un ingrediente").assertExists()
        assertTrue(runBlocking { container.recipeRepository.getAll() }.isEmpty())
    }

    @Test
    fun pickingAnIngredientFromTheSuggestionsFillsTheRow() {
        openEditor()

        compose.onAllNodesWithText("Ingrediente")[0].performTextInput("har")
        waitForText("Harina")
        compose.onNodeWithText("Harina").performClick()

        // 22,50 € for 25 kg: the catalogue unit price appears under the row.
        waitForText("0,90 €/kg")
    }

    @Test
    fun aRowCanBeRemovedBySwipingIt() {
        openEditor()
        compose.onNodeWithText("Añadir ingrediente").performScrollTo().performClick()
        assertEquals(2, compose.onAllNodesWithText("Ingrediente").fetchSemanticsNodes().size)

        compose.onAllNodesWithText("Ingrediente")[1].performTouchInput { swipeLeft() }

        compose.waitUntil(5_000) { compose.onAllNodesWithText("Ingrediente").fetchSemanticsNodes().size == 1 }
    }

    @Test
    fun editingAStoredRecipeKeepsItsExtrasAndLoadsEverything() {
        val recipeId = runBlocking {
            val flour = database.ingredientDao().getByName("Harina")!!
            container.recipeRepository.save(
                recipe = Recipe(
                    name = "Pan",
                    yieldQuantity = 10.0,
                    yieldUnit = MeasureUnit.UD,
                    pricingMode = PricingMode.MARKUP,
                    pricingValue = 200.0,
                    vatPct = 4.0,
                ),
                lines = listOf(RecipeLine(ingredientId = flour.id, quantity = 1.0, unit = MeasureUnit.KG, wastePct = 10.0)),
                extras = listOf(ExtraCost(name = "Bolsa", amount = 0.05, per = ExtraCostPer.UNIT)),
            )
        }
        val viewModel = RecipeEditViewModel(container.recipeRepository, container.ingredientRepository, recipeId)

        val loaded = runBlocking { viewModel.uiState.first { !it.loading } }

        assertEquals("Pan", loaded.form.name)
        assertEquals("10", loaded.form.yieldQuantity)
        assertEquals(PricingMode.MARKUP, loaded.form.pricingMode)
        assertEquals("200", loaded.form.pricingText)
        assertEquals("Harina", loaded.form.lines.single().query)
        assertEquals("1", loaded.form.lines.single().quantity)
        assertEquals("10", loaded.form.lines.single().waste)

        viewModel.onNameChange("Pan de molde")
        viewModel.save()
        val saved = runBlocking { viewModel.uiState.first { it.finished } }
        assertTrue(saved.finished)

        val stored = runBlocking { container.recipeRepository.get(recipeId)!! }
        assertEquals("Pan de molde", stored.recipe.name)
        assertEquals(4.0, stored.recipe.vatPct, 0.0)
        assertEquals(1, stored.lines.size)
        assertEquals("Bolsa", stored.extras.single().name)
        assertEquals(1, runBlocking { container.recipeRepository.getAll() }.size)
    }
}
