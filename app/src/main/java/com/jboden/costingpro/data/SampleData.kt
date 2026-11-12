package com.jboden.costingpro.data

import androidx.room.withTransaction
import com.jboden.costingpro.domain.Allergen
import com.jboden.costingpro.domain.ExtraCostPer
import com.jboden.costingpro.domain.MeasureUnit
import com.jboden.costingpro.domain.PricingMode

/**
 * Example content for the first launch: the chocolate cookies of PLAN.md and their
 * ingredients, plus the prawn of the waste example.
 */
object SampleData {

    const val COOKIES_NAME = "Galletas de chocolate"

    suspend fun insertInto(database: CostingDatabase, now: Long = System.currentTimeMillis()) {
        database.withTransaction {
            val ingredientDao = database.ingredientDao()

            fun ingredient(
                name: String,
                price: Double,
                quantity: Double,
                unit: MeasureUnit,
                wastePct: Double = 0.0,
                allergens: Int = 0,
            ) = Ingredient(
                name = name,
                purchasePrice = price,
                purchaseQuantity = quantity,
                purchaseUnit = unit,
                defaultWastePct = wastePct,
                allergens = allergens,
                updatedAt = now,
            )

            val flour = ingredientDao.insert(
                ingredient("Harina", 22.50, 25.0, MeasureUnit.KG, allergens = Allergen.maskOf(Allergen.GLUTEN)),
            )
            val butter = ingredientDao.insert(
                ingredient("Mantequilla", 8.00, 1.0, MeasureUnit.KG, allergens = Allergen.maskOf(Allergen.MILK)),
            )
            val sugar = ingredientDao.insert(
                ingredient("Azúcar", 1.20, 1.0, MeasureUnit.KG),
            )
            val chocolate = ingredientDao.insert(
                ingredient(
                    "Chocolate", 10.00, 1.0, MeasureUnit.KG,
                    allergens = Allergen.maskOf(Allergen.MILK, Allergen.SOY),
                ),
            )
            val eggs = ingredientDao.insert(
                ingredient("Huevos", 3.00, 12.0, MeasureUnit.UD, allergens = Allergen.maskOf(Allergen.EGGS)),
            )
            ingredientDao.insert(
                ingredient(
                    "Langostino", 18.00, 1.0, MeasureUnit.KG,
                    wastePct = 40.0,
                    allergens = Allergen.maskOf(Allergen.CRUSTACEANS),
                ),
            )

            fun line(position: Int, ingredientId: Long, quantity: Double, unit: MeasureUnit) =
                RecipeLine(position = position, ingredientId = ingredientId, quantity = quantity, unit = unit)

            database.recipeDao().save(
                recipe = Recipe(
                    name = COOKIES_NAME,
                    yieldQuantity = 40.0,
                    yieldUnit = MeasureUnit.UD,
                    pricingMode = PricingMode.PRICE,
                    pricingValue = 1.00,
                    vatPct = Recipe.DEFAULT_VAT_PCT,
                    createdAt = now,
                    updatedAt = now,
                ),
                lines = listOf(
                    line(0, flour, 500.0, MeasureUnit.G),
                    line(1, butter, 250.0, MeasureUnit.G),
                    line(2, sugar, 200.0, MeasureUnit.G),
                    line(3, chocolate, 200.0, MeasureUnit.G),
                    line(4, eggs, 2.0, MeasureUnit.UD),
                ),
                extras = listOf(
                    ExtraCost(name = "Bolsa", amount = 0.05, per = ExtraCostPer.UNIT),
                ),
            )
        }
    }
}
