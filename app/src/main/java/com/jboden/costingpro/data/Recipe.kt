package com.jboden.costingpro.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.jboden.costingpro.domain.ExtraCostPer
import com.jboden.costingpro.domain.MeasureUnit
import com.jboden.costingpro.domain.PricingMode

/** A costed recipe (escandallo). */
@Entity(tableName = "recipe")
data class Recipe(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** How much one batch produces, e.g. 40. */
    val yieldQuantity: Double,
    val yieldUnit: MeasureUnit,
    /** Which figure the user fixed for the selling price. */
    val pricingMode: PricingMode = PricingMode.PRICE,
    /** Net price per unit for PRICE, or the percentage for MARKUP and FOOD_COST. */
    val pricingValue: Double = 0.0,
    val vatPct: Double = DEFAULT_VAT_PCT,
    /** Path of the photo in the app's internal storage. */
    val photoPath: String? = null,
    val notes: String? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
) {
    companion object {
        const val DEFAULT_VAT_PCT = 10.0
    }
}

/**
 * One row of a recipe. Exactly one of [ingredientId] and [subRecipeId] is set;
 * [RecipeRepository] enforces it.
 *
 * Deleting a recipe deletes its lines. An ingredient or recipe that is used
 * by a line cannot be deleted (RESTRICT).
 */
@Entity(
    tableName = "recipe_line",
    foreignKeys = [
        ForeignKey(
            entity = Recipe::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Ingredient::class,
            parentColumns = ["id"],
            childColumns = ["ingredientId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = Recipe::class,
            parentColumns = ["id"],
            childColumns = ["subRecipeId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("recipeId"), Index("ingredientId"), Index("subRecipeId")],
)
data class RecipeLine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipeId: Long = 0,
    /** Order of the row within the recipe. */
    val position: Int = 0,
    val ingredientId: Long? = null,
    val subRecipeId: Long? = null,
    /** Net quantity that goes into the recipe. */
    val quantity: Double,
    /** Same magnitude as the ingredient or as the sub-recipe yield. */
    val unit: MeasureUnit,
    /** Waste of this row, 0 to 99. */
    val wastePct: Double = 0.0,
)

/** A cost that is not an ingredient, e.g. a bag or labour. */
@Entity(
    tableName = "extra_cost",
    foreignKeys = [
        ForeignKey(
            entity = Recipe::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("recipeId")],
)
data class ExtraCost(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipeId: Long = 0,
    val name: String,
    /** Amount without VAT. */
    val amount: Double,
    val per: ExtraCostPer,
)

/** A recipe with its lines (ordered by position when read through [RecipeRepository]) and extras. */
data class RecipeWithDetails(
    @Embedded val recipe: Recipe,
    @Relation(parentColumn = "id", entityColumn = "recipeId")
    val lines: List<RecipeLine>,
    @Relation(parentColumn = "id", entityColumn = "recipeId")
    val extras: List<ExtraCost>,
)
