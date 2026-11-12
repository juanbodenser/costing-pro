package com.jboden.costingpro.ui.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jboden.costingpro.data.ExtraCost
import com.jboden.costingpro.data.Ingredient
import com.jboden.costingpro.data.IngredientRepository
import com.jboden.costingpro.data.Recipe
import com.jboden.costingpro.data.RecipeLine
import com.jboden.costingpro.data.RecipeRepository
import com.jboden.costingpro.domain.MeasureUnit
import com.jboden.costingpro.domain.PricingMode
import com.jboden.costingpro.ui.common.Formats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** An ingredient the autocomplete can offer. */
data class CatalogueItem(val id: Long, val name: String)

data class RecipeEditUiState(
    val isNew: Boolean,
    val loading: Boolean,
    val form: RecipeForm,
    val result: EditorResult,
    /** All catalogue ingredients, by name. */
    val catalogue: List<CatalogueItem>,
    /** Ingredient of each row, to offer its units; same order as the form rows. */
    val rowIngredients: List<Ingredient?>,
    /** Errors to show. Null until the user has tried to save. */
    val errors: Validation?,
    val saving: Boolean,
    /** Saved: the screen must close. */
    val finished: Boolean,
)

/** @param recipeId the recipe to edit, or [NEW_ID] to create one. */
class RecipeEditViewModel(
    private val recipes: RecipeRepository,
    private val ingredients: IngredientRepository,
    private val recipeId: Long,
) : ViewModel() {

    private data class Draft(
        val form: RecipeForm,
        val loading: Boolean,
        val attempted: Boolean = false,
        val saving: Boolean = false,
        val finished: Boolean = false,
    )

    private val draft = MutableStateFlow(
        if (recipeId == NEW_ID) {
            Draft(RecipeForm(lines = listOf(LineForm(key = 1))), loading = false)
        } else {
            Draft(RecipeForm(), loading = true)
        },
    )

    /** Null until the catalogue has been read. */
    private val catalogue = MutableStateFlow<List<Ingredient>?>(null)

    private var nextKey = 2L

    /** The stored recipe being edited. Keeps what the editor does not show: VAT, photo, notes and dates. */
    private var original: Recipe? = null

    /** The stored extra costs. The editor does not show them yet; they are counted and saved back unchanged. */
    private var extras: List<ExtraCost> = emptyList()

    val uiState: StateFlow<RecipeEditUiState> = combine(draft, catalogue) { draft, catalogue ->
        val all = catalogue.orEmpty()
        val byId = all.associateBy { it.id }
        val form = draft.form
        RecipeEditUiState(
            isNew = recipeId == NEW_ID,
            loading = draft.loading || catalogue == null,
            form = form,
            result = RecipeEditorLogic.derive(form, byId, extras, vatPct()),
            catalogue = all.map { CatalogueItem(it.id, it.name) },
            rowIngredients = form.lines.map { line -> line.ingredientId?.let(byId::get) },
            errors = if (draft.attempted) RecipeEditorLogic.validate(form, byId) else null,
            saving = draft.saving,
            finished = draft.finished,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        RecipeEditUiState(
            isNew = recipeId == NEW_ID,
            loading = true,
            form = RecipeForm(),
            result = RecipeEditorLogic.derive(RecipeForm(), emptyMap(), emptyList(), Recipe.DEFAULT_VAT_PCT),
            catalogue = emptyList(),
            rowIngredients = emptyList(),
            errors = null,
            saving = false,
            finished = false,
        ),
    )

    init {
        viewModelScope.launch { ingredients.observeAll().collect { catalogue.value = it } }
        if (recipeId != NEW_ID) {
            viewModelScope.launch {
                val details = recipes.get(recipeId)
                if (details == null) {
                    draft.update { it.copy(loading = false, finished = true) }
                    return@launch
                }
                original = details.recipe
                extras = details.extras
                // Sub-recipe rows cannot be made yet (phase 6), so there are none to keep here.
                val lines = details.lines.mapNotNull { line ->
                    val ingredient = line.ingredientId?.let { ingredients.get(it) } ?: return@mapNotNull null
                    LineForm(
                        key = nextKey++,
                        ingredientId = ingredient.id,
                        query = ingredient.name,
                        quantity = Formats.number(line.quantity),
                        unit = line.unit,
                        waste = wasteText(line.wastePct),
                    )
                }
                val recipe = details.recipe
                // A stored value of 0 means "no price typed", except for a markup of 0 %.
                val pricingText = if (recipe.pricingValue == 0.0 && recipe.pricingMode != PricingMode.MARKUP) {
                    ""
                } else {
                    Formats.number(recipe.pricingValue)
                }
                draft.update {
                    it.copy(
                        loading = false,
                        form = RecipeForm(
                            name = recipe.name,
                            yieldQuantity = if (recipe.yieldQuantity == 0.0) "" else Formats.number(recipe.yieldQuantity),
                            yieldUnit = recipe.yieldUnit,
                            lines = lines,
                            pricingMode = recipe.pricingMode,
                            pricingText = pricingText,
                        ),
                    )
                }
            }
        }
    }

    private fun vatPct() = original?.vatPct ?: Recipe.DEFAULT_VAT_PCT

    private fun wasteText(pct: Double) = if (pct == 0.0) "" else Formats.number(pct)

    private fun updateForm(change: (RecipeForm) -> RecipeForm) = draft.update { it.copy(form = change(it.form)) }

    private fun updateLine(key: Long, change: (LineForm) -> LineForm) = updateForm { form ->
        form.copy(lines = form.lines.map { if (it.key == key) change(it) else it })
    }

    fun onNameChange(value: String) = updateForm { it.copy(name = value) }

    fun onYieldQuantityChange(value: String) = updateForm { it.copy(yieldQuantity = value) }

    fun onYieldUnitChange(value: MeasureUnit) = updateForm { it.copy(yieldUnit = value) }

    fun addLine() = updateForm { it.copy(lines = it.lines + LineForm(key = nextKey++)) }

    fun removeLine(key: Long) = updateForm { form -> form.copy(lines = form.lines.filterNot { it.key == key }) }

    /**
     * The user typed in the ingredient field. A name that matches a catalogue ingredient
     * selects it; anything else leaves the row without ingredient until one is picked.
     */
    fun onQueryChange(key: Long, text: String) = updateLine(key) { line ->
        val match = catalogue.value.orEmpty().firstOrNull { it.name.equals(text.trim(), ignoreCase = true) }
        when {
            match == null -> line.copy(query = text, ingredientId = null)
            match.id == line.ingredientId -> line.copy(query = text)
            else -> select(line, match, text)
        }
    }

    /** The user picked an ingredient from the suggestions. */
    fun onIngredientPicked(key: Long, ingredientId: Long) {
        val ingredient = catalogue.value.orEmpty().firstOrNull { it.id == ingredientId } ?: return
        updateLine(key) { select(it, ingredient, ingredient.name) }
    }

    fun onLineQuantityChange(key: Long, value: String) = updateLine(key) { it.copy(quantity = value) }

    fun onLineUnitChange(key: Long, value: MeasureUnit) = updateLine(key) { it.copy(unit = value) }

    fun onLineWasteChange(key: Long, value: String) = updateLine(key) { it.copy(waste = value) }

    fun onPriceChange(value: String) = updateForm { it.copy(pricingMode = PricingMode.PRICE, pricingText = value) }

    fun onMarkupChange(value: String) = updateForm { it.copy(pricingMode = PricingMode.MARKUP, pricingText = value) }

    fun onFoodCostChange(value: String) =
        updateForm { it.copy(pricingMode = PricingMode.FOOD_COST, pricingText = value) }

    /** Proposes the ingredient's usual unit and waste, keeping the current unit if it still fits. */
    private fun select(line: LineForm, ingredient: Ingredient, query: String): LineForm = line.copy(
        ingredientId = ingredient.id,
        query = query,
        unit = line.unit.takeIf { it in RecipeEditorLogic.unitsFor(ingredient) } ?: RecipeEditorLogic.defaultUnit(ingredient),
        waste = wasteText(ingredient.defaultWastePct),
    )

    fun save() {
        val current = draft.value
        if (current.loading || current.saving || current.finished) return

        val byId = catalogue.value.orEmpty().associateBy { it.id }
        val validation = RecipeEditorLogic.validate(current.form, byId)
        if (!validation.ok) {
            draft.update { it.copy(attempted = true) }
            return
        }

        draft.update { it.copy(attempted = true, saving = true) }
        val form = current.form
        val yieldQuantity = Formats.parseDecimal(form.yieldQuantity)?.toDouble() ?: 0.0
        // A price field that was cleared counts as no price at all.
        val priceValue = Formats.parseDecimal(form.pricingText)?.toDouble()
        val recipe = (original ?: Recipe(name = "", yieldQuantity = 0.0, yieldUnit = form.yieldUnit)).copy(
            name = form.name.trim(),
            yieldQuantity = yieldQuantity,
            yieldUnit = form.yieldUnit,
            pricingMode = if (priceValue == null) PricingMode.PRICE else form.pricingMode,
            pricingValue = priceValue ?: 0.0,
        )
        val lines = validation.lines.map { line ->
            RecipeLine(
                ingredientId = line.ingredientId,
                quantity = Formats.parseDecimal(line.quantity)!!.toDouble(),
                unit = line.unit,
                wastePct = if (line.waste.isBlank()) 0.0 else Formats.parseDecimal(line.waste)!!.toDouble(),
            )
        }
        viewModelScope.launch {
            recipes.save(recipe, lines, extras)
            draft.update { it.copy(finished = true) }
        }
    }

    companion object {
        const val NEW_ID = 0L

        fun factory(
            recipes: RecipeRepository,
            ingredients: IngredientRepository,
            recipeId: Long,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { RecipeEditViewModel(recipes, ingredients, recipeId) }
        }
    }
}
