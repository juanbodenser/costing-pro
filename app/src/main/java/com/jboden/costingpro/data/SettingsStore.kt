package com.jboden.costingpro.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App-wide settings. For now only the default VAT, which new recipes start with.
 *
 * @param persist saves a new default VAT. The real app writes it to SharedPreferences;
 * tests keep it in memory.
 */
class SettingsStore(
    initialDefaultVatPct: Double = DEFAULT_VAT_PCT,
    private val persist: (Double) -> Unit = {},
) {

    private val _defaultVatPct = MutableStateFlow(initialDefaultVatPct)

    /** VAT, in percent, proposed for new recipes. Changing it does not touch existing recipes. */
    val defaultVatPct: StateFlow<Double> = _defaultVatPct.asStateFlow()

    fun setDefaultVatPct(value: Double) {
        require(value in 0.0..MAX_VAT_PCT) { "VAT must be between 0 and $MAX_VAT_PCT" }
        _defaultVatPct.value = value
        persist(value)
    }

    companion object {
        /** The VAT of PLAN.md until the user changes it. */
        const val DEFAULT_VAT_PCT = 10.0

        const val MAX_VAT_PCT = 100.0

        private const val PREFS = "settings"
        private const val KEY_DEFAULT_VAT = "default_vat_pct"

        /** The store of the real app, kept in the private SharedPreferences of the app. */
        fun create(context: Context): SettingsStore {
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            // Stored as text: SharedPreferences has no double, and a float would lose digits like 4.2.
            val stored = prefs.getString(KEY_DEFAULT_VAT, null)?.toDoubleOrNull()?.takeIf { it in 0.0..MAX_VAT_PCT }
            return SettingsStore(stored ?: DEFAULT_VAT_PCT) { value ->
                prefs.edit().putString(KEY_DEFAULT_VAT, value.toString()).apply()
            }
        }
    }
}
