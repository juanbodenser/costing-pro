package com.jboden.costingpro.ui

import androidx.compose.ui.test.assertIsDisplayed
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
import com.jboden.costingpro.data.Ingredient
import com.jboden.costingpro.data.PhotoStore
import com.jboden.costingpro.data.Recipe
import com.jboden.costingpro.data.RecipeLine
import com.jboden.costingpro.domain.MeasureUnit
import com.jboden.costingpro.domain.PricingMode
import com.jboden.costingpro.ui.recipes.RecipeEditViewModel
import com.jboden.costingpro.ui.theme.CostingProTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.Test
import org.junit.runner.RunWith

/** Phase 6: recipes used as ingredients of other recipes, over an in-memory database. */
@RunWith(AndroidJUnit4::class)
class SubRecipeTest {

    private val databaseRule = InMemoryDatabaseRule()
    private val compose = createComposeRule()

    // The database is closed last, after the screens and the view models have stopped using it.
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(databaseRule).around(compose)

    private val database get() = databaseRule.database

    private lateinit var container: AppContainer
    private var sauceId = 0L
    private var dishId = 0L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        container = AppContainer({ database }, { PhotoStore(context) })
        runBlocking {
            val ids = database.ingredientDao().insertAll(
                listOf(
                    Ingredient(name = "Tomate", purchasePrice = 2.0, purchaseQuantity = 1.0, purchaseUnit = MeasureUnit.KG),
                    Ingredient(name = "Pasta", purchasePrice = 1.50, purchaseQuantity = 1.0, purchaseUnit = MeasureUnit.KG),
                ),
            )
            // Sauce: 3 kg of tomato make 2 l: 6,00 € a batch and 3,00 € a litre.
            sauceId = container.recipeRepository.save(
                recipe = Recipe(name = "Salsa de tomate", yieldQuantity = 2.0, yieldUnit = MeasureUnit.L),
                lines = listOf(RecipeLine(ingredientId = ids[0], quantity = 3.0, unit = MeasureUnit.KG)),
                extras = emptyList(),
            )
            // Dish: 400 g of pasta (0,60 €) and 500 ml of sauce (1,50 €) make 4 portions, sold at 2,00 €.
            dishId = container.recipeRepository.save(
                recipe = Recipe(
                    name = "Pasta con tomate",
                    yieldQuantity = 4.0,
                    yieldUnit = MeasureUnit.RACION,
                    pricingMode = PricingMode.PRICE,
                    pricingValue = 2.0,
                ),
                lines = listOf(
                    RecipeLine(ingredientId = ids[1], quantity = 400.0, unit = MeasureUnit.G),
                    RecipeLine(subRecipeId = sauceId, quantity = 500.0, unit = MeasureUnit.ML),
                ),
                extras = emptyList(),
            )
        }
    }

    /** Opens the app. Tests that only use the repository or a view model do not, so nothing observes the database. */
    private fun launchApp() {
        compose.setContent {
            CostingProTheme { CostingProApp(container) }
        }
    }

    private fun waitForText(text: String) {
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    // ---------- Repository ----------

    private fun usingRecipe(name: String, subRecipeId: Long) = Triple(
        Recipe(name = name, yieldQuantity = 1.0, yieldUnit = MeasureUnit.UD),
        listOf(RecipeLine(subRecipeId = subRecipeId, quantity = 1.0, unit = MeasureUnit.UD)),
        emptyList<com.jboden.costingpro.data.ExtraCost>(),
    )

    @Test
    fun saveRejectsARecipeThatWouldContainItselfThroughOthers() = runBlocking {
        val repository = container.recipeRepository
        val base = repository.save(Recipe(name = "A", yieldQuantity = 1.0, yieldUnit = MeasureUnit.UD), emptyList(), emptyList())
        val (b, bLines, _) = usingRecipe("B", base)
        val middle = repository.save(b, bLines, emptyList())
        val (c, cLines, _) = usingRecipe("C", middle)
        val top = repository.save(c, cLines, emptyList())

        // A -> C would close A -> C -> B -> A.
        val closing = assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repository.save(
                    Recipe(id = base, name = "A", yieldQuantity = 1.0, yieldUnit = MeasureUnit.UD),
                    listOf(RecipeLine(subRecipeId = top, quantity = 1.0, unit = MeasureUnit.UD)),
                    emptyList(),
                )
            }
        }
        assertTrue(closing.message.orEmpty(), closing.message.orEmpty().contains("contain itself"))
        // Nothing was written.
        assertTrue(repository.get(base)!!.lines.isEmpty())

        // B -> B directly is still refused too.
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repository.save(
                    Recipe(id = middle, name = "B", yieldQuantity = 1.0, yieldUnit = MeasureUnit.UD),
                    listOf(RecipeLine(subRecipeId = middle, quantity = 1.0, unit = MeasureUnit.UD)),
                    emptyList(),
                )
            }
        }
        Unit
    }

    @Test
    fun saveAllowsReusingARecipeWithoutAloop() = runBlocking {
        // Re-saving the dish with the sauce it already uses is fine, and so is adding a second user of the sauce.
        val dish = container.recipeRepository.get(dishId)!!
        container.recipeRepository.save(dish.recipe, dish.lines, dish.extras)
        val (second, lines, extras) = usingRecipe("Otro plato", sauceId)
        val secondId = container.recipeRepository.save(second, lines, extras)

        assertEquals(sauceId, container.recipeRepository.get(secondId)!!.lines.single().subRecipeId)
        assertEquals(listOf("Otro plato", "Pasta con tomate"), container.recipeRepository.recipesUsing(sauceId).map { it.name })
    }

    // ---------- The criterion of the phase ----------

    @Test
    fun raisingTheTomatoRaisesTheCostOfTheDishThatUsesTheSauce() {
        launchApp()
        waitForText("Coste 0,53 € · Precio 2,00 €")
        // The sauce itself costs 3,00 € a litre.
        compose.onNodeWithText("Coste 3,00 € · Sin precio").assertIsDisplayed()

        runBlocking {
            val tomato = database.ingredientDao().getByName("Tomate")!!
            container.ingredientRepository.save(tomato.copy(purchasePrice = 3.0))
        }

        // Sauce: 4,50 € a litre, 2,25 € for 500 ml; dish 0,60 + 2,25 = 2,85 € over 4 portions.
        waitForText("Coste 0,71 € · Precio 2,00 €")
        compose.onNodeWithText("Coste 4,50 € · Sin precio").assertIsDisplayed()
    }

    @Test
    fun theDishDetailShowsTheSauceAsASubRecipeAndFollowsItsPrice() {
        launchApp()
        waitForText("Pasta con tomate")
        compose.onNodeWithText("Pasta con tomate").performClick()
        waitForText("Desglose")

        compose.onNodeWithText("Salsa de tomate").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Subreceta").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("500 ml").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("1,50 €").performScrollTo().assertIsDisplayed()
    }

    // ---------- Editor ----------

    @Test
    fun theEditorOffersRecipesInTheAutocompleteAndCostsThem() {
        launchApp()
        compose.onNodeWithContentDescription("Nuevo escandallo").performClick()
        waitForText("Título")
        compose.onNodeWithText("Título").performTextInput("Menú")
        compose.onNodeWithText("Rendimiento").performTextInput("1")

        compose.onAllNodesWithText("Ingrediente")[0].performTextInput("sal")
        waitForText("Receta")
        compose.onNodeWithText("Receta").assertIsDisplayed()
        compose.onNodeWithText("Salsa de tomate").performClick()
        compose.onAllNodesWithText("Cantidad")[0].performTextInput("500")

        // The row says it is a sub-recipe, defaults to ml and costs 500 ml × 3,00 €/l.
        waitForText("1,50 €")
        compose.onNodeWithText("Subreceta").assertIsDisplayed()
        compose.onNodeWithText("3,00 €/l").assertIsDisplayed()

        compose.onNodeWithText("Guardar").performScrollTo().performClick()
        compose.waitUntil(5_000) { runBlocking { container.recipeRepository.getAll() }.size == 3 }
        val menu = runBlocking { container.recipeRepository.getAll() }.single { it.recipe.name == "Menú" }
        val line = menu.lines.single()
        assertEquals(sauceId, line.subRecipeId)
        assertEquals(null, line.ingredientId)
        assertEquals(MeasureUnit.ML, line.unit)
        assertEquals(500.0, line.quantity, 0.0)
    }

    @Test
    fun theEditorDoesNotOfferARecipeThatWouldContainItself() {
        fun offered(recipeId: Long): List<String> {
            val viewModel = databaseRule.track(RecipeEditViewModel(container.recipeRepository, container.ingredientRepository, container.settings, recipeId))
            val state = runBlocking { withTimeout(5_000) { viewModel.uiState.first { !it.loading } } }
            return state.catalogue.filter { it.isRecipe }.map { it.name }
        }

        // A new recipe can use both; the sauce cannot use itself or the dish that uses it; the dish can use the sauce.
        assertEquals(listOf("Pasta con tomate", "Salsa de tomate"), offered(RecipeEditViewModel.NEW_ID))
        assertEquals(emptyList<String>(), offered(sauceId))
        assertEquals(listOf("Salsa de tomate"), offered(dishId))
    }

    @Test
    fun editingARecipeThatUsesASauceLoadsTheSauceRow() {
        val viewModel = databaseRule.track(RecipeEditViewModel(container.recipeRepository, container.ingredientRepository, container.settings, dishId))

        val state = runBlocking { withTimeout(5_000) { viewModel.uiState.first { !it.loading } } }

        val sauceRow = state.form.lines[1]
        assertEquals(sauceId, sauceRow.subRecipeId)
        assertEquals(null, sauceRow.ingredientId)
        assertEquals("Salsa de tomate", sauceRow.query)
        assertEquals(MeasureUnit.ML, sauceRow.unit)
        assertTrue(state.rows[1].isRecipe)
        assertEquals(listOf(MeasureUnit.L, MeasureUnit.ML), state.rows[1].units)
        // 0,60 € of pasta and 1,50 € of sauce.
        assertEquals("2.10", state.result.cost.batchCost.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString())

        viewModel.save()
        runBlocking { withTimeout(5_000) { viewModel.uiState.first { it.finished } } }
        val saved = runBlocking { container.recipeRepository.get(dishId)!! }
        assertEquals(listOf(null, sauceId), saved.lines.map { it.subRecipeId })
    }

    // ---------- Delete protection ----------

    @Test
    fun aRecipeUsedAsSubRecipeCannotBeDeleted() {
        launchApp()
        waitForText("Salsa de tomate")
        compose.onNodeWithText("Salsa de tomate").performClick()
        waitForText("Desglose")

        compose.onNodeWithContentDescription("Más acciones").performClick()
        compose.onNodeWithText("Borrar").performClick()
        waitForText("¿Borrar escandallo?")
        compose.onAllNodesWithText("Borrar").onLast().performClick()

        waitForText("No se puede borrar")
        compose.onNodeWithText("• Pasta con tomate").assertIsDisplayed()
        assertNotNull(runBlocking { container.recipeRepository.get(sauceId) })
        assertFalse(runBlocking { container.recipeRepository.getAll() }.isEmpty())
    }
}
