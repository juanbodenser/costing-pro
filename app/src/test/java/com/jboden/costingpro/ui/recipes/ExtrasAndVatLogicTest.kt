package com.jboden.costingpro.ui.recipes

import com.jboden.costingpro.data.Ingredient
import com.jboden.costingpro.domain.ExtraCostPer
import com.jboden.costingpro.domain.MarginStatus
import com.jboden.costingpro.domain.MeasureUnit
import com.jboden.costingpro.domain.PricingMode
import java.math.BigDecimal
import java.math.RoundingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Extra costs, VAT and the editable price with VAT in the recipe editor (phase 7). */
class ExtrasAndVatLogicTest {

    private fun ingredient(id: Long, name: String, price: Double, quantity: Double, unit: MeasureUnit) =
        Ingredient(id = id, name = name, purchasePrice = price, purchaseQuantity = quantity, purchaseUnit = unit)

    private val catalogue = listOf(
        ingredient(1, "Harina", 0.90, 1.0, MeasureUnit.KG),
        ingredient(2, "Mantequilla", 8.0, 1.0, MeasureUnit.KG),
        ingredient(3, "Azúcar", 1.20, 1.0, MeasureUnit.KG),
        ingredient(4, "Chocolate", 10.0, 1.0, MeasureUnit.KG),
        ingredient(5, "Huevos", 3.0, 12.0, MeasureUnit.UD),
    ).associateBy { it.id }

    private fun line(key: Long, id: Long, quantity: String, unit: MeasureUnit) =
        LineForm(key = key, ingredientId = id, query = catalogue.getValue(id).name, quantity = quantity, unit = unit)

    private val bag = ExtraForm(key = 100, name = "Bolsa", amount = "0,05", per = ExtraCostPer.UNIT)

    /** The complete cookies of PLAN.md: the bag extra, a price of 1,00 € and 10 % VAT. */
    private val cookies = RecipeForm(
        name = "Galletas de chocolate",
        yieldQuantity = "40",
        yieldUnit = MeasureUnit.UD,
        lines = listOf(
            line(1, 1, "500", MeasureUnit.G),
            line(2, 2, "250", MeasureUnit.G),
            line(3, 3, "200", MeasureUnit.G),
            line(4, 4, "200", MeasureUnit.G),
            line(5, 5, "2", MeasureUnit.UD),
        ),
        extras = listOf(bag),
        vatPct = "10",
        priceField = PriceField.NET_PRICE,
        pricingText = "1",
    )

    private fun derive(form: RecipeForm) = RecipeEditorLogic.derive(form, catalogue)

    private fun BigDecimal?.money() = this!!.setScale(2, RoundingMode.HALF_UP).toPlainString()

    /** The criterion of phase 7: the complete cookies give 0,18 € per unit and 1,10 € of price with VAT. */
    @Test
    fun `the complete cookies give 0,18 per unit and 1,10 with VAT`() {
        val result = derive(cookies)

        assertEquals("7.19", result.cost.batchCost.money())
        assertEquals("0.18", result.cost.unitCost.money())
        assertEquals("1,10", result.fields.priceWithVat)
        assertEquals("1.10", result.price!!.priceWithVat.money())
        assertEquals("0.82", result.price!!.profitPerUnit.money())
        assertEquals("82.0", result.price!!.marginPct!!.setScale(1, RoundingMode.HALF_UP).toPlainString())
        assertEquals(MarginStatus.GOOD, result.status)
    }

    // ---------- Extra costs ----------

    @Test
    fun `a per-batch extra is added once and a per-unit extra once per unit`() {
        val labour = ExtraForm(key = 101, name = "Mano de obra", amount = "3", per = ExtraCostPer.BATCH)

        val result = derive(cookies.copy(extras = listOf(bag, labour)))

        // 5,19 € of ingredients, 2,00 € of bags and 3,00 € of labour.
        assertEquals("2.00", (result.cost.extrasCost - BigDecimal("3")).money())
        assertEquals("5.00", result.cost.extrasCost.money())
        assertEquals("10.19", result.cost.batchCost.money())
    }

    @Test
    fun `an extra with a comma amount or an invalid amount`() {
        val comma = ExtraForm(key = 1, name = "Etiqueta", amount = "0,10", per = ExtraCostPer.UNIT)
        val broken = ExtraForm(key = 2, name = "Roto", amount = "abc", per = ExtraCostPer.BATCH)
        val blank = ExtraForm(key = 3, name = "Sin importe", amount = "", per = ExtraCostPer.BATCH)

        val result = derive(cookies.copy(extras = listOf(comma, broken, blank)))

        // Only the extra with a valid amount counts: 40 × 0,10 €.
        assertEquals("4.00", result.cost.extrasCost.money())
    }

    @Test
    fun `without yield a per-unit extra cannot be counted`() {
        val result = derive(cookies.copy(yieldQuantity = ""))

        assertEquals("0.00", result.cost.extrasCost.money())
        assertNull(result.cost.unitCost)
    }

    @Test
    fun `empty extra rows are dropped and half-filled ones are errors`() {
        val empty = ExtraForm(key = 1)
        val noName = ExtraForm(key = 2, amount = "1")
        val noAmount = ExtraForm(key = 3, name = "Caja")
        val badAmount = ExtraForm(key = 4, name = "Caja", amount = "1,2x")

        val validation = RecipeEditorLogic.validate(cookies.copy(extras = listOf(bag, empty, noName, noAmount, badAmount)), catalogue)

        assertEquals(setOf(2L, 3L, 4L), validation.extraErrors.keys)
        assertEquals(FieldError.REQUIRED, validation.extraErrors[2]!!.name)
        assertEquals(FieldError.REQUIRED, validation.extraErrors[3]!!.amount)
        assertEquals(FieldError.NUMBER, validation.extraErrors[4]!!.amount)
        assertEquals(listOf(100L, 2L, 3L, 4L), validation.extras.map { it.key })
        assertTrue(!validation.ok)
    }

    @Test
    fun `valid extras pass validation`() {
        val validation = RecipeEditorLogic.validate(cookies.copy(extras = listOf(bag, ExtraForm(key = 5))), catalogue)

        assertTrue(validation.ok)
        assertEquals(listOf(100L), validation.extras.map { it.key })
    }

    // ---------- VAT and the price with VAT ----------

    @Test
    fun `the VAT of the recipe sets the price with VAT`() {
        assertEquals("1,21", derive(cookies.copy(vatPct = "21")).fields.priceWithVat)
        assertEquals("1,04", derive(cookies.copy(vatPct = "4")).fields.priceWithVat)
        assertEquals("1,00", derive(cookies.copy(vatPct = "0")).fields.priceWithVat)
        // 1,075 rounds half up to 1,08.
        assertEquals("1,08", derive(cookies.copy(vatPct = "7,5")).fields.priceWithVat)
    }

    @Test
    fun `typing the price with VAT sets the net price`() {
        val result = derive(cookies.copy(priceField = PriceField.PRICE_WITH_VAT, pricingText = "2,20"))

        assertEquals("2,20", result.fields.priceWithVat)
        assertEquals("2,00", result.fields.price)
        // Net 2,00 over a unit cost of 0,17975: markup 1012,7 %, food cost 9,0 %.
        assertEquals("9,0", result.fields.foodCost)
        assertEquals("1012,7", result.fields.markup)
    }

    @Test
    fun `a price with VAT that is not exact is stored as the net price it implies`() {
        val form = cookies.copy(priceField = PriceField.PRICE_WITH_VAT, pricingText = "2,50")

        val result = derive(form)
        val stored = RecipeEditorLogic.storedPricing(form)!!

        assertEquals("2,27", result.fields.price)
        assertEquals(PricingMode.PRICE, stored.mode)
        assertEquals(2.5 / 1.1, stored.value, 1e-9)
    }

    @Test
    fun `changing the VAT while the price with VAT is fixed moves the net price`() {
        val form = cookies.copy(priceField = PriceField.PRICE_WITH_VAT, pricingText = "1,21", vatPct = "21")

        assertEquals("1,00", derive(form).fields.price)
    }

    @Test
    fun `an invalid VAT leaves the price with VAT blank`() {
        assertEquals("", derive(cookies.copy(vatPct = "abc")).fields.priceWithVat)
        assertEquals("", derive(cookies.copy(vatPct = "")).fields.priceWithVat)
    }

    @Test
    fun `the VAT must be a number from 0 to 100`() {
        fun error(vat: String) = RecipeEditorLogic.validate(cookies.copy(vatPct = vat), catalogue).vatError

        assertNull(error("10"))
        assertNull(error("0"))
        assertNull(error("100"))
        assertNull(error("7,5"))
        assertEquals(FieldError.REQUIRED, error(""))
        assertEquals(FieldError.NUMBER, error("abc"))
        assertEquals(FieldError.NUMBER, error("-4"))
        assertEquals(FieldError.VAT_RANGE, error("100,5"))
    }

    // ---------- What is stored ----------

    @Test
    fun `each price field is stored as its own figure`() {
        fun stored(field: PriceField, text: String) = RecipeEditorLogic.storedPricing(cookies.copy(priceField = field, pricingText = text))

        assertEquals(StoredPricing(PricingMode.PRICE, 1.5), stored(PriceField.NET_PRICE, "1,5"))
        assertEquals(StoredPricing(PricingMode.MARKUP, 200.0), stored(PriceField.MARKUP, "200"))
        assertEquals(StoredPricing(PricingMode.FOOD_COST, 30.0), stored(PriceField.FOOD_COST, "30"))
        assertEquals(StoredPricing(PricingMode.PRICE, 2.0), stored(PriceField.PRICE_WITH_VAT, "2,2"))
    }

    @Test
    fun `no typed price means nothing is stored`() {
        assertNull(RecipeEditorLogic.storedPricing(cookies.copy(pricingText = "")))
        assertNull(RecipeEditorLogic.storedPricing(cookies.copy(pricingText = "abc")))
        assertNull(RecipeEditorLogic.storedPricing(cookies.copy(priceField = PriceField.PRICE_WITH_VAT, pricingText = "2", vatPct = "x")))
    }

    @Test
    fun `a stored mode maps back to its price field`() {
        assertEquals(PriceField.NET_PRICE, PriceField.of(PricingMode.PRICE))
        assertEquals(PriceField.MARKUP, PriceField.of(PricingMode.MARKUP))
        assertEquals(PriceField.FOOD_COST, PriceField.of(PricingMode.FOOD_COST))
    }
}
