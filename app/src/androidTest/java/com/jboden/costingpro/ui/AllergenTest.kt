package com.jboden.costingpro.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jboden.costingpro.AppContainer
import com.jboden.costingpro.data.Ingredient
import com.jboden.costingpro.data.PhotoStore
import com.jboden.costingpro.data.Recipe
import com.jboden.costingpro.data.RecipeLine
import com.jboden.costingpro.domain.Allergen
import com.jboden.costingpro.domain.MeasureUnit
import com.jboden.costingpro.ui.theme.CostingProTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** Phase 8: allergens in the ingredient form and inherited in the recipe detail. */
@RunWith(AndroidJUnit4::class)
class AllergenTest {

    private val databaseRule = InMemoryDatabaseRule()
    private val compose = createComposeRule()

    // The database is closed last, after the screens have stopped using it.
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(databaseRule).around(compose)

    private val database get() = databaseRule.database
    private lateinit var container: AppContainer

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        container = AppContainer({ database }, { PhotoStore(context) })
        runBlocking {
            fun ingredient(name: String, unit: MeasureUnit, vararg allergens: Allergen) = Ingredient(
                name = name, purchasePrice = 1.0, purchaseQuantity = 1.0, purchaseUnit = unit,
                allergens = Allergen.maskOf(*allergens),
            )
            val ids = database.ingredientDao().insertAll(
                listOf(
                    ingredient("Harina", MeasureUnit.KG, Allergen.GLUTEN),
                    ingredient("Huevo", MeasureUnit.UD, Allergen.EGGS),
                    ingredient("Apio", MeasureUnit.KG, Allergen.CELERY),
                    ingredient("Agua", MeasureUnit.L),
                ),
            )
            val stock = container.recipeRepository.save(
                recipe = Recipe(name = "Fondo", yieldQuantity = 1.0, yieldUnit = MeasureUnit.L),
                lines = listOf(RecipeLine(ingredientId = ids[2], quantity = 0.2, unit = MeasureUnit.KG)),
                extras = emptyList(),
            )
            container.recipeRepository.save(
                recipe = Recipe(name = "Masa", yieldQuantity = 10.0, yieldUnit = MeasureUnit.UD),
                lines = listOf(
                    RecipeLine(ingredientId = ids[0], quantity = 500.0, unit = MeasureUnit.G),
                    RecipeLine(ingredientId = ids[1], quantity = 2.0, unit = MeasureUnit.UD),
                ),
                extras = emptyList(),
            )
            container.recipeRepository.save(
                recipe = Recipe(name = "Sopa", yieldQuantity = 4.0, yieldUnit = MeasureUnit.RACION),
                lines = listOf(
                    RecipeLine(ingredientId = ids[3], quantity = 1.0, unit = MeasureUnit.L),
                    RecipeLine(subRecipeId = stock, quantity = 500.0, unit = MeasureUnit.ML),
                ),
                extras = emptyList(),
            )
        }
        compose.setContent { CostingProTheme { CostingProApp(container) } }
    }

    private fun waitForText(text: String) {
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun openRecipe(name: String) {
        waitForText(name)
        compose.onNodeWithText(name).performClick()
        waitForText("Desglose")
    }

    /** The criterion of the phase in PLAN.md. */
    @Test
    fun aRecipeWithFlourAndEggShowsGlutenAndEggs() {
        openRecipe("Masa")

        compose.onNodeWithText("Gluten").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Huevos").performScrollTo().assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithText("Apio").fetchSemanticsNodes().size)
    }

    @Test
    fun aRecipeInheritsTheAllergensOfItsSubRecipe() {
        openRecipe("Sopa")

        // Celery comes from the stock, through the sub-recipe.
        compose.onNodeWithText("Apio").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aRecipeWithoutAllergensSaysSo() {
        runBlocking {
            val water = database.ingredientDao().getByName("Agua")!!
            container.recipeRepository.save(
                recipe = Recipe(name = "Hielo", yieldQuantity = 1.0, yieldUnit = MeasureUnit.KG),
                lines = listOf(RecipeLine(ingredientId = water.id, quantity = 1.0, unit = MeasureUnit.L)),
                extras = emptyList(),
            )
        }

        openRecipe("Hielo")

        compose.onNodeWithText("Sus ingredientes no declaran alérgenos.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theIngredientFormSavesTheCheckedAllergens() {
        compose.onNodeWithText("Ingredientes").performClick()
        waitForText("Agua")
        compose.onNodeWithText("Agua").performClick()
        waitForText("Alérgenos")

        compose.onNodeWithText("Sulfitos").performScrollTo().performClick()
        compose.onNodeWithText("Mostaza").performScrollTo().performClick()
        compose.onNodeWithText("Mostaza").performClick()
        compose.onNodeWithText("Guardar").performScrollTo().performClick()

        compose.waitUntil(5_000) {
            runBlocking { database.ingredientDao().getByName("Agua")!!.allergens } != 0
        }
        val water = runBlocking { database.ingredientDao().getByName("Agua")!! }
        assertEquals(setOf(Allergen.SULPHITES), Allergen.fromMask(water.allergens))
    }

    @Test
    fun theIngredientFormShowsTheStoredAllergensAndKeepsThemOnSave() {
        compose.onNodeWithText("Ingredientes").performClick()
        waitForText("Harina")
        compose.onNodeWithText("Harina").performClick()
        waitForText("Alérgenos")

        compose.onNodeWithText("Gluten").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Guardar").performScrollTo().performClick()
        waitForText("Ingredientes")

        val flour = runBlocking { database.ingredientDao().getByName("Harina")!! }
        assertEquals(setOf(Allergen.GLUTEN), Allergen.fromMask(flour.allergens))
    }

    @Test
    fun checkingAnAllergenReachesTheRecipesThatUseTheIngredient() {
        runBlocking {
            val water = database.ingredientDao().getByName("Agua")!!
            container.ingredientRepository.save(water.copy(allergens = Allergen.maskOf(Allergen.SULPHITES)))
        }

        openRecipe("Sopa")

        compose.onNodeWithText("Sulfitos").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Apio").performScrollTo().assertIsDisplayed()
    }
}
