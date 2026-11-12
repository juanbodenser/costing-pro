package com.jboden.costingpro.ui.recipes

import com.jboden.costingpro.data.Ingredient
import com.jboden.costingpro.data.RecipeLine
import com.jboden.costingpro.data.RecipeWithDetails
import com.jboden.costingpro.data.toPrice
import com.jboden.costingpro.domain.CostCalculator
import com.jboden.costingpro.domain.ExtraCostInput
import com.jboden.costingpro.domain.LineInput
import com.jboden.costingpro.domain.LineSource
import com.jboden.costingpro.domain.MarginStatus
import com.jboden.costingpro.domain.MeasureUnit
import com.jboden.costingpro.domain.PriceResult
import com.jboden.costingpro.domain.Pricing
import com.jboden.costingpro.domain.PricingMode
import com.jboden.costingpro.domain.RecipeCost
import com.jboden.costingpro.domain.RecipeInput
import java.math.BigDecimal

/**
 * One line of the cost breakdown. The extras of the recipe appear as a single row with
 * no [name], [quantity] or [unit]; the screen gives it its own label.
 */
data class BreakdownRow(
    val name: String?,
    val quantity: Double?,
    val unit: MeasureUnit?,
    val cost: BigDecimal?,
    /** Share of this row in the batch cost, as a percentage. */
    val sharePct: BigDecimal?,
    /** The row uses another recipe instead of a catalogue ingredient. */
    val isSubRecipe: Boolean = false,
)

/** A stored recipe with everything the list and the detail screen show, already costed. */
data class RecipeSummary(
    val id: Long,
    val name: String,
    val photoPath: String?,
    val yieldQuantity: Double,
    val yieldUnit: MeasureUnit,
    val cost: RecipeCost,
    /** Null while the recipe has no price. */
    val price: PriceResult?,
    val status: MarginStatus?,
    /** In recipe order. */
    val breakdown: List<BreakdownRow>,
)

object RecipeSummaryLogic {

    /** A stored line that can be costed, with what the breakdown shows for it. */
    private class CostedLine(val line: RecipeLine, val input: LineInput, val name: String, val isSubRecipe: Boolean)

    /**
     * Costs a stored recipe against the current catalogue prices and the current state of the
     * recipes it uses, so a change in an ingredient reaches every recipe that depends on it.
     *
     * @param recipes all stored recipes, to find the sub-recipes by id.
     */
    fun summarize(
        details: RecipeWithDetails,
        catalogue: Map<Long, Ingredient>,
        recipes: Map<Long, RecipeWithDetails> = emptyMap(),
    ): RecipeSummary {
        val recipe = details.recipe
        val costed = costedLines(details, catalogue, recipes, path = setOf(recipe.id))
        val yieldQuantity = BigDecimal.valueOf(recipe.yieldQuantity).takeIf { it.signum() > 0 }

        val cost = CostCalculator.cost(inputOf(details, catalogue, recipes))

        // A stored 0 means "no price typed", except for a markup of 0 %.
        val hasPrice = recipe.pricingValue != 0.0 || recipe.pricingMode == PricingMode.MARKUP
        val price = if (hasPrice) {
            CostCalculator.price(
                unitCost = cost.unitCost,
                pricing = Pricing(recipe.pricingMode, BigDecimal.valueOf(recipe.pricingValue), BigDecimal.valueOf(recipe.vatPct)),
                yieldQuantity = yieldQuantity ?: BigDecimal.ZERO,
            )
        } else {
            null
        }

        val rows = costed.mapIndexed { index, item ->
            val lineCost = cost.lines[index].cost
            BreakdownRow(
                name = item.name,
                quantity = item.line.quantity,
                unit = item.line.unit,
                cost = lineCost,
                sharePct = lineCost?.let { CostCalculator.sharePct(it, cost.batchCost) },
                isSubRecipe = item.isSubRecipe,
            )
        }
        val extrasRow = BreakdownRow(
            name = null,
            quantity = null,
            unit = null,
            cost = cost.extrasCost,
            sharePct = CostCalculator.sharePct(cost.extrasCost, cost.batchCost),
        ).takeIf { cost.extrasCost.signum() != 0 }

        return RecipeSummary(
            id = recipe.id,
            name = recipe.name,
            photoPath = recipe.photoPath,
            yieldQuantity = recipe.yieldQuantity,
            yieldUnit = recipe.yieldUnit,
            cost = cost,
            price = price,
            status = price?.foodCostPct?.let(MarginStatus::fromFoodCost),
            breakdown = rows + listOfNotNull(extrasRow),
        )
    }

    /**
     * The calculator input of a stored recipe, with its sub-recipes built the same way inside it.
     *
     * @param path ids of the recipes being built, to cut a loop short if the data had one.
     */
    fun inputOf(
        details: RecipeWithDetails,
        catalogue: Map<Long, Ingredient>,
        recipes: Map<Long, RecipeWithDetails>,
        path: Set<Long> = setOf(details.recipe.id),
    ): RecipeInput {
        val recipe = details.recipe
        return RecipeInput(
            yieldQuantity = BigDecimal.valueOf(recipe.yieldQuantity).takeIf { it.signum() > 0 } ?: BigDecimal.ZERO,
            yieldUnit = recipe.yieldUnit,
            lines = costedLines(details, catalogue, recipes, path).map { it.input },
            extras = details.extras.map { ExtraCostInput(BigDecimal.valueOf(it.amount), it.per) },
        )
    }

    /** The lines of a recipe whose ingredient or sub-recipe exists, in recipe order. */
    private fun costedLines(
        details: RecipeWithDetails,
        catalogue: Map<Long, Ingredient>,
        recipes: Map<Long, RecipeWithDetails>,
        path: Set<Long>,
    ): List<CostedLine> = details.lines.mapNotNull { line ->
        fun input(source: LineSource) = LineInput(
            source = source,
            quantity = BigDecimal.valueOf(line.quantity),
            unit = line.unit,
            wastePct = BigDecimal.valueOf(line.wastePct),
        )
        val ingredient = line.ingredientId?.let(catalogue::get)
        val subRecipe = line.subRecipeId?.let(recipes::get)?.takeIf { it.recipe.id !in path }
        when {
            ingredient != null -> CostedLine(line, input(LineSource.Ingredient(ingredient.toPrice())), ingredient.name, false)
            subRecipe != null -> {
                val subInput = inputOf(subRecipe, catalogue, recipes, path + subRecipe.recipe.id)
                CostedLine(line, input(LineSource.SubRecipe(subInput)), subRecipe.recipe.name, true)
            }
            else -> null
        }
    }

    /** The rows from the most expensive to the cheapest; rows without cost go last. */
    fun sortedByCost(rows: List<BreakdownRow>): List<BreakdownRow> =
        rows.sortedWith(compareByDescending<BreakdownRow> { it.cost != null }.thenByDescending { it.cost })
}
