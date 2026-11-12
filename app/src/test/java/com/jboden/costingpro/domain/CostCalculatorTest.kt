package com.jboden.costingpro.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class CostCalculatorTest {

    // ---------- Helpers ----------

    private fun bd(value: String) = BigDecimal(value)

    /** Compares numerically, ignoring scale (6 equals 6.00). */
    private fun assertAmount(expected: String, actual: BigDecimal?) {
        assertNotNull("expected $expected but was null", actual)
        assertTrue("expected $expected but was $actual", bd(expected).compareTo(actual) == 0)
    }

    private fun ingredient(price: String, quantity: String, unit: MeasureUnit) =
        LineSource.Ingredient(IngredientPrice(bd(price), bd(quantity), unit))

    private fun line(source: LineSource, quantity: String, unit: MeasureUnit, waste: String = "0") =
        LineInput(source, bd(quantity), unit, bd(waste))

    private val flour = ingredient("22.50", "25", MeasureUnit.KG) // 0.90 €/kg
    private val butter = ingredient("8.00", "1", MeasureUnit.KG)
    private val sugar = ingredient("1.20", "1", MeasureUnit.KG)
    private val chocolate = ingredient("10.00", "1", MeasureUnit.KG)
    private val eggs = ingredient("3.00", "12", MeasureUnit.UD) // 0.25 €/ud

    private val cookieLines = listOf(
        line(flour, "500", MeasureUnit.G),
        line(butter, "250", MeasureUnit.G),
        line(sugar, "200", MeasureUnit.G),
        line(chocolate, "200", MeasureUnit.G),
        line(eggs, "2", MeasureUnit.UD),
    )

    /** The test case from PLAN.md: chocolate cookies, batch of 40 units, a 0.05 € bag per unit. */
    private val cookies = RecipeInput(
        yieldQuantity = bd("40"),
        yieldUnit = MeasureUnit.UD,
        lines = cookieLines,
        extras = listOf(ExtraCostInput(bd("0.05"), ExtraCostPer.UNIT)),
    )

    // ---------- Cookies case ----------

    @Test
    fun cookies_lineCosts() {
        val lines = CostCalculator.cost(cookies).lines

        assertAmount("0.45", lines[0].cost)
        assertAmount("2.00", lines[1].cost)
        assertAmount("0.24", lines[2].cost)
        assertAmount("2.00", lines[3].cost)
        assertAmount("0.50", lines[4].cost)
        assertAmount("0.90", lines[0].unitPrice)
        assertAmount("0.25", lines[4].unitPrice)
    }

    @Test
    fun cookies_totals() {
        val cost = CostCalculator.cost(cookies)

        assertAmount("5.19", cost.rawMaterialCost)
        assertAmount("2.00", cost.extrasCost)
        assertAmount("7.19", cost.batchCost)
        assertAmount("0.17975", cost.unitCost)
        assertAmount("0.12975", cost.rawMaterialUnitCost)
        assertAmount("0.05", cost.extrasUnitCost)
        assertFalse(cost.hasInvalidLines)
        assertEquals(bd("0.18"), cost.unitCost!!.toMoneyDisplay())
    }

    @Test
    fun cookies_withoutExtras_matchesPhase4Criterion() {
        val cost = CostCalculator.cost(cookies.copy(extras = emptyList()))

        assertAmount("5.19", cost.batchCost)
        assertEquals(bd("0.13"), cost.unitCost!!.toMoneyDisplay())
    }

    @Test
    fun cookies_priceOfOneEuro() {
        val cost = CostCalculator.cost(cookies)
        val pricing = Pricing(PricingMode.PRICE, bd("1.00"), vatPct = bd("10"))

        val result = CostCalculator.price(cost.unitCost, pricing, cookies.yieldQuantity)

        assertAmount("1.00", result.netPrice)
        assertAmount("0.82025", result.profitPerUnit)
        assertAmount("32.81", result.profitPerBatch)
        assertAmount("82.025", result.marginPct)
        assertAmount("17.975", result.foodCostPct)
        assertAmount("1.10", result.priceWithVat)

        assertEquals(bd("0.82"), result.profitPerUnit!!.toMoneyDisplay())
        assertEquals(bd("82.0"), result.marginPct!!.toPercentDisplay())
        assertEquals(bd("18.0"), result.foodCostPct!!.toPercentDisplay())
        assertEquals(bd("456.3"), result.markupPct!!.toPercentDisplay())
        assertEquals(bd("1.10"), result.priceWithVat!!.toMoneyDisplay())
    }

    // ---------- Waste ----------

    @Test
    fun waste_prawnExample_dividesByUsableFraction() {
        val prawn = ingredient("18", "1", MeasureUnit.KG)

        val result = CostCalculator.lineCost(line(prawn, "200", MeasureUnit.G, waste = "40"))

        // 0.2 × 18 ÷ 0.6 = 6.00, not the 5.04 that multiplying by (1 + waste) would give.
        assertAmount("6.00", result.cost)
        assertNull(result.issue)
    }

    @Test
    fun waste_outOfRange_isInvalid() {
        val prawn = ingredient("18", "1", MeasureUnit.KG)

        assertEquals(
            LineIssue.INVALID_WASTE,
            CostCalculator.lineCost(line(prawn, "200", MeasureUnit.G, waste = "100")).issue,
        )
        assertEquals(
            LineIssue.INVALID_WASTE,
            CostCalculator.lineCost(line(prawn, "200", MeasureUnit.G, waste = "-1")).issue,
        )
        assertNull(CostCalculator.lineCost(line(prawn, "200", MeasureUnit.G, waste = "99")).issue)
    }

    // ---------- Units ----------

    @Test
    fun unitPrice_convertsPurchaseFormatToBaseUnit() {
        assertAmount("0.90", CostCalculator.unitPrice(IngredientPrice(bd("22.50"), bd("25"), MeasureUnit.KG)))
        // 1.50 € for 500 g is 3.00 €/kg; 0.60 € for 750 ml is 0.80 €/l.
        assertAmount("3.00", CostCalculator.unitPrice(IngredientPrice(bd("1.50"), bd("500"), MeasureUnit.G)))
        assertAmount("0.80", CostCalculator.unitPrice(IngredientPrice(bd("0.60"), bd("750"), MeasureUnit.ML)))
        assertNull(CostCalculator.unitPrice(IngredientPrice(bd("1.50"), bd("0"), MeasureUnit.G)))
    }

    @Test
    fun line_convertsWithinSameMagnitude() {
        val oil = ingredient("20", "5", MeasureUnit.L) // 4 €/l

        assertAmount("1.00", CostCalculator.lineCost(line(oil, "250", MeasureUnit.ML)).cost)
        assertAmount("1.80", CostCalculator.lineCost(line(flour, "2", MeasureUnit.KG)).cost)
    }

    @Test
    fun line_acrossMagnitudes_isInvalid() {
        val result = CostCalculator.lineCost(line(flour, "250", MeasureUnit.ML))

        assertEquals(LineIssue.UNIT_MISMATCH, result.issue)
        assertNull(result.cost)
    }

    @Test
    fun invalidLines_areLeftOutOfTotalsAndFlagged() {
        val recipe = cookies.copy(lines = cookieLines + line(flour, "250", MeasureUnit.ML))

        val cost = CostCalculator.cost(recipe)

        assertTrue(cost.hasInvalidLines)
        assertAmount("5.19", cost.rawMaterialCost)
    }

    @Test
    fun line_withZeroPurchaseQuantity_isInvalid() {
        val broken = ingredient("5", "0", MeasureUnit.KG)

        assertEquals(
            LineIssue.INVALID_PURCHASE_QUANTITY,
            CostCalculator.lineCost(line(broken, "100", MeasureUnit.G)).issue,
        )
    }

    // ---------- Extras ----------

    @Test
    fun extras_batchAndUnitAreCombined() {
        val recipe = cookies.copy(
            extras = listOf(
                ExtraCostInput(bd("0.05"), ExtraCostPer.UNIT),
                ExtraCostInput(bd("3.00"), ExtraCostPer.BATCH),
            ),
        )

        val cost = CostCalculator.cost(recipe)

        assertAmount("5.00", cost.extrasCost) // 3.00 + 0.05 × 40
        assertAmount("10.19", cost.batchCost)
    }

    // ---------- Yield ----------

    @Test
    fun zeroYield_hasNoUnitCostNorMargin() {
        val cost = CostCalculator.cost(cookies.copy(yieldQuantity = BigDecimal.ZERO))

        assertAmount("5.19", cost.rawMaterialCost)
        assertNull(cost.unitCost)
        assertNull(cost.rawMaterialUnitCost)
        assertNull(cost.extrasUnitCost)

        val pricing = Pricing(PricingMode.MARKUP, bd("200"), vatPct = bd("10"))
        val result = CostCalculator.price(cost.unitCost, pricing, BigDecimal.ZERO)
        assertNull(result.netPrice)
        assertNull(result.marginPct)
        assertNull(result.profitPerUnit)
    }

    // ---------- Sub-recipes ----------

    @Test
    fun subRecipe_costsByItsUnitCost() {
        // Stock: 12 € of bones for 3 l, so 4 €/l.
        val bones = ingredient("3", "1", MeasureUnit.KG)
        val stock = RecipeInput(
            yieldQuantity = bd("3"),
            yieldUnit = MeasureUnit.L,
            lines = listOf(line(bones, "4", MeasureUnit.KG)),
        )
        val dish = RecipeInput(
            yieldQuantity = bd("10"),
            yieldUnit = MeasureUnit.RACION,
            lines = listOf(
                line(LineSource.SubRecipe(stock), "500", MeasureUnit.ML),
                line(LineSource.SubRecipe(stock), "500", MeasureUnit.ML, waste = "20"),
            ),
        )

        val cost = CostCalculator.cost(dish)

        assertAmount("4", cost.lines[0].unitPrice)
        assertAmount("2.00", cost.lines[0].cost)
        assertAmount("2.50", cost.lines[1].cost)
        assertAmount("0.45", cost.unitCost)
    }

    @Test
    fun subRecipe_unitCostIncludesItsExtras() {
        val dough = RecipeInput(
            yieldQuantity = bd("2"),
            yieldUnit = MeasureUnit.KG,
            lines = listOf(line(flour, "2", MeasureUnit.KG)), // 1.80
            extras = listOf(ExtraCostInput(bd("0.20"), ExtraCostPer.BATCH)),
        )

        val result = CostCalculator.lineCost(line(LineSource.SubRecipe(dough), "500", MeasureUnit.G))

        assertAmount("1.00", result.unitPrice)
        assertAmount("0.50", result.cost)
    }

    @Test
    fun subRecipe_withoutYieldOrWithOtherMagnitude_isInvalid() {
        val noYield = RecipeInput(BigDecimal.ZERO, MeasureUnit.L, listOf(line(flour, "1", MeasureUnit.KG)))
        val litres = noYield.copy(yieldQuantity = bd("2"))

        assertEquals(
            LineIssue.SUBRECIPE_WITHOUT_YIELD,
            CostCalculator.lineCost(line(LineSource.SubRecipe(noYield), "1", MeasureUnit.L)).issue,
        )
        assertEquals(
            LineIssue.UNIT_MISMATCH,
            CostCalculator.lineCost(line(LineSource.SubRecipe(litres), "1", MeasureUnit.KG)).issue,
        )
    }

    // ---------- Linked price fields ----------

    @Test
    fun price_fromMarkup() {
        val result = CostCalculator.price(bd("2.00"), Pricing(PricingMode.MARKUP, bd("150"), bd("10")), bd("4"))

        assertAmount("5.00", result.netPrice)
        assertAmount("3.00", result.profitPerUnit)
        assertAmount("12.00", result.profitPerBatch)
        assertAmount("60", result.marginPct)
        assertAmount("150", result.markupPct)
        assertAmount("40", result.foodCostPct)
        assertAmount("5.50", result.priceWithVat)
    }

    @Test
    fun price_fromFoodCostTarget() {
        val result = CostCalculator.price(bd("2.00"), Pricing(PricingMode.FOOD_COST, bd("25"), bd("21")), bd("1"))

        assertAmount("8.00", result.netPrice)
        assertAmount("75", result.marginPct)
        assertAmount("300", result.markupPct)
        assertAmount("25", result.foodCostPct)
        assertAmount("9.68", result.priceWithVat)
    }

    @Test
    fun price_threeModesAgreeOnTheSamePrice() {
        val unitCost = bd("0.17975")
        val fromPrice = CostCalculator.price(unitCost, Pricing(PricingMode.PRICE, bd("1.00"), bd("10")), bd("40"))

        val fromMarkup = CostCalculator.price(
            unitCost, Pricing(PricingMode.MARKUP, fromPrice.markupPct!!, bd("10")), bd("40"),
        )
        val fromFoodCost = CostCalculator.price(
            unitCost, Pricing(PricingMode.FOOD_COST, fromPrice.foodCostPct!!, bd("10")), bd("40"),
        )

        assertEquals(bd("1.00"), fromMarkup.netPrice!!.toMoneyDisplay())
        assertEquals(bd("1.00"), fromFoodCost.netPrice!!.toMoneyDisplay())
    }

    @Test
    fun price_divisionsByZeroGiveNull() {
        val zeroFoodCost = CostCalculator.price(bd("2"), Pricing(PricingMode.FOOD_COST, bd("0"), bd("10")), bd("1"))
        assertNull(zeroFoodCost.netPrice)
        assertNull(zeroFoodCost.marginPct)

        val zeroPrice = CostCalculator.price(bd("2"), Pricing(PricingMode.PRICE, bd("0"), bd("10")), bd("1"))
        assertAmount("-2", zeroPrice.profitPerUnit)
        assertNull(zeroPrice.marginPct)
        assertNull(zeroPrice.foodCostPct)

        val zeroCost = CostCalculator.price(bd("0"), Pricing(PricingMode.PRICE, bd("3"), bd("10")), bd("1"))
        assertAmount("100", zeroCost.marginPct)
        assertNull(zeroCost.markupPct)
    }

    @Test
    fun price_fixedPriceWithoutUnitCost_keepsThePriceOnly() {
        val result = CostCalculator.price(null, Pricing(PricingMode.PRICE, bd("3.00"), bd("10")), BigDecimal.ZERO)

        assertAmount("3.00", result.netPrice)
        assertAmount("3.30", result.priceWithVat)
        assertNull(result.profitPerUnit)
        assertNull(result.marginPct)
    }

    // ---------- VAT ----------

    @Test
    fun vat_netPriceFromPriceWithVat() {
        assertAmount("1.00", CostCalculator.netPriceFromPriceWithVat(bd("1.10"), bd("10")))
        assertAmount("1.10", CostCalculator.priceWithVat(bd("1.00"), bd("10")))
        assertNull(CostCalculator.netPriceFromPriceWithVat(bd("1.10"), bd("-100")))
    }
}
