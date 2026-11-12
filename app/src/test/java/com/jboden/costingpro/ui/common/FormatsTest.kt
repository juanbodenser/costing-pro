package com.jboden.costingpro.ui.common

import com.jboden.costingpro.domain.CostCalculator
import com.jboden.costingpro.domain.IngredientPrice
import com.jboden.costingpro.domain.MeasureUnit
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormatsTest {

    @Test
    fun `money uses decimal comma, two decimals and the euro sign behind`() {
        assertEquals("0,90 €", Formats.money(BigDecimal("0.9")))
        assertEquals("0,18 €", Formats.money(BigDecimal("0.17975")))
        assertEquals("1.234,50 €", Formats.money(BigDecimal("1234.5")))
        assertEquals("0,00 €", Formats.money(BigDecimal.ZERO))
    }

    @Test
    fun `money rounds half up`() {
        assertEquals("0,13 €", Formats.money(BigDecimal("0.125")))
    }

    @Test
    fun `parseDecimal accepts comma and dot`() {
        assertEquals(BigDecimal("22.50"), Formats.parseDecimal("22,50"))
        assertEquals(BigDecimal("22.50"), Formats.parseDecimal("22.50"))
        assertEquals(BigDecimal("25"), Formats.parseDecimal(" 25 "))
        assertEquals(0, BigDecimal("0.5").compareTo(Formats.parseDecimal(",5")))
        assertEquals(0, BigDecimal("5").compareTo(Formats.parseDecimal("5,")))
    }

    @Test
    fun `parseDecimal rejects anything that is not a plain positive number`() {
        listOf("", " ", "abc", "-1", "1.234,56", "1,2,3", ",", "1e3", "2 5").forEach {
            assertNull("\"$it\"", Formats.parseDecimal(it))
        }
    }

    @Test
    fun `number drops needless zeros and keeps the minimum decimals`() {
        assertEquals("25", Formats.number(25.0))
        assertEquals("100", Formats.number(100.0))
        assertEquals("0,5", Formats.number(0.5))
        assertEquals("22,50", Formats.number(22.5, minDecimals = 2))
        assertEquals("3,00", Formats.number(3.0, minDecimals = 2))
        assertEquals("1,255", Formats.number(1.255, minDecimals = 2))
    }

    /** Phase 3 criterion: "Harina, 25 kg a 22,50 €" shows 0,90 €/kg. */
    @Test
    fun `flour typed with a comma costs 0,90 euros per kilo`() {
        val unitPrice = CostCalculator.unitPrice(
            IngredientPrice(
                purchasePrice = Formats.parseDecimal("22,50")!!,
                purchaseQuantity = Formats.parseDecimal("25")!!,
                purchaseUnit = MeasureUnit.KG,
            ),
        )!!
        assertEquals("0,90 €", Formats.money(unitPrice))
    }
}
