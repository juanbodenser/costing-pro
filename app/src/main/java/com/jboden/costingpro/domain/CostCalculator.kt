package com.jboden.costingpro.domain

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * All the costing maths of the app. Pure Kotlin, no Android dependencies.
 *
 * Results are never rounded here: use [toMoneyDisplay] and [toPercentDisplay] when showing them.
 */
object CostCalculator {

    /** Precision for divisions; far beyond what two-decimal display needs. */
    private val MC: MathContext = MathContext.DECIMAL128

    private val HUNDRED = BigDecimal(100)

    /** Price per base unit (kg, l or ud) of an ingredient, or null if its purchase quantity is not positive. */
    fun unitPrice(price: IngredientPrice): BigDecimal? {
        val baseQuantity = price.purchaseQuantity.multiply(price.purchaseUnit.toBase)
        if (baseQuantity.signum() <= 0) return null
        return price.purchasePrice.divide(baseQuantity, MC)
    }

    /** Cost of a recipe: every line, raw material, extras, batch and per-unit figures. */
    fun cost(recipe: RecipeInput): RecipeCost {
        val lines = recipe.lines.map(::lineCost)
        val rawMaterial = lines.fold(BigDecimal.ZERO) { sum, line -> sum + (line.cost ?: BigDecimal.ZERO) }

        val hasYield = recipe.yieldQuantity.signum() > 0
        val batchExtras = recipe.extras.total(ExtraCostPer.BATCH)
        val unitExtras = recipe.extras.total(ExtraCostPer.UNIT)
        // Per-unit extras need a yield to be scaled to the batch; without one they cannot be counted.
        val extras = if (hasYield) batchExtras + unitExtras.multiply(recipe.yieldQuantity) else batchExtras
        val batch = rawMaterial + extras

        return RecipeCost(
            lines = lines,
            rawMaterialCost = rawMaterial,
            extrasCost = extras,
            batchCost = batch,
            unitCost = if (hasYield) batch.divide(recipe.yieldQuantity, MC) else null,
            rawMaterialUnitCost = if (hasYield) rawMaterial.divide(recipe.yieldQuantity, MC) else null,
            extrasUnitCost = if (hasYield) extras.divide(recipe.yieldQuantity, MC) else null,
            hasInvalidLines = lines.any { it.issue != null },
        )
    }

    /** Cost of a single line: quantity × unit price ÷ (1 − waste ÷ 100). */
    fun lineCost(line: LineInput): LineCost {
        val sourceUnit: MeasureUnit
        val unitPrice: BigDecimal
        when (val source = line.source) {
            is LineSource.Ingredient -> {
                sourceUnit = source.price.purchaseUnit
                unitPrice = unitPrice(source.price)
                    ?: return invalid(LineIssue.INVALID_PURCHASE_QUANTITY)
            }

            is LineSource.SubRecipe -> {
                sourceUnit = source.recipe.yieldUnit
                unitPrice = subRecipeUnitPrice(source.recipe)
                    ?: return invalid(LineIssue.SUBRECIPE_WITHOUT_YIELD)
            }
        }

        if (line.unit.magnitude != sourceUnit.magnitude) return invalid(LineIssue.UNIT_MISMATCH, unitPrice)
        if (line.quantity.signum() < 0) return invalid(LineIssue.INVALID_QUANTITY, unitPrice)
        if (line.wastePct.signum() < 0 || line.wastePct >= HUNDRED) return invalid(LineIssue.INVALID_WASTE, unitPrice)

        val baseQuantity = line.quantity.multiply(line.unit.toBase)
        val usableFraction = BigDecimal.ONE - line.wastePct.divide(HUNDRED, MC)
        val cost = baseQuantity.multiply(unitPrice).divide(usableFraction, MC)
        return LineCost(unitPrice = unitPrice, cost = cost, issue = null)
    }

    /**
     * Selling price, profit and margin per unit.
     *
     * @param unitCost cost per unit including extras, or null if the recipe has no yield.
     * @param yieldQuantity units per batch, used for the profit per batch.
     */
    fun price(unitCost: BigDecimal?, pricing: Pricing, yieldQuantity: BigDecimal): PriceResult {
        val netPrice: BigDecimal? = when (pricing.mode) {
            PricingMode.PRICE -> pricing.value
            PricingMode.MARKUP -> unitCost?.let { netPriceFromMarkup(it, pricing.value) }
            PricingMode.FOOD_COST -> unitCost?.let { netPriceFromFoodCost(it, pricing.value) }
        }
        val profit = if (netPrice != null && unitCost != null) netPrice - unitCost else null
        // Divisors: null when dividing by them would be a division by zero.
        val priceDivisor = netPrice?.takeIf { it.signum() != 0 }
        val costDivisor = unitCost?.takeIf { it.signum() != 0 }

        return PriceResult(
            netPrice = netPrice,
            priceWithVat = netPrice?.let { priceWithVat(it, pricing.vatPct) },
            profitPerUnit = profit,
            profitPerBatch = profit?.multiply(yieldQuantity),
            marginPct = if (profit != null && priceDivisor != null) percentOf(profit, priceDivisor) else null,
            markupPct = if (profit != null && costDivisor != null) percentOf(profit, costDivisor) else null,
            foodCostPct = if (unitCost != null && priceDivisor != null) percentOf(unitCost, priceDivisor) else null,
        )
    }

    /** Net price = unit cost × (1 + markup ÷ 100). */
    fun netPriceFromMarkup(unitCost: BigDecimal, markupPct: BigDecimal): BigDecimal =
        unitCost.multiply(BigDecimal.ONE + markupPct.divide(HUNDRED, MC))

    /** Net price = unit cost ÷ (food cost ÷ 100), or null if the target is not positive. */
    fun netPriceFromFoodCost(unitCost: BigDecimal, foodCostPct: BigDecimal): BigDecimal? {
        if (foodCostPct.signum() <= 0) return null
        return unitCost.multiply(HUNDRED).divide(foodCostPct, MC)
    }

    /** Price with VAT = net price × (1 + VAT ÷ 100). */
    fun priceWithVat(netPrice: BigDecimal, vatPct: BigDecimal): BigDecimal =
        netPrice.multiply(BigDecimal.ONE + vatPct.divide(HUNDRED, MC))

    /** Net price = price with VAT ÷ (1 + VAT ÷ 100), or null if VAT is −100 % or lower. */
    fun netPriceFromPriceWithVat(priceWithVat: BigDecimal, vatPct: BigDecimal): BigDecimal? {
        val factor = BigDecimal.ONE + vatPct.divide(HUNDRED, MC)
        if (factor.signum() <= 0) return null
        return priceWithVat.divide(factor, MC)
    }

    /** Batch cost of a sub-recipe ÷ its yield, per base unit of the yield. Null without a yield. */
    private fun subRecipeUnitPrice(recipe: RecipeInput): BigDecimal? {
        val baseYield = recipe.yieldQuantity.multiply(recipe.yieldUnit.toBase)
        if (baseYield.signum() <= 0) return null
        return cost(recipe).batchCost.divide(baseYield, MC)
    }

    private fun invalid(issue: LineIssue, unitPrice: BigDecimal? = null) =
        LineCost(unitPrice = unitPrice, cost = null, issue = issue)

    private fun percentOf(part: BigDecimal, whole: BigDecimal): BigDecimal =
        part.multiply(HUNDRED).divide(whole, MC)

    private fun List<ExtraCostInput>.total(per: ExtraCostPer): BigDecimal =
        filter { it.per == per }.fold(BigDecimal.ZERO) { sum, extra -> sum + extra.amount }
}

/** Rounds an amount to 2 decimals for display. */
fun BigDecimal.toMoneyDisplay(): BigDecimal = setScale(2, RoundingMode.HALF_UP)

/** Rounds a percentage to 1 decimal for display. */
fun BigDecimal.toPercentDisplay(): BigDecimal = setScale(1, RoundingMode.HALF_UP)
