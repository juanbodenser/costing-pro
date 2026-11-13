package com.jboden.costingpro.ui.ingredients

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jboden.costingpro.R
import com.jboden.costingpro.data.DeleteResult
import com.jboden.costingpro.data.Ingredient
import com.jboden.costingpro.data.IngredientRepository
import com.jboden.costingpro.domain.Allergen
import com.jboden.costingpro.domain.CostCalculator
import com.jboden.costingpro.domain.IngredientPrice
import com.jboden.costingpro.domain.MeasureUnit
import com.jboden.costingpro.ui.common.Formats
import java.math.BigDecimal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** State of the ingredient form. Numeric fields hold the text exactly as typed. */
data class IngredientEditUiState(
    val isNew: Boolean,
    val loading: Boolean,
    val name: String = "",
    val price: String = "",
    val quantity: String = "",
    val unit: MeasureUnit = MeasureUnit.KG,
    val waste: String = "",
    @StringRes val nameError: Int? = null,
    @StringRes val priceError: Int? = null,
    @StringRes val quantityError: Int? = null,
    @StringRes val wasteError: Int? = null,
    /** Names of the recipes that use this ingredient. */
    val usedIn: List<String> = emptyList(),
    /** Allergens of mandatory declaration that the ingredient contains. */
    val allergens: Set<Allergen> = emptySet(),
    val saving: Boolean = false,
    /** The "delete?" dialog is open. */
    val confirmingDelete: Boolean = false,
    /** Non-null while the "cannot delete, it is in use" dialog is open; the recipes that use it. */
    val deleteBlockedBy: List<String>? = null,
    /** Saved or deleted: the screen must close. */
    val finished: Boolean = false,
) {
    /** Live price per base unit, or null while price or quantity are not valid. */
    val unitPrice: BigDecimal?
        get() {
            val purchasePrice = Formats.parseDecimal(price) ?: return null
            val purchaseQuantity = Formats.parseDecimal(quantity) ?: return null
            return CostCalculator.unitPrice(IngredientPrice(purchasePrice, purchaseQuantity, unit))
        }
}

/** @param ingredientId the ingredient to edit, or 0 to create one. */
class IngredientEditViewModel(
    private val repository: IngredientRepository,
    private val ingredientId: Long,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        IngredientEditUiState(isNew = ingredientId == NEW_ID, loading = ingredientId != NEW_ID),
    )
    val uiState: StateFlow<IngredientEditUiState> = _uiState.asStateFlow()

    init {
        if (ingredientId != NEW_ID) {
            viewModelScope.launch {
                val ingredient = repository.get(ingredientId)
                if (ingredient == null) {
                    _uiState.update { it.copy(finished = true) }
                    return@launch
                }
                val usedIn = repository.recipesUsing(ingredientId).map { it.name }
                _uiState.update {
                    it.copy(
                        loading = false,
                        name = ingredient.name,
                        price = Formats.number(ingredient.purchasePrice, minDecimals = 2),
                        quantity = Formats.number(ingredient.purchaseQuantity),
                        unit = ingredient.purchaseUnit,
                        waste = if (ingredient.defaultWastePct == 0.0) "" else Formats.number(ingredient.defaultWastePct),
                        usedIn = usedIn,
                        allergens = Allergen.fromMask(ingredient.allergens),
                    )
                }
            }
        }
    }

    fun onNameChange(value: String) = _uiState.update { it.copy(name = value, nameError = null) }

    fun onPriceChange(value: String) = _uiState.update { it.copy(price = value, priceError = null) }

    fun onQuantityChange(value: String) = _uiState.update { it.copy(quantity = value, quantityError = null) }

    fun onUnitChange(value: MeasureUnit) = _uiState.update { it.copy(unit = value) }

    fun onAllergenToggle(allergen: Allergen) = _uiState.update {
        it.copy(allergens = if (allergen in it.allergens) it.allergens - allergen else it.allergens + allergen)
    }

    fun onWasteChange(value: String) = _uiState.update { it.copy(waste = value, wasteError = null) }

    fun save() {
        val state = _uiState.value
        if (state.loading || state.saving || state.finished) return

        val name = state.name.trim()
        val price = Formats.parseDecimal(state.price)
        val quantity = Formats.parseDecimal(state.quantity)
        // An empty waste field means no waste.
        val waste = if (state.waste.isBlank()) BigDecimal.ZERO else Formats.parseDecimal(state.waste)

        val nameError = if (name.isEmpty()) R.string.error_required else null
        val priceError = when {
            state.price.isBlank() -> R.string.error_required
            price == null -> R.string.error_number
            else -> null
        }
        val quantityError = when {
            state.quantity.isBlank() -> R.string.error_required
            quantity == null -> R.string.error_number
            quantity.signum() == 0 -> R.string.error_quantity_zero
            else -> null
        }
        val wasteError = when {
            waste == null -> R.string.error_number
            waste > MAX_WASTE_PCT -> R.string.error_waste_range
            else -> null
        }
        if (nameError != null || priceError != null || quantityError != null || wasteError != null) {
            _uiState.update {
                it.copy(
                    nameError = nameError,
                    priceError = priceError,
                    quantityError = quantityError,
                    wasteError = wasteError,
                )
            }
            return
        }

        _uiState.update { it.copy(saving = true) }
        viewModelScope.launch {
            val sameName = repository.findByName(name)
            if (sameName != null && sameName.id != ingredientId) {
                _uiState.update { it.copy(saving = false, nameError = R.string.error_ingredient_duplicate) }
                return@launch
            }
            val ingredient = Ingredient(
                id = ingredientId,
                name = name,
                purchasePrice = price!!.toDouble(),
                purchaseQuantity = quantity!!.toDouble(),
                purchaseUnit = state.unit,
                defaultWastePct = waste!!.toDouble(),
                allergens = Allergen.maskOf(*state.allergens.toTypedArray()),
            )
            repository.save(ingredient)
            _uiState.update { it.copy(finished = true) }
        }
    }

    fun requestDelete() = _uiState.update { it.copy(confirmingDelete = true) }

    fun dismissDialogs() = _uiState.update { it.copy(confirmingDelete = false, deleteBlockedBy = null) }

    fun confirmDelete() {
        _uiState.update { it.copy(confirmingDelete = false) }
        if (ingredientId == NEW_ID) return
        viewModelScope.launch {
            when (val result = repository.delete(ingredientId)) {
                DeleteResult.Deleted -> _uiState.update { it.copy(finished = true) }
                is DeleteResult.InUse -> {
                    val names = result.recipes.map { it.name }
                    _uiState.update { it.copy(deleteBlockedBy = names, usedIn = names) }
                }
            }
        }
    }

    companion object {
        const val NEW_ID = 0L

        /** PLAN.md: waste goes from 0 to 99. */
        private val MAX_WASTE_PCT = BigDecimal(99)

        fun factory(repository: IngredientRepository, ingredientId: Long): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { IngredientEditViewModel(repository, ingredientId) }
            }
    }
}
