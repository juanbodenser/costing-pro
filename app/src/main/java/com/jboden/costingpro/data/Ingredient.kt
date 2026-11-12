package com.jboden.costingpro.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.jboden.costingpro.domain.MeasureUnit

/** A catalogue ingredient, stored once with its purchase format. */
@Entity(
    tableName = "ingredient",
    indices = [Index(value = ["name"], unique = true)],
)
data class Ingredient(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Unique regardless of case (the NOCASE collation also applies to the unique index). */
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    /** Price of the purchase format, without VAT. */
    val purchasePrice: Double,
    /** Quantity of the purchase format, e.g. 25 for a 25 kg sack. */
    val purchaseQuantity: Double,
    /** Any unit except RACION. */
    val purchaseUnit: MeasureUnit,
    /** Usual waste, proposed when the ingredient is added to a recipe. */
    val defaultWastePct: Double = 0.0,
    /** Bit mask of [com.jboden.costingpro.domain.Allergen]. */
    val allergens: Int = 0,
    /** Epoch millis of the last price change. */
    val updatedAt: Long = 0,
)
