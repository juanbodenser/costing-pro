package com.jboden.costingpro.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SettingsStoreTest {

    @Test
    fun `the default VAT starts at 10 percent`() {
        assertEquals(10.0, SettingsStore().defaultVatPct.value, 0.0)
    }

    @Test
    fun `setting the default VAT updates the flow and persists it`() {
        val saved = mutableListOf<Double>()
        val store = SettingsStore(persist = { saved += it })

        store.setDefaultVatPct(21.0)
        store.setDefaultVatPct(4.5)

        assertEquals(4.5, store.defaultVatPct.value, 0.0)
        assertEquals(listOf(21.0, 4.5), saved)
    }

    @Test
    fun `a stored value is the starting point`() {
        assertEquals(7.0, SettingsStore(initialDefaultVatPct = 7.0).defaultVatPct.value, 0.0)
    }

    @Test
    fun `zero and one hundred are valid and anything outside is refused`() {
        val saved = mutableListOf<Double>()
        val store = SettingsStore(persist = { saved += it })

        store.setDefaultVatPct(0.0)
        store.setDefaultVatPct(100.0)
        assertThrows(IllegalArgumentException::class.java) { store.setDefaultVatPct(-1.0) }
        assertThrows(IllegalArgumentException::class.java) { store.setDefaultVatPct(100.1) }

        assertEquals(100.0, store.defaultVatPct.value, 0.0)
        assertEquals(listOf(0.0, 100.0), saved)
    }
}
