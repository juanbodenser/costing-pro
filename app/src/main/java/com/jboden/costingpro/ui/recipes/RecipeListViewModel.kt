package com.jboden.costingpro.ui.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jboden.costingpro.data.IngredientRepository
import com.jboden.costingpro.data.RecipeRepository
import com.jboden.costingpro.ui.ingredients.searchKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class RecipeListUiState(
    val loading: Boolean = true,
    val query: String = "",
    /** The recipes that match [query], by name. */
    val rows: List<RecipeSummary> = emptyList(),
    /** True when there are no recipes at all, whatever the query. */
    val listEmpty: Boolean = false,
)

class RecipeListViewModel(recipes: RecipeRepository, ingredients: IngredientRepository) : ViewModel() {

    private val query = MutableStateFlow("")

    // Costs are computed from the current catalogue, so a price change shows up here at once.
    val uiState: StateFlow<RecipeListUiState> =
        combine(recipes.observeAll(), ingredients.observeAll(), query) { stored, catalogue, query ->
            val byId = catalogue.associateBy { it.id }
            val needle = query.searchKey()
            val all = stored.map { RecipeSummaryLogic.summarize(it, byId) }
            RecipeListUiState(
                loading = false,
                query = query,
                rows = all.filter { needle in it.name.searchKey() },
                listEmpty = all.isEmpty(),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecipeListUiState())

    fun onQueryChange(value: String) {
        query.value = value
    }

    companion object {
        fun factory(recipes: RecipeRepository, ingredients: IngredientRepository): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { RecipeListViewModel(recipes, ingredients) }
            }
    }
}
