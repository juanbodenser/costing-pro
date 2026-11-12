package com.jboden.costingpro.domain

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Test

class MarginStatusTest {

    private fun status(foodCost: String) = MarginStatus.fromFoodCost(BigDecimal(foodCost))

    @Test
    fun `up to 30 percent is good`() {
        assertEquals(MarginStatus.GOOD, status("0"))
        assertEquals(MarginStatus.GOOD, status("18"))
        assertEquals(MarginStatus.GOOD, status("30"))
    }

    @Test
    fun `above 30 and up to 35 is fair`() {
        assertEquals(MarginStatus.FAIR, status("30.01"))
        assertEquals(MarginStatus.FAIR, status("35"))
    }

    @Test
    fun `above 35 is low`() {
        assertEquals(MarginStatus.LOW, status("35.01"))
        assertEquals(MarginStatus.LOW, status("120"))
    }
}
