package com.jboden.costingpro.ui.recipes

import com.jboden.costingpro.data.ExtraCost
import com.jboden.costingpro.data.Ingredient
import com.jboden.costingpro.data.RecipeWithDetails
import com.jboden.costingpro.data.toPrice
import com.jboden.costingpro.domain.CostCalculator
import com.jboden.costingpro.domain.ExtraCostInput
import com.jboden.costingpro.domain.LineInput
import com.jboden.costingpro.domain.LineIssue
import com.jboden.costingpro.domain.LineSource
import com.jboden.costingpro.domain.Magnitude
import com.jboden.costingpro.domain.MarginStatus
import com.jboden.costingpro.domain.MeasureUnit
import com.jboden.costingpro.domain.PriceResult
import com.jboden.costingpro.domain.Pricing
import com.jboden.costingpro.domain.PricingMode
import com.jboden.costingpro.domain.RecipeCost
import com.jboden.costingpro.domain.RecipeGraph
import com.jboden.costingpro.domain.RecipeInput
import com.jboden.costingpro.ui.common.Formats
import java.math.BigDecimal

// Pure Kotlin on purpose: everything the recipe editor computes or validates is here, tested on the JVM.
// The maths itself stays in CostCalculator; this file only turns form text into its inputs and back.

/**
 * One row of the editor, with every number as the user typed it.
 * A row uses an ingredient ([ingredientId]) or another recipe ([subRecipeId]), never both.
 */
data class LineForm(
    /** Identifies the row on screen; not stored. */
    val key: Long,
    val ingredientId: Long? = null,
    /** Text of the ingredient field. */
    val query: String = "",
    val quantity: String = "",
    val unit: MeasureUnit = MeasureUnit.G,
    val waste: String = "",
    val subRecipeId: Long? = null,
) {
    /** Nothing typed or chosen: a row the user added but never used. */
    val isEmpty: Boolean get() = ingredientId == null && subRecipeId == null && query.isBlank() && quantity.isBlank()

    /** An ingredient or a recipe has been chosen. */
    val hasSource: Boolean get() = ingredientId != null || subRecipeId != null
}

data class RecipeForm(
    val name: String = "",
    val yieldQuantity: String = "",
    val yieldUnit: MeasureUnit = MeasureUnit.UD,
    val lines: List<LineForm> = emptyList(),
    /** The price field the user typed in last; the other two are computed from it. */
    val pricingMode: PricingMode = PricingMode.PRICE,
    /** Text of the field named by [pricingMode]. */
    val pricingText: String = "",
)

/** Cost of one row. [cost] is null until the row is complete, or when it has an [issue]. */
data class LineResult(
    val unitPrice: BigDecimal?,
    val baseUnit: MeasureUnit?,
    val cost: BigDecimal?,
    val issue: LineIssue?,
)

/** Text of the three linked price fields. */
data class PriceFields(val price: String, val markup: String, val foodCost: String)

data class EditorResult(
    /** Same order as the form rows. */
    val lines: List<LineResult>,
    val cost: RecipeCost,
    /** Null when the yield is blank, invalid or zero. */
    val yieldQuantity: BigDecimal?,
    /** Null while no price has been typed. */
    val price: PriceResult?,
    val fields: PriceFields,
    val status: MarginStatus?,
)

enum class FieldError { REQUIRED, NUMBER, QUANTITY_ZERO, WASTE_RANGE, NOT_IN_CATALOGUE, UNIT_MISMATCH, CYCLE }

data class RowErrors(
    val ingredient: FieldError? = null,
    val quantity: FieldError? = null,
    val waste: FieldError? = null,
    val unit: FieldError? = null,
) {
    val any: Boolean get() = ingredient != null || quantity != null || waste != null || unit != null
}

data class Validation(
    val nameError: FieldError?,
    /** No usable row at all. */
    val noLines: Boolean,
    /** Errors by row key; only rows with some error. */
    val rowErrors: Map<Long, RowErrors>,
    /** The rows that would be saved: the form rows without the empty ones. */
    val lines: List<LineForm>,
) {
    val ok: Boolean get() = nameError == null && !noLines && rowErrors.isEmpty()
}

/** What a row is made of, once found in the catalogue or among the recipes. */
private sealed interface Source {
    /** Unit the source is priced in; the row unit must be of the same magnitude. */
    val unit: MeasureUnit

    class OfIngredient(val ingredient: Ingredient) : Source {
        override val unit get() = ingredient.purchaseUnit
    }

    class OfRecipe(val recipe: RecipeWithDetails) : Source {
        override val unit get() = recipe.recipe.yieldUnit
    }
}

object RecipeEditorLogic {

    /** Highest waste a row accepts (PLAN.md: 0 to 99). */
    private val MAX_WASTE_PCT = BigDecimal(99)

    /** Unit proposed when an ingredient or recipe priced in [unit] is picked: g, ml, ud or ración. */
    fun defaultUnit(unit: MeasureUnit): MeasureUnit = when (unit.magnitude) {
        Magnitude.MASS -> MeasureUnit.G
        Magnitude.VOLUME -> MeasureUnit.ML
        Magnitude.COUNT -> MeasureUnit.UD
        Magnitude.PORTION -> MeasureUnit.RACION
    }

    fun defaultUnit(ingredient: Ingredient): MeasureUnit = defaultUnit(ingredient.purchaseUnit)

    /** Units a row can use for a source priced in [sourceUnit]: those of its magnitude. */
    fun unitsFor(sourceUnit: MeasureUnit): List<MeasureUnit> =
        MeasureUnit.entries.filter { it.magnitude == sourceUnit.magnitude }

    /** Units a row can use for [ingredient], or all but the portion if no source has been chosen yet. */
    fun unitsFor(ingredient: Ingredient?): List<MeasureUnit> =
        if (ingredient == null) MeasureUnit.entries.filter { it != MeasureUnit.RACION } else unitsFor(ingredient.purchaseUnit)

    /**
     * Units a row can use, given what it is made of: the ingredient or recipe it has, or all
     * but the portion while it has none.
     */
    fun unitsFor(line: LineForm, catalogue: Map<Long, Ingredient>, recipes: Map<Long, RecipeWithDetails>): List<MeasureUnit> =
        source(line, catalogue, recipes)?.let { unitsFor(it.unit) } ?: unitsFor(null as Ingredient?)

    /** The sub-recipe uses stored: recipe id to the ids of the recipes it uses. */
    fun subRecipeGraph(recipes: Map<Long, RecipeWithDetails>): Map<Long, Set<Long>> =
        recipes.mapValues { (_, details) -> details.lines.mapNotNull { it.subRecipeId }.toSet() }

    /**
     * Costs, price fields and margin for the current form.
     *
     * @param extras the recipe's stored extra costs; the editor does not show them yet but they count.
     * @param recipes all stored recipes, for rows that use a recipe.
     * @param selfId the recipe being edited (0 if new): it cannot be used as a sub-recipe of itself.
     */
    fun derive(
        form: RecipeForm,
        catalogue: Map<Long, Ingredient>,
        extras: List<ExtraCost>,
        vatPct: Double,
        recipes: Map<Long, RecipeWithDetails> = emptyMap(),
        selfId: Long = RecipeGraph.NO_RECIPE,
    ): EditorResult {
        val inputs = mutableListOf<LineInput>()
        // For each row, its position among the inputs, or null if the row is not complete yet.
        val inputIndex = form.lines.map { line ->
            val lineSource = source(line, catalogue, recipes, selfId)?.let { lineSource(it, catalogue, recipes, selfId) }
            lineInput(line, lineSource)?.let { input ->
                inputs += input
                inputs.lastIndex
            }
        }

        val yieldQuantity = Formats.parseDecimal(form.yieldQuantity)?.takeIf { it.signum() > 0 }
        val cost = CostCalculator.cost(
            RecipeInput(
                yieldQuantity = yieldQuantity ?: BigDecimal.ZERO,
                yieldUnit = form.yieldUnit,
                lines = inputs,
                extras = extras.map { ExtraCostInput(BigDecimal.valueOf(it.amount), it.per) },
            ),
        )

        val lines = form.lines.mapIndexed { row, line ->
            val found = source(line, catalogue, recipes, selfId)
            val index = inputIndex[row]
            if (index != null) {
                val lineCost = cost.lines[index]
                LineResult(lineCost.unitPrice, found?.unit?.baseUnit, lineCost.cost, lineCost.issue)
            } else {
                LineResult(
                    unitPrice = found?.let { unitPrice(lineSource(it, catalogue, recipes, selfId)) },
                    baseUnit = found?.unit?.baseUnit,
                    cost = null,
                    issue = null,
                )
            }
        }

        val typedValue = Formats.parseDecimal(form.pricingText)
        val price = typedValue?.let {
            CostCalculator.price(
                unitCost = cost.unitCost,
                pricing = Pricing(form.pricingMode, it, BigDecimal.valueOf(vatPct)),
                yieldQuantity = yieldQuantity ?: BigDecimal.ZERO,
            )
        }

        // The field being typed in keeps its text exactly; the other two show the computed value.
        val fields = PriceFields(
            price = if (form.pricingMode == PricingMode.PRICE) form.pricingText else money(price?.netPrice),
            markup = if (form.pricingMode == PricingMode.MARKUP) form.pricingText else percent(price?.markupPct),
            foodCost = if (form.pricingMode == PricingMode.FOOD_COST) form.pricingText else percent(price?.foodCostPct),
        )

        return EditorResult(
            lines = lines,
            cost = cost,
            yieldQuantity = yieldQuantity,
            price = price,
            fields = fields,
            status = price?.foodCostPct?.let(MarginStatus::fromFoodCost),
        )
    }

    /** What stops the form from being saved: a title and at least one valid row are required. */
    fun validate(
        form: RecipeForm,
        catalogue: Map<Long, Ingredient>,
        recipes: Map<Long, RecipeWithDetails> = emptyMap(),
        selfId: Long = RecipeGraph.NO_RECIPE,
    ): Validation {
        val kept = form.lines.filterNot { it.isEmpty }
        val graph = subRecipeGraph(recipes)
        val rowErrors = buildMap {
            kept.forEach { line ->
                val errors = rowErrors(line, source(line, catalogue, recipes), selfId, graph)
                if (errors.any) put(line.key, errors)
            }
        }
        return Validation(
            nameError = if (form.name.isBlank()) FieldError.REQUIRED else null,
            noLines = kept.isEmpty(),
            rowErrors = rowErrors,
            lines = kept,
        )
    }

    private fun rowErrors(line: LineForm, source: Source?, selfId: Long, graph: Map<Long, Set<Long>>): RowErrors {
        val quantity = Formats.parseDecimal(line.quantity)
        val waste = if (line.waste.isBlank()) BigDecimal.ZERO else Formats.parseDecimal(line.waste)
        return RowErrors(
            ingredient = when {
                source == null -> FieldError.NOT_IN_CATALOGUE
                source is Source.OfRecipe && RecipeGraph.wouldCreateCycle(selfId, source.recipe.recipe.id, graph) ->
                    FieldError.CYCLE

                else -> null
            },
            quantity = when {
                line.quantity.isBlank() -> FieldError.REQUIRED
                quantity == null -> FieldError.NUMBER
                quantity.signum() == 0 -> FieldError.QUANTITY_ZERO
                else -> null
            },
            waste = when {
                waste == null -> FieldError.NUMBER
                waste > MAX_WASTE_PCT -> FieldError.WASTE_RANGE
                else -> null
            },
            unit = if (source != null && line.unit.magnitude != source.unit.magnitude) FieldError.UNIT_MISMATCH else null,
        )
    }

    /** The ingredient or recipe a row refers to, if it still exists. A row is never its own recipe. */
    private fun source(
        line: LineForm,
        catalogue: Map<Long, Ingredient>,
        recipes: Map<Long, RecipeWithDetails>,
        selfId: Long = RecipeGraph.NO_RECIPE,
    ): Source? {
        line.ingredientId?.let(catalogue::get)?.let { return Source.OfIngredient(it) }
        line.subRecipeId?.takeIf { it != selfId }?.let(recipes::get)?.let { return Source.OfRecipe(it) }
        return null
    }

    /** The calculator source of a found [Source]; a recipe is built with its own sub-recipes inside. */
    private fun lineSource(
        source: Source,
        catalogue: Map<Long, Ingredient>,
        recipes: Map<Long, RecipeWithDetails>,
        selfId: Long,
    ): LineSource = when (source) {
        is Source.OfIngredient -> LineSource.Ingredient(source.ingredient.toPrice())
        is Source.OfRecipe -> LineSource.SubRecipe(
            RecipeSummaryLogic.inputOf(source.recipe, catalogue, recipes, path = setOf(selfId, source.recipe.recipe.id)),
        )
    }

    /** Price per base unit of a source, before waste. */
    private fun unitPrice(source: LineSource): BigDecimal? = when (source) {
        is LineSource.Ingredient -> CostCalculator.unitPrice(source.price)
        // One base unit of the recipe's own unit costs exactly its unit price.
        is LineSource.SubRecipe ->
            CostCalculator.lineCost(LineInput(source, BigDecimal.ONE, source.recipe.yieldUnit.baseUnit)).unitPrice
    }

    /** The calculator input of a row, or null while the row has no source or a number is missing. */
    private fun lineInput(line: LineForm, source: LineSource?): LineInput? {
        if (source == null) return null
        val quantity = Formats.parseDecimal(line.quantity) ?: return null
        val waste = if (line.waste.isBlank()) BigDecimal.ZERO else Formats.parseDecimal(line.waste) ?: return null
        return LineInput(source, quantity, line.unit, waste)
    }

    private fun money(value: BigDecimal?) = value?.let { Formats.plain(it, 2) }.orEmpty()

    private fun percent(value: BigDecimal?) = value?.let { Formats.plain(it, 1) }.orEmpty()
}
