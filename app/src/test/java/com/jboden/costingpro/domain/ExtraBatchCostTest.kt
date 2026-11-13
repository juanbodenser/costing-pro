package com.jboden.costingpro.domain

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Test

class ExtraBatchCostTest {

    private fun assertAmount(expected: String, actual: BigDecimal) {
        assertEquals("expected $expected but was $actual", 0, BigDecimal(expected).compareTo(actual))
    }

    @Test
    fun `a per-batch extra costs its amount whatever the yield`() {
        val labour = ExtraCostInput(BigDecimal("3.00"), ExtraCostPer.BATCH)

        assertAmount("3.00", CostCalculator.extraBatchCost(labour, BigDecimal(40)))
        assertAmount("3.00", CostCalculator.extraBatchCost(labour, BigDecimal.ZERO))
    }

    @Test
    fun `a per-unit extra costs its amount times the yield`() {
        val bag = ExtraCostInput(BigDecimal("0.05"), ExtraCostPer.UNIT)

        assertAmount("2.00", CostCalculator.extraBatchCost(bag, BigDecimal(40)))
        assertAmount("0.125", CostCalculator.extraBatchCost(bag, BigDecimal("2.5")))
    }

    @Test
    fun `a per-unit extra adds nothing without a yield`() {
        val bag = ExtraCostInput(BigDecimal("0.05"), ExtraCostPer.UNIT)

        assertAmount("0", CostCalculator.extraBatchCost(bag, BigDecimal.ZERO))
    }

    @Test
    fun `the extras of a recipe are the sum of what each one adds`() {
        val recipe = RecipeInput(
            yieldQuantity = BigDecimal(40),
            yieldUnit = MeasureUnit.UD,
            lines = emptyList(),
            extras = listOf(
                ExtraCostInput(BigDecimal("0.05"), ExtraCostPer.UNIT),
                ExtraCostInput(BigDecimal("3.00"), ExtraCostPer.BATCH),
                ExtraCostInput(BigDecimal("0.10"), ExtraCostPer.UNIT),
            ),
        )

        val cost = CostCalculator.cost(recipe)

        // 2,00 + 3,00 + 4,00.
        assertAmount("9.00", cost.extrasCost)
        assertAmount("0.225", cost.extrasUnitCost!!)
    }
}
