package com.jboden.costingpro.ui.recipes

import com.jboden.costingpro.data.Ingredient
import com.jboden.costingpro.data.Recipe
import com.jboden.costingpro.data.RecipeLine
import com.jboden.costingpro.data.RecipeWithDetails
import com.jboden.costingpro.domain.MeasureUnit
import java.math.BigDecimal
import java.math.RoundingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The recipe editor with rows that use other recipes (phase 6). */
class SubRecipeEditorLogicTest {

    private val tomato = Ingredient(
        id = 6, name = "Tomate", purchasePrice = 2.0, purchaseQuantity = 1.0, purchaseUnit = MeasureUnit.KG,
    )
    private val catalogue = mapOf(6L to tomato)

    /** 3 kg of tomato make 2 l of sauce: 3,00 € a litre. */
    private val sauce = RecipeWithDetails(
        recipe = Recipe(id = 10, name = "Salsa de tomate", yieldQuantity = 2.0, yieldUnit = MeasureUnit.L),
        lines = listOf(RecipeLine(ingredientId = 6, quantity = 3.0, unit = MeasureUnit.KG)),
        extras = emptyList(),
    )

    /** A dish that already uses the sauce. */
    private val dish = RecipeWithDetails(
        recipe = Recipe(id = 11, name = "Pasta con tomate", yieldQuantity = 4.0, yieldUnit = MeasureUnit.RACION),
        lines = listOf(RecipeLine(subRecipeId = 10, quantity = 500.0, unit = MeasureUnit.ML)),
        extras = emptyList(),
    )

    private val recipes = listOf(sauce, dish).associateBy { it.recipe.id }

    private fun subLine(key: Long, recipeId: Long, quantity: String, unit: MeasureUnit) =
        LineForm(key = key, subRecipeId = recipeId, query = "x", quantity = quantity, unit = unit)

    private fun assertMoney(expected: String, actual: BigDecimal?) {
        assertEquals(expected, actual!!.setScale(2, RoundingMode.HALF_UP).toPlainString())
    }

    @Test
    fun `a row that uses a recipe is costed with the unit cost of that recipe`() {
        val form = RecipeForm(
            name = "Menú",
            yieldQuantity = "1",
            lines = listOf(subLine(1, 10, "500", MeasureUnit.ML)),
        )

        val result = RecipeEditorLogic.derive(form, catalogue, emptyList(), 10.0, recipes)

        // 500 ml of a sauce that costs 3,00 € a litre.
        assertMoney("1.50", result.lines[0].cost)
        assertMoney("3.00", result.lines[0].unitPrice)
        assertEquals(MeasureUnit.L, result.lines[0].baseUnit)
        assertMoney("1.50", result.cost.batchCost)
    }

    @Test
    fun `a recipe row without quantity still shows the unit price of the recipe`() {
        val form = RecipeForm(lines = listOf(subLine(1, 10, "", MeasureUnit.ML)))

        val result = RecipeEditorLogic.derive(form, catalogue, emptyList(), 10.0, recipes)

        assertNull(result.lines[0].cost)
        assertMoney("3.00", result.lines[0].unitPrice)
    }

    @Test
    fun `units of a recipe row follow the yield unit of the recipe`() {
        val onSauce = subLine(1, 10, "1", MeasureUnit.ML)
        val onDish = subLine(2, 11, "1", MeasureUnit.RACION)

        assertEquals(listOf(MeasureUnit.L, MeasureUnit.ML), RecipeEditorLogic.unitsFor(onSauce, catalogue, recipes))
        assertEquals(listOf(MeasureUnit.RACION), RecipeEditorLogic.unitsFor(onDish, catalogue, recipes))
        assertEquals(MeasureUnit.RACION, RecipeEditorLogic.defaultUnit(MeasureUnit.RACION))
        assertEquals(MeasureUnit.ML, RecipeEditorLogic.defaultUnit(MeasureUnit.L))
    }

    @Test
    fun `a recipe row in a unit of another kind is an error`() {
        val form = RecipeForm(name = "Menú", lines = listOf(subLine(1, 10, "1", MeasureUnit.KG)))

        val validation = RecipeEditorLogic.validate(form, catalogue, recipes)

        assertEquals(FieldError.UNIT_MISMATCH, validation.rowErrors[1]!!.unit)
        assertFalse(validation.ok)
    }

    @Test
    fun `a recipe that contains the one being edited cannot be used in it`() {
        // Editing the sauce (10): the dish (11) already contains it.
        val form = RecipeForm(name = "Salsa", lines = listOf(subLine(1, 11, "1", MeasureUnit.RACION)))

        val validation = RecipeEditorLogic.validate(form, catalogue, recipes, selfId = 10)

        assertEquals(FieldError.CYCLE, validation.rowErrors[1]!!.ingredient)
        assertFalse(validation.ok)
    }

    @Test
    fun `a recipe cannot be used in itself`() {
        val form = RecipeForm(name = "Salsa", yieldQuantity = "1", lines = listOf(subLine(1, 10, "1", MeasureUnit.L)))

        assertFalse(RecipeEditorLogic.validate(form, catalogue, recipes, selfId = 10).ok)
        val result = RecipeEditorLogic.derive(form, catalogue, emptyList(), 10.0, recipes, selfId = 10)
        assertNull(result.lines[0].cost)
    }

    @Test
    fun `a recipe can use a recipe that does not contain it`() {
        // A new recipe may use the dish; the dish may use the sauce it already uses.
        val usesDish = RecipeForm(name = "Menú", lines = listOf(subLine(1, 11, "2", MeasureUnit.RACION)))
        val dishUsesSauce = RecipeForm(name = "Pasta", lines = listOf(subLine(1, 10, "1", MeasureUnit.L)))

        assertTrue(RecipeEditorLogic.validate(usesDish, catalogue, recipes).ok)
        assertTrue(RecipeEditorLogic.validate(dishUsesSauce, catalogue, recipes, selfId = 11).ok)
    }

    @Test
    fun `a recipe row whose recipe is gone is an error`() {
        val form = RecipeForm(name = "Menú", lines = listOf(subLine(1, 99, "1", MeasureUnit.L)))

        val validation = RecipeEditorLogic.validate(form, catalogue, recipes)

        assertEquals(FieldError.NOT_IN_CATALOGUE, validation.rowErrors[1]!!.ingredient)
    }

    @Test
    fun `the graph lists the sub-recipes each recipe uses`() {
        assertEquals(mapOf(10L to emptySet<Long>(), 11L to setOf(10L)), RecipeEditorLogic.subRecipeGraph(recipes))
    }
}
