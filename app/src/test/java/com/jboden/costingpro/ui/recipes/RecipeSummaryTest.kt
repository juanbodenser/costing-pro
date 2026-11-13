package com.jboden.costingpro.ui.recipes

import com.jboden.costingpro.data.ExtraCost
import com.jboden.costingpro.data.Ingredient
import com.jboden.costingpro.data.Recipe
import com.jboden.costingpro.data.RecipeLine
import com.jboden.costingpro.data.RecipeWithDetails
import com.jboden.costingpro.domain.ExtraCostPer
import com.jboden.costingpro.domain.MarginStatus
import com.jboden.costingpro.domain.MeasureUnit
import com.jboden.costingpro.domain.PricingMode
import java.math.BigDecimal
import java.math.RoundingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecipeSummaryTest {

    private fun ingredient(id: Long, name: String, price: Double, quantity: Double, unit: MeasureUnit) =
        Ingredient(id = id, name = name, purchasePrice = price, purchaseQuantity = quantity, purchaseUnit = unit)

    private val catalogue = listOf(
        ingredient(1, "Harina", 22.50, 25.0, MeasureUnit.KG),
        ingredient(2, "Mantequilla", 8.0, 1.0, MeasureUnit.KG),
        ingredient(3, "Azúcar", 1.20, 1.0, MeasureUnit.KG),
        ingredient(4, "Chocolate", 10.0, 1.0, MeasureUnit.KG),
        ingredient(5, "Huevos", 3.0, 12.0, MeasureUnit.UD),
    ).associateBy { it.id }

    private fun line(ingredientId: Long, quantity: Double, unit: MeasureUnit) =
        RecipeLine(ingredientId = ingredientId, quantity = quantity, unit = unit)

    /** The cookies of PLAN.md, with the bag extra and a price of 1,00 €. */
    private fun cookies(pricingValue: Double = 1.0, mode: PricingMode = PricingMode.PRICE) = RecipeWithDetails(
        recipe = Recipe(
            id = 7, name = "Galletas", yieldQuantity = 40.0, yieldUnit = MeasureUnit.UD,
            pricingMode = mode, pricingValue = pricingValue, photoPath = "photos/a.jpg",
        ),
        lines = listOf(
            line(1, 500.0, MeasureUnit.G),
            line(2, 250.0, MeasureUnit.G),
            line(3, 200.0, MeasureUnit.G),
            line(4, 200.0, MeasureUnit.G),
            line(5, 2.0, MeasureUnit.UD),
        ),
        extras = listOf(ExtraCost(name = "Bolsa", amount = 0.05, per = ExtraCostPer.UNIT)),
    )

    private fun BigDecimal?.money() = this!!.setScale(2, RoundingMode.HALF_UP).toPlainString()

    private fun BigDecimal?.pct() = this!!.setScale(1, RoundingMode.HALF_UP).toPlainString()

    @Test
    fun `the full cookies case matches PLAN`() {
        val summary = RecipeSummaryLogic.summarize(cookies(), catalogue)

        assertEquals("5.19", summary.cost.rawMaterialCost.money())
        assertEquals("7.19", summary.cost.batchCost.money())
        assertEquals("0.18", summary.cost.unitCost.money())
        assertEquals("1.00", summary.price!!.netPrice.money())
        assertEquals("1.10", summary.price!!.priceWithVat.money())
        assertEquals("0.82", summary.price!!.profitPerUnit.money())
        assertEquals("82.0", summary.price!!.marginPct.pct())
        assertEquals("18.0", summary.price!!.foodCostPct.pct())
        assertEquals(MarginStatus.GOOD, summary.status)
        assertEquals("photos/a.jpg", summary.photoPath)
    }

    @Test
    fun `the breakdown lists each row with its share of the batch cost, extras last`() {
        val breakdown = RecipeSummaryLogic.summarize(cookies(), catalogue).breakdown

        assertEquals(listOf("Harina", "Mantequilla", "Azúcar", "Chocolate", "Huevos", "Bolsa"), breakdown.map { it.name })
        assertEquals(listOf("0.45", "2.00", "0.24", "2.00", "0.50", "2.00"), breakdown.map { it.cost.money() })
        // 2,00 € of 7,19 €.
        assertEquals("27.8", breakdown[1].sharePct.pct())
        assertEquals("27.8", breakdown.last().sharePct.pct())
        assertEquals(100.0, breakdown.sumOf { it.sharePct!!.toDouble() }, 0.0001)
    }

    @Test
    fun `sorting puts the most expensive rows first`() {
        val breakdown = RecipeSummaryLogic.summarize(cookies(), catalogue).breakdown

        val sorted = RecipeSummaryLogic.sortedByCost(breakdown)

        assertEquals(listOf("2.00", "2.00", "2.00", "0.50", "0.45", "0.24"), sorted.map { it.cost.money() })
        // Equal costs keep their recipe order.
        assertEquals(listOf("Mantequilla", "Chocolate", "Bolsa"), sorted.take(3).map { it.name })
    }

    @Test
    fun `a stored price of zero means no price`() {
        val summary = RecipeSummaryLogic.summarize(cookies(pricingValue = 0.0), catalogue)

        assertNull(summary.price)
        assertNull(summary.status)
    }

    @Test
    fun `a markup of zero is a price equal to the cost`() {
        val summary = RecipeSummaryLogic.summarize(cookies(pricingValue = 0.0, mode = PricingMode.MARKUP), catalogue)

        assertEquals("0.18", summary.price!!.netPrice.money())
        assertEquals("100.0", summary.price!!.foodCostPct.pct())
        assertEquals(MarginStatus.LOW, summary.status)
    }

    @Test
    fun `without yield there is no unit cost`() {
        val details = cookies()
        val summary = RecipeSummaryLogic.summarize(
            details.copy(recipe = details.recipe.copy(yieldQuantity = 0.0)),
            catalogue,
        )

        assertNull(summary.cost.unitCost)
        assertNull(summary.status)
    }

    @Test
    fun `rows of a missing ingredient and sub-recipe rows are left out`() {
        val details = cookies()
        val summary = RecipeSummaryLogic.summarize(
            details.copy(lines = details.lines + RecipeLine(subRecipeId = 99, quantity = 1.0, unit = MeasureUnit.L)),
            catalogue - 5L,
        )

        assertEquals(listOf("Harina", "Mantequilla", "Azúcar", "Chocolate", "Bolsa"), summary.breakdown.map { it.name })
    }
}
