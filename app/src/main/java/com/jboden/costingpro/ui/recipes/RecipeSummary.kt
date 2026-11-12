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

    /**
     * Costs a stored recipe against the current catalogue prices.
     * Sub-recipe rows are not counted yet: they arrive in phase 6.
     */
    fun summarize(details: RecipeWithDetails, catalogue: Map<Long, Ingredient>): RecipeSummary {
        val recipe = details.recipe
        val costed: List<Pair<RecipeLine, Ingredient>> = details.lines.mapNotNull { line ->
            line.ingredientId?.let(catalogue::get)?.let { line to it }
        }
        val yieldQuantity = BigDecimal.valueOf(recipe.yieldQuantity).takeIf { it.signum() > 0 }

        val cost = CostCalculator.cost(
            RecipeInput(
                yieldQuantity = yieldQuantity ?: BigDecimal.ZERO,
                yieldUnit = recipe.yieldUnit,
                lines = costed.map { (line, ingredient) ->
                    LineInput(
                        source = LineSource.Ingredient(ingredient.toPrice()),
                        quantity = BigDecimal.valueOf(line.quantity),
                        unit = line.unit,
                        wastePct = BigDecimal.valueOf(line.wastePct),
                    )
                },
                extras = details.extras.map { ExtraCostInput(BigDecimal.valueOf(it.amount), it.per) },
            ),
        )

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

        val rows = costed.mapIndexed { index, (line, ingredient) ->
            val lineCost = cost.lines[index].cost
            BreakdownRow(
                name = ingredient.name,
                quantity = line.quantity,
                unit = line.unit,
                cost = lineCost,
                sharePct = lineCost?.let { CostCalculator.sharePct(it, cost.batchCost) },
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

    /** The rows from the most expensive to the cheapest; rows without cost go last. */
    fun sortedByCost(rows: List<BreakdownRow>): List<BreakdownRow> =
        rows.sortedWith(compareByDescending<BreakdownRow> { it.cost != null }.thenByDescending { it.cost })
}
