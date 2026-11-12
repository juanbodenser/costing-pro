package com.jboden.costingpro.ui.ingredients

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jboden.costingpro.data.Ingredient
import com.jboden.costingpro.data.IngredientRepository
import com.jboden.costingpro.data.toPrice
import com.jboden.costingpro.domain.CostCalculator
import com.jboden.costingpro.domain.MeasureUnit
import java.math.BigDecimal
import java.text.Normalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** One ingredient of the catalogue list. */
data class IngredientRow(
    val id: Long,
    val name: String,
    val purchasePrice: BigDecimal,
    val purchaseQuantity: Double,
    val purchaseUnit: MeasureUnit,
    /** Price per [baseUnit], or null if the purchase quantity is not positive. */
    val unitPrice: BigDecimal?,
    val baseUnit: MeasureUnit,
)

data class IngredientListUiState(
    val loading: Boolean = true,
    val query: String = "",
    /** Ingredients that match [query], by name. */
    val rows: List<IngredientRow> = emptyList(),
    /** True when the catalogue has no ingredients at all, whatever the query. */
    val catalogueEmpty: Boolean = false,
)

class IngredientListViewModel(repository: IngredientRepository) : ViewModel() {

    private val query = MutableStateFlow("")

    val uiState: StateFlow<IngredientListUiState> =
        combine(repository.observeAll(), query) { ingredients, query ->
            val needle = query.searchKey()
            IngredientListUiState(
                loading = false,
                query = query,
                rows = ingredients.filter { needle in it.name.searchKey() }.map { it.toRow() },
                catalogueEmpty = ingredients.isEmpty(),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IngredientListUiState())

    fun onQueryChange(value: String) {
        query.value = value
    }

    private fun Ingredient.toRow(): IngredientRow {
        val price = toPrice()
        return IngredientRow(
            id = id,
            name = name,
            purchasePrice = price.purchasePrice,
            purchaseQuantity = purchaseQuantity,
            purchaseUnit = purchaseUnit,
            unitPrice = CostCalculator.unitPrice(price),
            baseUnit = purchaseUnit.baseUnit,
        )
    }

    companion object {
        fun factory(repository: IngredientRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { IngredientListViewModel(repository) }
        }
    }
}

private val diacritics = Regex("\\p{Mn}+")

/** Lower case and without accents, so "azucar" finds "Azúcar". */
internal fun String.searchKey(): String =
    Normalizer.normalize(trim(), Normalizer.Form.NFD).replace(diacritics, "").lowercase()
