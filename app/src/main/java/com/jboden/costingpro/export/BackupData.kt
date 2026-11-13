package com.jboden.costingpro.export

import com.jboden.costingpro.data.ExtraCost
import com.jboden.costingpro.data.Ingredient
import com.jboden.costingpro.data.Recipe
import com.jboden.costingpro.data.RecipeLine
import com.jboden.costingpro.domain.ExtraCostPer
import com.jboden.costingpro.domain.MeasureUnit
import com.jboden.costingpro.domain.PricingMode
import kotlinx.serialization.Serializable

/**
 * Everything a backup holds, as it is written to `backup.json`. The rows keep their ids,
 * so the lines still point to the right ingredients and recipes after a restore.
 *
 * These classes are the file format: change them only in a compatible way, or raise
 * [CURRENT_FORMAT].
 */
@Serializable
data class BackupData(
    val format: Int = CURRENT_FORMAT,
    /** Epoch millis of the export. */
    val exportedAt: Long,
    val defaultVatPct: Double,
    val ingredients: List<IngredientRow>,
    val recipes: List<RecipeRow>,
    val lines: List<LineRow>,
    val extras: List<ExtraRow>,
) {
    companion object {
        const val CURRENT_FORMAT = 1
    }
}

@Serializable
data class IngredientRow(
    val id: Long,
    val name: String,
    val purchasePrice: Double,
    val purchaseQuantity: Double,
    val purchaseUnit: MeasureUnit,
    val defaultWastePct: Double,
    val allergens: Int,
    val updatedAt: Long,
)

@Serializable
data class RecipeRow(
    val id: Long,
    val name: String,
    val yieldQuantity: Double,
    val yieldUnit: MeasureUnit,
    val pricingMode: PricingMode,
    val pricingValue: Double,
    val vatPct: Double,
    /** Relative path of the photo, the same inside the .zip as under the files directory. */
    val photoPath: String? = null,
    val notes: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class LineRow(
    val id: Long,
    val recipeId: Long,
    val position: Int,
    val ingredientId: Long? = null,
    val subRecipeId: Long? = null,
    val quantity: Double,
    val unit: MeasureUnit,
    val wastePct: Double,
)

@Serializable
data class ExtraRow(
    val id: Long,
    val recipeId: Long,
    val name: String,
    val amount: Double,
    val per: ExtraCostPer,
)

fun Ingredient.toRow() = IngredientRow(id, name, purchasePrice, purchaseQuantity, purchaseUnit, defaultWastePct, allergens, updatedAt)

fun IngredientRow.toEntity() = Ingredient(id, name, purchasePrice, purchaseQuantity, purchaseUnit, defaultWastePct, allergens, updatedAt)

fun Recipe.toRow() = RecipeRow(
    id, name, yieldQuantity, yieldUnit, pricingMode, pricingValue, vatPct, photoPath, notes, createdAt, updatedAt,
)

fun RecipeRow.toEntity() = Recipe(
    id, name, yieldQuantity, yieldUnit, pricingMode, pricingValue, vatPct, photoPath, notes, createdAt, updatedAt,
)

fun RecipeLine.toRow() = LineRow(id, recipeId, position, ingredientId, subRecipeId, quantity, unit, wastePct)

fun LineRow.toEntity() = RecipeLine(id, recipeId, position, ingredientId, subRecipeId, quantity, unit, wastePct)

fun ExtraCost.toRow() = ExtraRow(id, recipeId, name, amount, per)

fun ExtraRow.toEntity() = ExtraCost(id, recipeId, name, amount, per)
