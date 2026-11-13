package com.jboden.costingpro.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jboden.costingpro.data.SettingsStore
import com.jboden.costingpro.ui.common.Formats
import com.jboden.costingpro.ui.recipes.FieldError
import java.math.BigDecimal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class SettingsUiState(
    /** The VAT field as typed. */
    val vatText: String,
    /** Why the typed VAT was not saved, or null if it was. */
    val vatError: FieldError?,
)

class SettingsViewModel(private val settings: SettingsStore) : ViewModel() {

    private val vatText = MutableStateFlow(Formats.number(settings.defaultVatPct.value))

    val uiState: StateFlow<SettingsUiState> = vatText
        .map { SettingsUiState(vatText = it, vatError = vatError(it)) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SettingsUiState(vatText.value, vatError = null),
        )

    /** Saves the VAT as soon as what is typed is valid. Until then the stored value stays as it was. */
    fun onVatChange(value: String) {
        vatText.value = value
        if (vatError(value) == null) {
            settings.setDefaultVatPct(Formats.parseDecimal(value)!!.toDouble())
        }
    }

    private fun vatError(text: String): FieldError? {
        val value = Formats.parseDecimal(text)
        return when {
            text.isBlank() -> FieldError.REQUIRED
            value == null -> FieldError.NUMBER
            value > BigDecimal(SettingsStore.MAX_VAT_PCT) -> FieldError.VAT_RANGE
            else -> null
        }
    }

    companion object {
        fun factory(settings: SettingsStore): ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel(settings) }
        }
    }
}
