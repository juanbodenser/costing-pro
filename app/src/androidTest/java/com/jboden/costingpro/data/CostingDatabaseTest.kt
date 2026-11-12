package com.jboden.costingpro.data

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jboden.costingpro.domain.Allergen
import com.jboden.costingpro.domain.ExtraCostPer
import com.jboden.costingpro.domain.MeasureUnit
import com.jboden.costingpro.domain.PricingMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CostingDatabaseTest {

    private lateinit var database: CostingDatabase
    private lateinit var ingredients: IngredientRepository
    private lateinit var recipes: RecipeRepository
    private var clock = 1_000L

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            CostingDatabase::class.java,
        ).build()
        ingredients = IngredientRepository(database.ingredientDao(), now = { clock })
        recipes = RecipeRepository(database.recipeDao(), now = { clock })
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun flour() = Ingredient(
        name = "Harina",
        purchasePrice = 22.50,
        purchaseQuantity = 25.0,
        purchaseUnit = MeasureUnit.KG,
        allergens = Allergen.maskOf(Allergen.GLUTEN),
    )

    private fun eggs() = Ingredient(
        name = "Huevos",
        purchasePrice = 3.00,
        purchaseQuantity = 12.0,
        purchaseUnit = MeasureUnit.UD,
    )

    private fun cookies() = Recipe(
        name = "Galletas de chocolate",
        yieldQuantity = 40.0,
        yieldUnit = MeasureUnit.UD,
        pricingMode = PricingMode.PRICE,
        pricingValue = 1.00,
        notes = "Hornear 12 min",
    )

    // ---------- Phase 2 criterion ----------

    @Test
    fun savesAndReadsARecipeWithLinesAndExtras(): Unit = runBlocking {
        val flourId = ingredients.save(flour())
        val eggsId = ingredients.save(eggs())

        val recipeId = recipes.save(
            recipe = cookies(),
            lines = listOf(
                RecipeLine(ingredientId = flourId, quantity = 500.0, unit = MeasureUnit.G),
                RecipeLine(ingredientId = eggsId, quantity = 2.0, unit = MeasureUnit.UD, wastePct = 5.0),
            ),
            extras = listOf(
                ExtraCost(name = "Bolsa", amount = 0.05, per = ExtraCostPer.UNIT),
                ExtraCost(name = "Mano de obra", amount = 3.0, per = ExtraCostPer.BATCH),
            ),
        )

        val loaded = recipes.get(recipeId)
        assertNotNull(loaded)
        loaded!!

        assertEquals("Galletas de chocolate", loaded.recipe.name)
        assertEquals(40.0, loaded.recipe.yieldQuantity, 0.0)
        assertEquals(MeasureUnit.UD, loaded.recipe.yieldUnit)
        assertEquals(PricingMode.PRICE, loaded.recipe.pricingMode)
        assertEquals(1.00, loaded.recipe.pricingValue, 0.0)
        assertEquals(10.0, loaded.recipe.vatPct, 0.0)
        assertEquals("Hornear 12 min", loaded.recipe.notes)
        assertNull(loaded.recipe.photoPath)
        assertEquals(1_000L, loaded.recipe.createdAt)

        assertEquals(2, loaded.lines.size)
        assertEquals(listOf(0, 1), loaded.lines.map { it.position })
        assertEquals(flourId, loaded.lines[0].ingredientId)
        assertNull(loaded.lines[0].subRecipeId)
        assertEquals(500.0, loaded.lines[0].quantity, 0.0)
        assertEquals(MeasureUnit.G, loaded.lines[0].unit)
        assertEquals(eggsId, loaded.lines[1].ingredientId)
        assertEquals(5.0, loaded.lines[1].wastePct, 0.0)

        assertEquals(listOf("Bolsa", "Mano de obra"), loaded.extras.map { it.name })
        assertEquals(0.05, loaded.extras[0].amount, 0.0)
        assertEquals(ExtraCostPer.UNIT, loaded.extras[0].per)
        assertEquals(ExtraCostPer.BATCH, loaded.extras[1].per)

        val storedFlour = ingredients.get(flourId)!!
        assertEquals(setOf(Allergen.GLUTEN), Allergen.fromMask(storedFlour.allergens))
    }

    // ---------- Recipes ----------

    @Test
    fun savingAgainReplacesLinesAndExtrasAndKeepsCreatedAt(): Unit = runBlocking {
        val flourId = ingredients.save(flour())
        val eggsId = ingredients.save(eggs())
        val recipeId = recipes.save(
            cookies(),
            lines = listOf(RecipeLine(ingredientId = flourId, quantity = 500.0, unit = MeasureUnit.G)),
            extras = listOf(ExtraCost(name = "Bolsa", amount = 0.05, per = ExtraCostPer.UNIT)),
        )

        clock = 2_000L
        val saved = recipes.get(recipeId)!!
        recipes.save(
            saved.recipe.copy(name = "Galletas dobles"),
            lines = listOf(
                RecipeLine(ingredientId = eggsId, quantity = 4.0, unit = MeasureUnit.UD),
                RecipeLine(ingredientId = flourId, quantity = 1.0, unit = MeasureUnit.KG),
            ),
            extras = emptyList(),
        )

        val loaded = recipes.get(recipeId)!!
        assertEquals("Galletas dobles", loaded.recipe.name)
        assertEquals(1_000L, loaded.recipe.createdAt)
        assertEquals(2_000L, loaded.recipe.updatedAt)
        assertEquals(listOf(eggsId, flourId), loaded.lines.map { it.ingredientId })
        assertTrue(loaded.extras.isEmpty())
        assertEquals(1, recipes.observeAll().first().size)
    }

    @Test
    fun subRecipeLineIsStoredAndProtectsTheSubRecipe(): Unit = runBlocking {
        val flourId = ingredients.save(flour())
        val doughId = recipes.save(
            Recipe(name = "Masa", yieldQuantity = 2.0, yieldUnit = MeasureUnit.KG),
            lines = listOf(RecipeLine(ingredientId = flourId, quantity = 2.0, unit = MeasureUnit.KG)),
            extras = emptyList(),
        )
        val pieId = recipes.save(
            Recipe(name = "Empanada", yieldQuantity = 8.0, yieldUnit = MeasureUnit.RACION),
            lines = listOf(RecipeLine(subRecipeId = doughId, quantity = 500.0, unit = MeasureUnit.G)),
            extras = emptyList(),
        )

        assertEquals(doughId, recipes.get(pieId)!!.lines.single().subRecipeId)
        assertEquals(listOf("Empanada"), recipes.recipesUsing(doughId).map { it.name })

        val result = recipes.delete(doughId)
        assertTrue(result is DeleteResult.InUse)
        assertNotNull(recipes.get(doughId))

        assertEquals(DeleteResult.Deleted, recipes.delete(pieId))
        assertEquals(DeleteResult.Deleted, recipes.delete(doughId))
    }

    @Test
    fun deletingARecipeDeletesItsLinesAndExtras(): Unit = runBlocking {
        val flourId = ingredients.save(flour())
        val recipeId = recipes.save(
            cookies(),
            lines = listOf(RecipeLine(ingredientId = flourId, quantity = 500.0, unit = MeasureUnit.G)),
            extras = listOf(ExtraCost(name = "Bolsa", amount = 0.05, per = ExtraCostPer.UNIT)),
        )

        assertEquals(DeleteResult.Deleted, recipes.delete(recipeId))

        assertNull(recipes.get(recipeId))
        assertEquals(0, countRows("recipe_line"))
        assertEquals(0, countRows("extra_cost"))
        // The ingredient is free again.
        assertEquals(DeleteResult.Deleted, ingredients.delete(flourId))
    }

    @Test
    fun aLineNeedsExactlyOneOfIngredientAndSubRecipe() {
        runBlocking {
            val flourId = ingredients.save(flour())
            val doughId = recipes.save(
                Recipe(name = "Masa", yieldQuantity = 2.0, yieldUnit = MeasureUnit.KG),
                lines = emptyList(),
                extras = emptyList(),
            )

            assertThrows(IllegalArgumentException::class.java) {
                runBlocking {
                    recipes.save(
                        cookies(),
                        lines = listOf(RecipeLine(quantity = 1.0, unit = MeasureUnit.KG)),
                        extras = emptyList(),
                    )
                }
            }
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking {
                    recipes.save(
                        cookies(),
                        lines = listOf(
                            RecipeLine(
                                ingredientId = flourId, subRecipeId = doughId,
                                quantity = 1.0, unit = MeasureUnit.KG,
                            ),
                        ),
                        extras = emptyList(),
                    )
                }
            }
            // A recipe cannot use itself as a sub-recipe.
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking {
                    recipes.save(
                        recipes.get(doughId)!!.recipe,
                        lines = listOf(RecipeLine(subRecipeId = doughId, quantity = 1.0, unit = MeasureUnit.KG)),
                        extras = emptyList(),
                    )
                }
            }
        }
    }

    // ---------- Ingredients ----------

    @Test
    fun ingredientInUseIsNotDeleted(): Unit = runBlocking {
        val flourId = ingredients.save(flour())
        recipes.save(
            cookies(),
            lines = listOf(RecipeLine(ingredientId = flourId, quantity = 500.0, unit = MeasureUnit.G)),
            extras = emptyList(),
        )

        val result = ingredients.delete(flourId)

        assertTrue(result is DeleteResult.InUse)
        assertEquals(listOf("Galletas de chocolate"), (result as DeleteResult.InUse).recipes.map { it.name })
        assertNotNull(ingredients.get(flourId))
        // The database itself also refuses, even bypassing the repository.
        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { database.ingredientDao().deleteById(flourId) }
        }
    }

    @Test
    fun ingredientNameIsUniqueIgnoringCase() {
        runBlocking {
            ingredients.save(flour())

            assertThrows(SQLiteConstraintException::class.java) {
                runBlocking { ingredients.save(flour().copy(name = "HARINA")) }
            }
            assertEquals("Harina", ingredients.findByName(" harina ")?.name)
        }
    }

    @Test
    fun updatedAtOnlyChangesWithThePurchaseFormat(): Unit = runBlocking {
        val flourId = ingredients.save(flour())
        assertEquals(1_000L, ingredients.get(flourId)!!.updatedAt)

        clock = 2_000L
        ingredients.save(ingredients.get(flourId)!!.copy(defaultWastePct = 2.0))
        assertEquals(1_000L, ingredients.get(flourId)!!.updatedAt)

        clock = 3_000L
        ingredients.save(ingredients.get(flourId)!!.copy(purchasePrice = 24.0))
        assertEquals(3_000L, ingredients.get(flourId)!!.updatedAt)
        assertEquals(2.0, ingredients.get(flourId)!!.defaultWastePct, 0.0)
    }

    // ---------- Sample data ----------

    @Test
    fun sampleDataContainsTheCookiesRecipe(): Unit = runBlocking {
        SampleData.insertInto(database, now = 5_000L)

        val allIngredients = ingredients.observeAll().first()
        assertEquals(
            listOf("Azúcar", "Chocolate", "Harina", "Huevos", "Langostino", "Mantequilla"),
            allIngredients.map { it.name },
        )
        assertEquals(40.0, allIngredients.single { it.name == "Langostino" }.defaultWastePct, 0.0)

        val cookies = recipes.getAll().single()
        assertEquals(SampleData.COOKIES_NAME, cookies.recipe.name)
        assertEquals(5, cookies.lines.size)
        assertEquals(1, cookies.extras.size)
        assertEquals(1, recipes.observeCount().first())
    }

    private fun countRows(table: String): Int =
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }
}
