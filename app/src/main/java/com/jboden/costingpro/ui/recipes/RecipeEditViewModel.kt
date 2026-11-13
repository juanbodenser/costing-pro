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
import com.jboden.costingpro.data.RecipeWithDetails
import com.jboden.costingpro.data.SettingsStore
import com.jboden.costingpro.domain.ExtraCostPer
import com.jboden.costingpro.domain.MeasureUnit
import com.jboden.costingpro.domain.PricingMode
import com.jboden.costingpro.domain.RecipeGraph
import com.jboden.costingpro.ui.common.Formats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** An ingredient or a recipe the autocomplete can offer. Ids are only unique within their kind. */
data class CatalogueItem(val id: Long, val name: String, val isRecipe: Boolean = false)

/** What the screen needs to draw one row besides its form values. */
data class RowInfo(
    /** Units the row can be measured in, given what it is made of. */
    val units: List<MeasureUnit>,
    /** The row uses another recipe. */
    val isRecipe: Boolean,
)

data class RecipeEditUiState(
    val isNew: Boolean,
    val loading: Boolean,
    val form: RecipeForm,
    val result: EditorResult,
    /** Ingredients and the recipes that can be used here, by name. */
    val catalogue: List<CatalogueItem>,
    /** Same order as the form rows. */
    val rows: List<RowInfo>,
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
    private val settings: SettingsStore,
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
            Draft(
                RecipeForm(lines = listOf(LineForm(key = 1)), vatPct = Formats.number(settings.defaultVatPct.value)),
                loading = false,
            )
        } else {
            Draft(RecipeForm(), loading = true)
        },
    )

    /** Null until the catalogue has been read. */
    private val catalogue = MutableStateFlow<List<Ingredient>?>(null)

    /** All stored recipes, to offer them as sub-recipes. Null until they have been read. */
    private val allRecipes = MutableStateFlow<List<RecipeWithDetails>?>(null)

    private var nextKey = 2L

    /** The stored recipe being edited. Keeps what the editor does not show: VAT, photo, notes and dates. */
    private var original: Recipe? = null

    val uiState: StateFlow<RecipeEditUiState> = combine(draft, catalogue, allRecipes) { draft, catalogue, stored ->
        val all = catalogue.orEmpty()
        val byId = all.associateBy { it.id }
        val recipesById = stored.orEmpty().associateBy { it.recipe.id }
        val form = draft.form
        RecipeEditUiState(
            isNew = recipeId == NEW_ID,
            loading = draft.loading || catalogue == null || stored == null,
            form = form,
            result = RecipeEditorLogic.derive(form, byId, recipesById, recipeId),
            catalogue = items(all, stored.orEmpty()),
            rows = form.lines.map { line ->
                RowInfo(RecipeEditorLogic.unitsFor(line, byId, recipesById), isRecipe = line.subRecipeId != null)
            },
            errors = if (draft.attempted) RecipeEditorLogic.validate(form, byId, recipesById, recipeId) else null,
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
            result = RecipeEditorLogic.derive(RecipeForm(), emptyMap()),
            catalogue = emptyList(),
            rows = emptyList(),
            errors = null,
            saving = false,
            finished = false,
        ),
    )

    init {
        viewModelScope.launch { ingredients.observeAll().collect { catalogue.value = it } }
        viewModelScope.launch { recipes.observeAll().collect { allRecipes.value = it } }
        if (recipeId != NEW_ID) {
            viewModelScope.launch {
                val details = recipes.get(recipeId)
                if (details == null) {
                    draft.update { it.copy(loading = false, finished = true) }
                    return@launch
                }
                original = details.recipe
                val lines = details.lines.mapNotNull { line ->
                    val ingredient = line.ingredientId?.let { ingredients.get(it) }
                    val subRecipe = line.subRecipeId?.let { recipes.get(it) }
                    val query = ingredient?.name ?: subRecipe?.recipe?.name ?: return@mapNotNull null
                    LineForm(
                        key = nextKey++,
                        ingredientId = ingredient?.id,
                        subRecipeId = subRecipe?.recipe?.id,
                        query = query,
                        quantity = Formats.number(line.quantity),
                        unit = line.unit,
                        waste = wasteText(line.wastePct),
                    )
                }
                val extras = details.extras.map { extra ->
                    ExtraForm(
                        key = nextKey++,
                        name = extra.name,
                        amount = Formats.number(extra.amount, minDecimals = 2),
                        per = extra.per,
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
                            extras = extras,
                            vatPct = Formats.number(recipe.vatPct),
                            priceField = PriceField.of(recipe.pricingMode),
                            pricingText = pricingText,
                        ),
                    )
                }
            }
        }
    }

    private fun wasteText(pct: Double) = if (pct == 0.0) "" else Formats.number(pct)

    /**
     * What the autocomplete offers: every ingredient, and every recipe except this one and the
     * ones that contain it (using them here would make a recipe contain itself).
     */
    private fun items(ingredients: List<Ingredient>, stored: List<RecipeWithDetails>): List<CatalogueItem> {
        val cannotUse = RecipeGraph.cannotBeUsedIn(recipeId, RecipeEditorLogic.subRecipeGraph(stored.associateBy { it.recipe.id }))
        return (
            ingredients.map { CatalogueItem(it.id, it.name) } +
                stored.filter { it.recipe.id !in cannotUse }.map { CatalogueItem(it.recipe.id, it.recipe.name, isRecipe = true) }
            ).sortedBy { it.name.lowercase() }
    }

    private fun currentItems(): List<CatalogueItem> = items(catalogue.value.orEmpty(), allRecipes.value.orEmpty())

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
     * The user typed in the ingredient field. A name that matches exactly one ingredient or
     * recipe selects it; anything else leaves the row without a source until one is picked.
     */
    fun onQueryChange(key: Long, text: String) = updateLine(key) { line ->
        val matches = currentItems().filter { it.name.equals(text.trim(), ignoreCase = true) }
        val match = matches.singleOrNull()
        when {
            match == null -> line.copy(query = text, ingredientId = null, subRecipeId = null)
            line.isSelected(match) -> line.copy(query = text)
            else -> select(line, match, text)
        }
    }

    /** The user picked an ingredient or recipe from the suggestions. */
    fun onItemPicked(key: Long, item: CatalogueItem) {
        updateLine(key) { select(it, item, item.name) }
    }

    fun onLineQuantityChange(key: Long, value: String) = updateLine(key) { it.copy(quantity = value) }

    fun onLineUnitChange(key: Long, value: MeasureUnit) = updateLine(key) { it.copy(unit = value) }

    fun onLineWasteChange(key: Long, value: String) = updateLine(key) { it.copy(waste = value) }

    // ---------- Extra costs ----------

    fun addExtra() = updateForm { it.copy(extras = it.extras + ExtraForm(key = nextKey++)) }

    fun removeExtra(key: Long) = updateForm { form -> form.copy(extras = form.extras.filterNot { it.key == key }) }

    private fun updateExtra(key: Long, change: (ExtraForm) -> ExtraForm) = updateForm { form ->
        form.copy(extras = form.extras.map { if (it.key == key) change(it) else it })
    }

    fun onExtraNameChange(key: Long, value: String) = updateExtra(key) { it.copy(name = value) }

    fun onExtraAmountChange(key: Long, value: String) = updateExtra(key) { it.copy(amount = value) }

    fun onExtraPerChange(key: Long, value: ExtraCostPer) = updateExtra(key) { it.copy(per = value) }

    // ---------- Price and VAT ----------

    fun onVatChange(value: String) = updateForm { it.copy(vatPct = value) }

    fun onPriceChange(value: String) = updateForm { it.copy(priceField = PriceField.NET_PRICE, pricingText = value) }

    fun onMarkupChange(value: String) = updateForm { it.copy(priceField = PriceField.MARKUP, pricingText = value) }

    fun onFoodCostChange(value: String) =
        updateForm { it.copy(priceField = PriceField.FOOD_COST, pricingText = value) }

    fun onPriceWithVatChange(value: String) =
        updateForm { it.copy(priceField = PriceField.PRICE_WITH_VAT, pricingText = value) }

    private fun LineForm.isSelected(item: CatalogueItem) =
        if (item.isRecipe) subRecipeId == item.id else ingredientId == item.id

    /**
     * Makes [item] the source of the row. Proposes the usual waste of an ingredient and a unit of the
     * right kind, keeping the current unit if it still fits.
     */
    private fun select(line: LineForm, item: CatalogueItem, query: String): LineForm {
        val sourceUnit: MeasureUnit
        val waste: String
        if (item.isRecipe) {
            val recipe = allRecipes.value.orEmpty().firstOrNull { it.recipe.id == item.id } ?: return line
            sourceUnit = recipe.recipe.yieldUnit
            waste = ""
        } else {
            val ingredient = catalogue.value.orEmpty().firstOrNull { it.id == item.id } ?: return line
            sourceUnit = ingredient.purchaseUnit
            waste = wasteText(ingredient.defaultWastePct)
        }
        return line.copy(
            ingredientId = item.id.takeIf { !item.isRecipe },
            subRecipeId = item.id.takeIf { item.isRecipe },
            query = query,
            unit = line.unit.takeIf { it in RecipeEditorLogic.unitsFor(sourceUnit) } ?: RecipeEditorLogic.defaultUnit(sourceUnit),
            waste = waste,
        )
    }

    fun save() {
        val current = draft.value
        if (current.loading || current.saving || current.finished) return

        val byId = catalogue.value.orEmpty().associateBy { it.id }
        val recipesById = allRecipes.value.orEmpty().associateBy { it.recipe.id }
        val validation = RecipeEditorLogic.validate(current.form, byId, recipesById, recipeId)
        if (!validation.ok) {
            draft.update { it.copy(attempted = true) }
            return
        }

        draft.update { it.copy(attempted = true, saving = true) }
        val form = current.form
        val yieldQuantity = Formats.parseDecimal(form.yieldQuantity)?.toDouble() ?: 0.0
        // A price field that was cleared counts as no price at all.
        val pricing = RecipeEditorLogic.storedPricing(form)
        val recipe = (original ?: Recipe(name = "", yieldQuantity = 0.0, yieldUnit = form.yieldUnit)).copy(
            name = form.name.trim(),
            yieldQuantity = yieldQuantity,
            yieldUnit = form.yieldUnit,
            pricingMode = pricing?.mode ?: PricingMode.PRICE,
            pricingValue = pricing?.value ?: 0.0,
            vatPct = Formats.parseDecimal(form.vatPct)!!.toDouble(),
        )
        val extras = validation.extras.map { extra ->
            ExtraCost(
                name = extra.name.trim(),
                amount = Formats.parseDecimal(extra.amount)!!.toDouble(),
                per = extra.per,
            )
        }
        val lines = validation.lines.map { line ->
            RecipeLine(
                ingredientId = line.ingredientId,
                subRecipeId = line.subRecipeId,
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
            settings: SettingsStore,
            recipeId: Long,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { RecipeEditViewModel(recipes, ingredients, settings, recipeId) }
        }
    }
}
