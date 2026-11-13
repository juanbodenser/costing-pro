package com.jboden.costingpro.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
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
import com.jboden.costingpro.data.SettingsStore
import com.jboden.costingpro.domain.ExtraCostPer
import com.jboden.costingpro.domain.MeasureUnit
import com.jboden.costingpro.domain.PricingMode
import com.jboden.costingpro.ui.recipes.RecipeEditViewModel
import com.jboden.costingpro.ui.theme.CostingProTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.Test
import org.junit.runner.RunWith

/** Phase 7: extra costs, VAT per recipe, the editable price with VAT and the default VAT in Settings. */
@RunWith(AndroidJUnit4::class)
class ExtrasAndVatTest {

    private val databaseRule = InMemoryDatabaseRule()
    private val compose = createComposeRule()

    // The database is closed last, after the screens and the view models have stopped using it.
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(databaseRule).around(compose)

    private val database get() = databaseRule.database

    private lateinit var container: AppContainer

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        container = AppContainer({ database }, { PhotoStore(context) }, { SettingsStore() })
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
    }

    private fun launchApp() {
        compose.setContent { CostingProTheme { CostingProApp(container) } }
    }

    private fun waitForText(text: String) {
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun openEditor() {
        launchApp()
        compose.onNodeWithContentDescription("Nuevo escandallo").performClick()
        waitForText("Título")
    }

    private fun buildCookies() {
        compose.onNodeWithText("Título").performTextInput("Galletas de chocolate")
        compose.onNodeWithText("Rendimiento").performTextInput("40")
        val rows = listOf("Harina" to "500", "Mantequilla" to "250", "Azúcar" to "200", "Chocolate" to "200", "Huevos" to "2")
        rows.forEachIndexed { index, (name, quantity) ->
            if (index > 0) compose.onNodeWithText("Añadir ingrediente").performScrollTo().performClick()
            compose.onAllNodesWithText("Ingrediente")[index].performScrollTo().performTextInput(name)
            compose.onAllNodesWithText("Cantidad")[index].performScrollTo().performTextInput(quantity)
        }
    }

    /** Adds the bag extra of the cookies: 0,05 € per unit. */
    private fun addBagExtra() {
        compose.onNodeWithText("Añadir coste extra").performScrollTo().performClick()
        compose.onNodeWithText("Concepto").performScrollTo().performTextInput("Bolsa")
        compose.onNodeWithText("Importe").performScrollTo().performTextInput("0,05")
        compose.onNodeWithText("Por lote").performScrollTo().performClick()
        compose.onNodeWithText("Por unidad").performClick()
    }

    // ---------- The criterion of the phase ----------

    @Test
    fun theCompleteCookiesGive0_18PerUnitAnd1_10WithVat() {
        openEditor()
        buildCookies()
        addBagExtra()

        compose.onNodeWithText("Precio neto por unidad").performScrollTo().performTextInput("1")

        // 5,19 € of ingredients and 2,00 € of bags: 7,19 € a batch and 0,18 € a unit.
        waitForText("7,19 €")
        compose.onNodeWithText("0,18 €").performScrollTo().assertIsDisplayed()
        // 10 % VAT is the default: the price with VAT field shows 1,10.
        compose.onNodeWithText("PVP con IVA").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("1,10").assertExists()
        compose.onNodeWithText("82,0 %").performScrollTo().assertIsDisplayed()

        compose.onNodeWithText("Guardar").performScrollTo().performClick()
        compose.waitUntil(5_000) { runBlocking { container.recipeRepository.getAll() }.isNotEmpty() }
        val saved = runBlocking { container.recipeRepository.getAll() }.single()
        assertEquals(10.0, saved.recipe.vatPct, 0.0)
        assertEquals(PricingMode.PRICE, saved.recipe.pricingMode)
        assertEquals(1.0, saved.recipe.pricingValue, 0.0)
        val extra = saved.extras.single()
        assertEquals("Bolsa", extra.name)
        assertEquals(0.05, extra.amount, 0.0)
        assertEquals(ExtraCostPer.UNIT, extra.per)
    }

    // ---------- Price with VAT and VAT per recipe ----------

    @Test
    fun typingThePriceWithVatSetsTheNetPrice() {
        openEditor()
        buildCookies()

        compose.onNodeWithText("PVP con IVA").performScrollTo().performTextInput("2,20")

        waitForText("2,00")
        compose.onNodeWithText("2,20").assertExists()
    }

    @Test
    fun theVatOfTheRecipeCanBeChanged() {
        openEditor()
        buildCookies()
        compose.onNodeWithText("Precio neto por unidad").performScrollTo().performTextInput("1")
        waitForText("1,10")

        compose.onNodeWithText("IVA").performScrollTo().performTextReplacement("21")

        waitForText("1,21")
        compose.onNodeWithText("Guardar").performScrollTo().performClick()
        compose.waitUntil(5_000) { runBlocking { container.recipeRepository.getAll() }.isNotEmpty() }
        assertEquals(21.0, runBlocking { container.recipeRepository.getAll() }.single().recipe.vatPct, 0.0)
    }

    @Test
    fun anInvalidVatBlocksSavingAndShowsTheError() {
        openEditor()
        buildCookies()

        compose.onNodeWithText("IVA").performScrollTo().performTextReplacement("150")
        compose.onNodeWithText("Guardar").performScrollTo().performClick()

        waitForText("El IVA debe estar entre 0 y 100 %")
        assertTrue(runBlocking { container.recipeRepository.getAll() }.isEmpty())
    }

    // ---------- Extra costs ----------

    @Test
    fun anExtraRowCanBeRemovedBySwipingIt() {
        openEditor()
        compose.onNodeWithText("Añadir coste extra").performScrollTo().performClick()
        compose.onNodeWithText("Concepto").performScrollTo().assertIsDisplayed()

        compose.onNodeWithText("Concepto").performTouchInput { swipeLeft() }

        compose.waitUntil(5_000) { compose.onAllNodesWithText("Concepto").fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun aHalfFilledExtraBlocksSaving() {
        openEditor()
        buildCookies()
        compose.onNodeWithText("Añadir coste extra").performScrollTo().performClick()
        compose.onNodeWithText("Importe").performScrollTo().performTextInput("1")

        compose.onNodeWithText("Guardar").performScrollTo().performClick()

        waitForText("Este dato es obligatorio")
        assertTrue(runBlocking { container.recipeRepository.getAll() }.isEmpty())
    }

    @Test
    fun editingExtrasOfAStoredRecipeChangesAndRemovesThem() {
        val recipeId = runBlocking {
            val flour = database.ingredientDao().getByName("Harina")!!
            container.recipeRepository.save(
                recipe = Recipe(name = "Pan", yieldQuantity = 10.0, yieldUnit = MeasureUnit.UD, vatPct = 4.0),
                lines = listOf(RecipeLine(ingredientId = flour.id, quantity = 1.0, unit = MeasureUnit.KG)),
                extras = listOf(
                    ExtraCost(name = "Bolsa", amount = 0.05, per = ExtraCostPer.UNIT),
                    ExtraCost(name = "Horno", amount = 1.5, per = ExtraCostPer.BATCH),
                ),
            )
        }
        val viewModel = databaseRule.track(RecipeEditViewModel(container.recipeRepository, container.ingredientRepository, container.settings, recipeId))

        val loaded = runBlocking { withTimeout(5_000) { viewModel.uiState.first { !it.loading } } }

        assertEquals(listOf("Bolsa", "Horno"), loaded.form.extras.map { it.name })
        assertEquals(listOf("0,05", "1,50"), loaded.form.extras.map { it.amount })
        assertEquals(listOf(ExtraCostPer.UNIT, ExtraCostPer.BATCH), loaded.form.extras.map { it.per })
        assertEquals("4", loaded.form.vatPct)

        viewModel.onExtraAmountChange(loaded.form.extras[0].key, "0,10")
        viewModel.removeExtra(loaded.form.extras[1].key)
        viewModel.save()
        runBlocking { withTimeout(5_000) { viewModel.uiState.first { it.finished } } }

        val stored = runBlocking { container.recipeRepository.get(recipeId)!! }
        assertEquals(listOf("Bolsa"), stored.extras.map { it.name })
        assertEquals(0.10, stored.extras.single().amount, 0.0)
        assertEquals(4.0, stored.recipe.vatPct, 0.0)
    }

    @Test
    fun theDetailListsEachExtraByName() {
        runBlocking {
            val flour = database.ingredientDao().getByName("Harina")!!
            container.recipeRepository.save(
                recipe = Recipe(name = "Pan", yieldQuantity = 10.0, yieldUnit = MeasureUnit.UD),
                lines = listOf(RecipeLine(ingredientId = flour.id, quantity = 1.0, unit = MeasureUnit.KG)),
                extras = listOf(ExtraCost(name = "Bolsa", amount = 0.05, per = ExtraCostPer.UNIT)),
            )
        }
        launchApp()
        waitForText("Pan")
        compose.onNodeWithText("Pan").performClick()
        waitForText("Desglose")

        compose.onNodeWithText("Bolsa").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Coste extra").performScrollTo().assertIsDisplayed()
        // 0,05 € on each of the 10 units.
        compose.onNodeWithText("0,50 €").performScrollTo().assertIsDisplayed()
    }

    // ---------- Settings ----------

    @Test
    fun theDefaultVatInSettingsIsTheOneNewRecipesStartWith() {
        launchApp()
        compose.onNodeWithText("Ajustes").performClick()
        waitForText("IVA de venta")
        compose.onNodeWithText("10").assertExists()

        compose.onNodeWithText("IVA de venta").performTextReplacement("21")

        compose.waitUntil(5_000) { container.settings.defaultVatPct.value == 21.0 }
        compose.onNodeWithText("Recetas").performClick()
        compose.onNodeWithContentDescription("Nuevo escandallo").performClick()
        waitForText("Título")
        compose.onNodeWithText("21").performScrollTo().assertExists()
    }

    @Test
    fun anInvalidDefaultVatIsNotSaved() {
        launchApp()
        compose.onNodeWithText("Ajustes").performClick()
        waitForText("IVA de venta")

        compose.onNodeWithText("IVA de venta").performTextReplacement("150")
        waitForText("El IVA debe estar entre 0 y 100 %")
        compose.onNodeWithText("IVA de venta").performTextReplacement("abc")
        waitForText("Escribe un número, por ejemplo 22,50")
        compose.onNodeWithText("IVA de venta").performTextReplacement("")
        waitForText("Este dato es obligatorio")

        assertEquals(10.0, container.settings.defaultVatPct.value, 0.0)
    }

    @Test
    fun changingTheDefaultVatDoesNotChangeStoredRecipes() {
        val recipeId = runBlocking {
            val flour = database.ingredientDao().getByName("Harina")!!
            container.recipeRepository.save(
                recipe = Recipe(name = "Pan", yieldQuantity = 10.0, yieldUnit = MeasureUnit.UD, vatPct = 4.0),
                lines = listOf(RecipeLine(ingredientId = flour.id, quantity = 1.0, unit = MeasureUnit.KG)),
                extras = emptyList(),
            )
        }

        container.settings.setDefaultVatPct(21.0)

        assertEquals(4.0, runBlocking { container.recipeRepository.get(recipeId)!! }.recipe.vatPct, 0.0)
    }
}
