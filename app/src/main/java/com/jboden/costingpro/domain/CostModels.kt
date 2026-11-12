package com.jboden.costingpro.domain

import java.math.BigDecimal

// ---------- Inputs ----------

/** Purchase format of a catalogue ingredient, e.g. 22.50 € for 25 kg. Price excludes VAT. */
data class IngredientPrice(
    val purchasePrice: BigDecimal,
    val purchaseQuantity: BigDecimal,
    val purchaseUnit: MeasureUnit,
)

/** What a recipe line is made of: a catalogue ingredient or another recipe. */
sealed interface LineSource {
    data class Ingredient(val price: IngredientPrice) : LineSource
    data class SubRecipe(val recipe: RecipeInput) : LineSource
}

/**
 * One row of a recipe.
 *
 * @param quantity net quantity that goes into the recipe, in [unit].
 * @param wastePct waste percentage, 0 to 99. It makes the line more expensive by dividing by (1 − waste).
 */
data class LineInput(
    val source: LineSource,
    val quantity: BigDecimal,
    val unit: MeasureUnit,
    val wastePct: BigDecimal = BigDecimal.ZERO,
)

enum class ExtraCostPer { BATCH, UNIT }

/** A cost that is not an ingredient (packaging, labour). Amount excludes VAT. */
data class ExtraCostInput(
    val amount: BigDecimal,
    val per: ExtraCostPer,
)

data class RecipeInput(
    val yieldQuantity: BigDecimal,
    val yieldUnit: MeasureUnit,
    val lines: List<LineInput>,
    val extras: List<ExtraCostInput> = emptyList(),
)

/** Which figure the user fixed for the selling price. */
enum class PricingMode { PRICE, MARKUP, FOOD_COST }

/**
 * @param value net price per unit for [PricingMode.PRICE], or the percentage for the other modes.
 * @param vatPct VAT applied on top of the net price.
 */
data class Pricing(
    val mode: PricingMode,
    val value: BigDecimal,
    val vatPct: BigDecimal,
)

// ---------- Results ----------

/** Why a line could not be costed. */
enum class LineIssue {
    /** The line unit is not the same magnitude as the ingredient or the sub-recipe yield. */
    UNIT_MISMATCH,

    /** Negative quantity. */
    INVALID_QUANTITY,

    /** Waste outside 0 (inclusive) to 100 (exclusive). */
    INVALID_WASTE,

    /** The ingredient's purchase quantity is zero or negative. */
    INVALID_PURCHASE_QUANTITY,

    /** The sub-recipe has no yield, so it has no unit cost. */
    SUBRECIPE_WITHOUT_YIELD,
}

/**
 * Cost of one line. Exactly one of [cost] and [issue] is non-null.
 *
 * @param unitPrice price per base unit (kg, l, ud, ración) of the ingredient or sub-recipe, before waste.
 */
data class LineCost(
    val unitPrice: BigDecimal?,
    val cost: BigDecimal?,
    val issue: LineIssue?,
)

/**
 * Cost of a recipe. All figures are unrounded; round only when displaying.
 *
 * Lines with an issue are left out of the totals and flagged by [hasInvalidLines].
 * The per-unit figures are null when the recipe has no yield.
 */
data class RecipeCost(
    val lines: List<LineCost>,
    val rawMaterialCost: BigDecimal,
    val extrasCost: BigDecimal,
    val batchCost: BigDecimal,
    val unitCost: BigDecimal?,
    val rawMaterialUnitCost: BigDecimal?,
    val extrasUnitCost: BigDecimal?,
    val hasInvalidLines: Boolean,
)

/**
 * Selling price and margin per unit. Percentages are on a 0–100 scale.
 *
 * A field is null when it cannot be computed: no unit cost, or a division by zero
 * (zero price, zero cost or a zero food-cost target).
 */
data class PriceResult(
    val netPrice: BigDecimal?,
    val priceWithVat: BigDecimal?,
    val profitPerUnit: BigDecimal?,
    val profitPerBatch: BigDecimal?,
    val marginPct: BigDecimal?,
    val markupPct: BigDecimal?,
    val foodCostPct: BigDecimal?,
)
