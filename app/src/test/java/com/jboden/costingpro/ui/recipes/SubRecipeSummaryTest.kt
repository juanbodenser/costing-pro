package com.jboden.costingpro.ui.recipes

import com.jboden.costingpro.data.ExtraCost
import com.jboden.costingpro.data.Ingredient
import com.jboden.costingpro.data.Recipe
import com.jboden.costingpro.data.RecipeLine
import com.jboden.costingpro.data.RecipeWithDetails
import com.jboden.costingpro.domain.ExtraCostPer
import com.jboden.costingpro.domain.MeasureUnit
import com.jboden.costingpro.domain.PricingMode
import java.math.BigDecimal
import java.math.RoundingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Costing stored recipes that use other recipes (phase 6). */
class SubRecipeSummaryTest {

    private fun ingredient(id: Long, name: String, price: Double, unit: MeasureUnit) =
        Ingredient(id = id, name = name, purchasePrice = price, purchaseQuantity = 1.0, purchaseUnit = unit)

    private val tomato = ingredient(6, "Tomate", 2.0, MeasureUnit.KG)
    private val pasta = ingredient(7, "Pasta", 1.50, MeasureUnit.KG)
    private val catalogue = listOf(tomato, pasta).associateBy { it.id }

    private fun line(ingredientId: Long, quantity: Double, unit: MeasureUnit) =
        RecipeLine(ingredientId = ingredientId, quantity = quantity, unit = unit)

    /** Sauce: 3 kg of tomato at 2 €/kg make 2 l, so 6,00 € a batch and 3,00 € a litre. */
    private val sauce = RecipeWithDetails(
        recipe = Recipe(id = 10, name = "Salsa de tomate", yieldQuantity = 2.0, yieldUnit = MeasureUnit.L),
        lines = listOf(line(6, 3.0, MeasureUnit.KG)),
        extras = emptyList(),
    )

    /** Dish: 400 g of pasta (0,60 €) and 500 ml of the sauce (1,50 €) make 4 portions. */
    private val dish = RecipeWithDetails(
        recipe = Recipe(
            id = 11, name = "Pasta con tomate", yieldQuantity = 4.0, yieldUnit = MeasureUnit.RACION,
            pricingMode = PricingMode.PRICE, pricingValue = 2.0,
        ),
        lines = listOf(
            line(7, 400.0, MeasureUnit.G),
            RecipeLine(subRecipeId = 10, quantity = 500.0, unit = MeasureUnit.ML),
        ),
        extras = emptyList(),
    )

    private val recipes = listOf(sauce, dish).associateBy { it.recipe.id }

    private fun BigDecimal?.money() = this!!.setScale(2, RoundingMode.HALF_UP).toPlainString()

    @Test
    fun `a recipe is costed with the unit cost of its sub-recipe`() {
        val summary = RecipeSummaryLogic.summarize(dish, catalogue, recipes)

        assertEquals("2.10", summary.cost.batchCost.money())
        assertEquals("0.53", summary.cost.unitCost.money())
        assertEquals(listOf("Pasta", "Salsa de tomate"), summary.breakdown.map { it.name })
        assertEquals(listOf(false, true), summary.breakdown.map { it.isSubRecipe })
        assertEquals(listOf("0.60", "1.50"), summary.breakdown.map { it.cost.money() })
    }

    /** The criterion of phase 6: raising the price of an ingredient of the sauce changes the dish that uses it. */
    @Test
    fun `raising an ingredient of the sauce raises the cost of the dish`() {
        val before = RecipeSummaryLogic.summarize(dish, catalogue, recipes)

        val dearerTomato = catalogue + (6L to tomato.copy(purchasePrice = 3.0))
        val after = RecipeSummaryLogic.summarize(dish, dearerTomato, recipes)

        assertEquals("2.10", before.cost.batchCost.money())
        // Sauce: 9,00 € a batch, 4,50 € a litre, 2,25 € for 500 ml; with the pasta, 2,85 €.
        assertEquals("2.85", after.cost.batchCost.money())
        assertEquals("0.71", after.cost.unitCost.money())
    }

    @Test
    fun `a change in a sub-recipe reaches the recipes two levels up`() {
        val menu = RecipeWithDetails(
            recipe = Recipe(id = 12, name = "Menú", yieldQuantity = 1.0, yieldUnit = MeasureUnit.UD),
            lines = listOf(RecipeLine(subRecipeId = 11, quantity = 2.0, unit = MeasureUnit.RACION)),
            extras = emptyList(),
        )
        val all = recipes + (12L to menu)

        // Two portions of the dish, at 0,525 € each.
        assertEquals("1.05", RecipeSummaryLogic.summarize(menu, catalogue, all).cost.batchCost.money())

        val thickerSauce = all + (10L to sauce.copy(recipe = sauce.recipe.copy(yieldQuantity = 1.0)))
        // The sauce now makes 1 l, so 6,00 € a litre and 3,00 € for 500 ml: dish 3,60 €, 0,90 € a portion, menu 1,80 €.
        assertEquals("1.80", RecipeSummaryLogic.summarize(menu, catalogue, thickerSauce).cost.batchCost.money())
    }

    @Test
    fun `the extras of a sub-recipe count in its unit cost`() {
        val bottled = sauce.copy(extras = listOf(ExtraCost(name = "Frasco", amount = 1.0, per = ExtraCostPer.BATCH)))
        val all = mapOf(10L to bottled, 11L to dish)

        // Batch 7,00 € over 2 l: 3,50 € a litre, 1,75 € for 500 ml; with the pasta, 2,35 €.
        assertEquals("2.35", RecipeSummaryLogic.summarize(dish, catalogue, all).cost.batchCost.money())
    }

    @Test
    fun `a sub-recipe line in a unit of another kind is not counted`() {
        val wrongUnit = dish.copy(lines = listOf(RecipeLine(subRecipeId = 10, quantity = 1.0, unit = MeasureUnit.KG)))

        val summary = RecipeSummaryLogic.summarize(wrongUnit, catalogue, recipes)

        assertEquals("0.00", summary.cost.batchCost.money())
        assertNull(summary.breakdown.single().cost)
        assertEquals(true, summary.cost.hasInvalidLines)
    }

    @Test
    fun `a sub-recipe without yield cannot be costed`() {
        val noYield = mapOf(10L to sauce.copy(recipe = sauce.recipe.copy(yieldQuantity = 0.0)), 11L to dish)

        val summary = RecipeSummaryLogic.summarize(dish, catalogue, noYield)

        assertEquals("0.60", summary.cost.batchCost.money())
        assertNull(summary.breakdown[1].cost)
    }

    @Test
    fun `stored data with a loop does not hang the calculation`() {
        fun recipe(id: Long, name: String, ingredientId: Long, subRecipeId: Long) = RecipeWithDetails(
            recipe = Recipe(id = id, name = name, yieldQuantity = 1.0, yieldUnit = MeasureUnit.UD),
            lines = listOf(
                line(ingredientId, 1.0, MeasureUnit.KG),
                RecipeLine(subRecipeId = subRecipeId, quantity = 1.0, unit = MeasureUnit.UD),
            ),
            extras = emptyList(),
        )
        val a = recipe(20, "A", ingredientId = 6, subRecipeId = 21)
        val b = recipe(21, "B", ingredientId = 7, subRecipeId = 20)

        val summary = RecipeSummaryLogic.summarize(a, catalogue, mapOf(20L to a, 21L to b))

        // A costs its tomato (2,00 €) plus B without the line that points back to A (1,50 €).
        assertEquals("3.50", summary.cost.batchCost.money())
    }
}
