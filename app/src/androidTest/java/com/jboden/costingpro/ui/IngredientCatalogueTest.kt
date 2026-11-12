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
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jboden.costingpro.AppContainer
import com.jboden.costingpro.data.CostingDatabase
import com.jboden.costingpro.data.PhotoStore
import com.jboden.costingpro.data.ExtraCost
import com.jboden.costingpro.data.Recipe
import com.jboden.costingpro.data.RecipeLine
import com.jboden.costingpro.domain.MeasureUnit
import com.jboden.costingpro.ui.theme.CostingProTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Phase 3: the ingredient catalogue, driven through the real screens over an in-memory database. */
@RunWith(AndroidJUnit4::class)
class IngredientCatalogueTest {

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
        compose.setContent {
            CostingProTheme { CostingProApp(container) }
        }
        compose.onNodeWithText("Ingredientes").performClick()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun waitForText(text: String) {
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().size > 0
        }
    }

    private fun createIngredient(name: String, price: String, quantity: String) {
        compose.onNodeWithContentDescription("Nuevo ingrediente").performClick()
        compose.onNodeWithText("Nombre").performTextInput(name)
        compose.onNodeWithText("Precio de compra").performTextInput(price)
        compose.onNodeWithText("Cantidad del formato").performTextInput(quantity)
        compose.onNodeWithText("Guardar").performScrollTo().performClick()
    }

    /** The criterion of the phase in PLAN.md. */
    @Test
    fun flourAt22_50For25KgShows0_90PerKg() {
        createIngredient("Harina", "22,50", "25")

        waitForText("0,90 €/kg")
        compose.onNodeWithText("Harina").assertIsDisplayed()
        compose.onNodeWithText("0,90 €/kg").assertIsDisplayed()
        compose.onNodeWithText("25 kg · 22,50 €").assertIsDisplayed()
    }

    @Test
    fun editingThePriceUpdatesTheList() {
        createIngredient("Harina", "22,50", "25")
        waitForText("0,90 €/kg")

        compose.onNodeWithText("Harina").performClick()
        compose.onNodeWithText("22,50").performTextReplacement("25.00")
        compose.onNodeWithText("Guardar").performScrollTo().performClick()

        waitForText("1,00 €/kg")
        compose.onNodeWithText("1,00 €/kg").assertIsDisplayed()
    }

    @Test
    fun aDuplicateNameIsRejected() {
        createIngredient("Harina", "22,50", "25")
        waitForText("0,90 €/kg")

        createIngredient("harina", "1", "1")

        waitForText("Ya hay un ingrediente con este nombre")
        assertEquals(1, runBlocking { database.ingredientDao().getAll() }.size)
    }

    @Test
    fun searchIgnoresCaseAndAccents() {
        createIngredient("Azúcar", "1,20", "1")
        waitForText("Azúcar")
        createIngredient("Harina", "22,50", "25")
        waitForText("Harina")

        compose.onNodeWithText("Buscar ingrediente").performTextInput("azuc")

        compose.waitUntil(5_000) { compose.onAllNodesWithText("Harina").fetchSemanticsNodes().size == 0 }
        compose.onNodeWithText("Azúcar").assertIsDisplayed()
    }

    @Test
    fun anUnusedIngredientIsDeletedAfterConfirming() {
        createIngredient("Harina", "22,50", "25")
        waitForText("Harina")

        compose.onNodeWithText("Harina").performClick()
        compose.onNodeWithContentDescription("Borrar").performClick()
        waitForText("¿Borrar ingrediente?")
        compose.onNodeWithText("Borrar").performClick()

        waitForText("Aún no hay ingredientes. Pulsa + para crear el primero.")
        assertNull(runBlocking { database.ingredientDao().getByName("Harina") })
    }

    @Test
    fun anIngredientInUseIsNotDeletedAndItsRecipesAreListed() {
        createIngredient("Harina", "22,50", "25")
        waitForText("Harina")
        runBlocking {
            val flour = database.ingredientDao().getByName("Harina")!!
            database.recipeDao().save(
                recipe = Recipe(name = "Pan", yieldQuantity = 1.0, yieldUnit = MeasureUnit.UD),
                lines = listOf(RecipeLine(ingredientId = flour.id, quantity = 500.0, unit = MeasureUnit.G)),
                extras = emptyList<ExtraCost>(),
            )
        }

        compose.onNodeWithText("Harina").performClick()
        waitForText("Usado en 1 receta")
        compose.onNodeWithContentDescription("Borrar").performClick()
        waitForText("¿Borrar ingrediente?")
        compose.onNodeWithText("Borrar").performClick()

        waitForText("No se puede borrar")
        assertNotNull(runBlocking { database.ingredientDao().getByName("Harina") })
    }
}
