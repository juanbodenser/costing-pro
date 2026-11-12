package com.jboden.costingpro.ui.common

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Spanish number formatting and parsing for the whole UI. Pure Kotlin. */
object Formats {

    private const val NBSP = ' '

    // Set by hand so the result does not depend on the locale data of the device.
    private val symbols = DecimalFormatSymbols(Locale.ROOT).apply {
        decimalSeparator = ','
        groupingSeparator = '.'
    }

    private val inputPattern = Regex("""\d+([.,]\d*)?|[.,]\d+""")

    /** "0,90 €": two decimals, decimal comma and the euro sign after a non-breaking space. */
    fun money(amount: BigDecimal): String {
        val format = DecimalFormat("#,##0.00", symbols).apply { roundingMode = RoundingMode.HALF_UP }
        return format.format(amount) + NBSP + "€"
    }

    /**
     * Parses what the user typed in a numeric field. Comma and dot both work as decimal
     * separator; thousands separators and negative numbers are not accepted.
     */
    fun parseDecimal(text: String): BigDecimal? {
        val trimmed = text.trim()
        if (!inputPattern.matches(trimmed)) return null
        return trimmed.replace(',', '.').toBigDecimalOrNull()
    }

    /** Text for a numeric field or a quantity: decimal comma, no grouping, no needless zeros. */
    fun number(value: Double, minDecimals: Int = 0): String {
        var decimal = BigDecimal.valueOf(value).stripTrailingZeros()
        if (decimal.scale() < minDecimals) decimal = decimal.setScale(minDecimals)
        return decimal.toPlainString().replace('.', ',')
    }
}
