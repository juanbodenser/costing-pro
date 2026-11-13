package com.jboden.costingpro.ui.recipes

import com.jboden.costingpro.data.Ingredient
import com.jboden.costingpro.domain.ExtraCostPer
import com.jboden.costingpro.domain.MarginStatus
import com.jboden.costingpro.domain.MeasureUnit
import com.jboden.costingpro.domain.PricingMode
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeEditorLogicTest {

    private fun ingredient(id: Long, name: String, price: Double, quantity: Double, unit: MeasureUnit, waste: Double = 0.0) =
        Ingredient(
            id = id, name = name, purchasePrice = price, purchaseQuantity = quantity,
            purchaseUnit = unit, defaultWastePct = waste,
        )

    private val flour = ingredient(1, "Harina", 0.90, 1.0, MeasureUnit.KG)
    private val butter = ingredient(2, "Mantequilla", 8.0, 1.0, MeasureUnit.KG)
    private val sugar = ingredient(3, "Azúcar", 1.20, 1.0, MeasureUnit.KG)
    private val chocolate = ingredient(4, "Chocolate", 10.0, 1.0, MeasureUnit.KG)
    private val eggs = ingredient(5, "Huevos", 3.0, 12.0, MeasureUnit.UD)
    private val catalogue = listOf(flour, butter, sugar, chocolate, eggs).associateBy { it.id }

    private fun line(key: Long, ingredient: Ingredient, quantity: String, unit: MeasureUnit, waste: String = "") =
        LineForm(key, ingredient.id, ingredient.name, quantity, unit, waste)

    /** The cookies of PLAN.md without the bag extra: phase 4 criterion. */
    private val cookies = RecipeForm(
        name = "Galletas de chocolate",
        yieldQuantity = "40",
        yieldUnit = MeasureUnit.UD,
        lines = listOf(
            line(1, flour, "500", MeasureUnit.G),
            line(2, butter, "250", MeasureUnit.G),
            line(3, sugar, "200", MeasureUnit.G),
            line(4, chocolate, "200", MeasureUnit.G),
            line(5, eggs, "2", MeasureUnit.UD),
        ),
    )

    private fun derive(form: RecipeForm) = RecipeEditorLogic.derive(form, catalogue)

    private fun assertMoney(expected: String, actual: BigDecimal?) {
        assertEquals(0, BigDecimal(expected).compareTo(actual!!.setScale(2, java.math.RoundingMode.HALF_UP)))
    }

    @Test
    fun `cookies without extras cost 5,19 per batch and 0,13 per unit`() {
        val result = derive(cookies)

        assertMoney("5.19", result.cost.batchCost)
        assertMoney("0.13", result.cost.unitCost)
        assertEquals(listOf("0.45", "2.00", "0.24", "2.00", "0.50"), result.lines.map { it.cost!!.setScale(2).toPlainString() })
    }

    @Test
    fun `extra costs count in the cost`() {
        val bag = ExtraForm(key = 1, name = "Bolsa", amount = "0,05", per = ExtraCostPer.UNIT)

        val result = derive(cookies.copy(extras = listOf(bag)))

        assertMoney("7.19", result.cost.batchCost)
        assertMoney("0.18", result.cost.unitCost)
    }

    @Test
    fun `typing the price fills markup and food cost`() {
        val result = derive(cookies.copy(priceField = PriceField.NET_PRICE, pricingText = "1"))

        assertEquals("1", result.fields.price)
        // 0,12975 € of cost on a 1,00 € price: food cost 13,0 %, markup 670,7 %.
        assertEquals("13,0", result.fields.foodCost)
        assertEquals("670,7", result.fields.markup)
        assertEquals(MarginStatus.GOOD, result.status)
    }

    @Test
    fun `typing the markup fills price and food cost`() {
        val result = derive(cookies.copy(priceField = PriceField.MARKUP, pricingText = "100"))

        // Double the cost: 0,2595 €, food cost 50 %.
        assertEquals("0,26", result.fields.price)
        assertEquals("50,0", result.fields.foodCost)
        assertEquals("100", result.fields.markup)
        assertEquals(MarginStatus.LOW, result.status)
    }

    @Test
    fun `typing the food cost fills price and markup`() {
        val result = derive(cookies.copy(priceField = PriceField.FOOD_COST, pricingText = "30"))

        assertEquals("0,43", result.fields.price)
        assertEquals("233,3", result.fields.markup)
        assertEquals("30", result.fields.foodCost)
        assertEquals(MarginStatus.GOOD, result.status)
    }

    @Test
    fun `the price accepts a comma`() {
        val result = derive(cookies.copy(priceField = PriceField.NET_PRICE, pricingText = "0,35"))

        assertEquals("0,35", result.fields.price)
        assertEquals("37,1", result.fields.foodCost)
        assertEquals(MarginStatus.LOW, result.status)
    }

    @Test
    fun `without a price the linked fields are blank and there is no margin`() {
        val result = derive(cookies)

        assertEquals(PriceFields("", "", "", ""), result.fields)
        assertNull(result.price)
        assertNull(result.status)
    }

    @Test
    fun `without yield there is no unit cost and no margin`() {
        val result = derive(cookies.copy(yieldQuantity = "", pricingText = "1"))

        assertNull(result.yieldQuantity)
        assertNull(result.cost.unitCost)
        assertNull(result.price?.marginPct)
        assertNull(result.status)
        assertMoney("5.19", result.cost.batchCost)
    }

    @Test
    fun `incomplete rows are left out of the totals`() {
        val form = cookies.copy(
            lines = cookies.lines + listOf(
                LineForm(key = 6),
                LineForm(key = 7, ingredientId = 1, query = "Harina", quantity = ""),
            ),
        )

        val result = derive(form)

        assertMoney("5.19", result.cost.batchCost)
        assertNull(result.lines[5].cost)
        assertNull(result.lines[6].cost)
        // The row with an ingredient still shows its unit price.
        assertMoney("0.90", result.lines[6].unitPrice)
    }

    @Test
    fun `waste makes a row more expensive`() {
        val prawn = ingredient(9, "Langostino", 18.0, 1.0, MeasureUnit.KG, waste = 40.0)
        val form = RecipeForm(
            name = "Langostinos",
            yieldQuantity = "1",
            lines = listOf(line(1, prawn, "200", MeasureUnit.G, waste = "40")),
        )

        val result = RecipeEditorLogic.derive(form, mapOf(9L to prawn))

        assertMoney("6.00", result.lines[0].cost)
    }

    @Test
    fun `validation passes for the cookies`() {
        val validation = RecipeEditorLogic.validate(cookies, catalogue)

        assertTrue(validation.ok)
        assertEquals(5, validation.lines.size)
    }

    @Test
    fun `a title and a row are required`() {
        val validation = RecipeEditorLogic.validate(RecipeForm(lines = listOf(LineForm(key = 1))), catalogue)

        assertEquals(FieldError.REQUIRED, validation.nameError)
        assertTrue(validation.noLines)
        assertFalse(validation.ok)
    }

    @Test
    fun `empty rows are ignored but half-filled ones are errors`() {
        val form = cookies.copy(
            lines = cookies.lines + listOf(
                LineForm(key = 6),
                LineForm(key = 7, ingredientId = null, query = "Hari", quantity = "100"),
                LineForm(key = 8, ingredientId = 1, query = "Harina", quantity = ""),
                LineForm(key = 9, ingredientId = 1, query = "Harina", quantity = "0"),
                LineForm(key = 10, ingredientId = 1, query = "Harina", quantity = "1,5x"),
                LineForm(key = 11, ingredientId = 1, query = "Harina", quantity = "100", waste = "100"),
                LineForm(key = 12, ingredientId = 1, query = "Harina", quantity = "100", unit = MeasureUnit.ML),
            ),
        )

        val validation = RecipeEditorLogic.validate(form, catalogue)

        assertEquals(setOf(7L, 8L, 9L, 10L, 11L, 12L), validation.rowErrors.keys)
        assertEquals(FieldError.NOT_IN_CATALOGUE, validation.rowErrors[7]!!.ingredient)
        assertEquals(FieldError.REQUIRED, validation.rowErrors[8]!!.quantity)
        assertEquals(FieldError.QUANTITY_ZERO, validation.rowErrors[9]!!.quantity)
        assertEquals(FieldError.NUMBER, validation.rowErrors[10]!!.quantity)
        assertEquals(FieldError.WASTE_RANGE, validation.rowErrors[11]!!.waste)
        assertEquals(FieldError.UNIT_MISMATCH, validation.rowErrors[12]!!.unit)
        assertFalse(validation.ok)
        // The empty row is dropped from what would be saved.
        assertFalse(validation.lines.any { it.key == 6L })
    }

    @Test
    fun `units follow the magnitude of the ingredient`() {
        assertEquals(listOf(MeasureUnit.KG, MeasureUnit.G), RecipeEditorLogic.unitsFor(flour))
        assertEquals(listOf(MeasureUnit.UD), RecipeEditorLogic.unitsFor(eggs))
        assertFalse(MeasureUnit.RACION in RecipeEditorLogic.unitsFor(null))
        assertEquals(MeasureUnit.G, RecipeEditorLogic.defaultUnit(flour))
        assertEquals(MeasureUnit.UD, RecipeEditorLogic.defaultUnit(eggs))
        assertEquals(MeasureUnit.ML, RecipeEditorLogic.defaultUnit(ingredient(8, "Aceite", 5.0, 1.0, MeasureUnit.L)))
    }
}
