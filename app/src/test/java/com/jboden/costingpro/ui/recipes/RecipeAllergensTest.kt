package com.jboden.costingpro.ui.recipes

import com.jboden.costingpro.data.Ingredient
import com.jboden.costingpro.data.Recipe
import com.jboden.costingpro.data.RecipeLine
import com.jboden.costingpro.data.RecipeWithDetails
import com.jboden.costingpro.domain.Allergen
import com.jboden.costingpro.domain.MeasureUnit
import org.junit.Assert.assertEquals
import org.junit.Test

/** Allergens of a recipe, inherited from its ingredients and sub-recipes (phase 8). */
class RecipeAllergensTest {

    private fun ingredient(id: Long, name: String, vararg allergens: Allergen) = Ingredient(
        id = id, name = name, purchasePrice = 1.0, purchaseQuantity = 1.0, purchaseUnit = MeasureUnit.KG,
        allergens = Allergen.maskOf(*allergens),
    )

    private val flour = ingredient(1, "Harina", Allergen.GLUTEN)
    private val egg = ingredient(2, "Huevo", Allergen.EGGS)
    private val sugar = ingredient(3, "Azúcar")
    private val celery = ingredient(4, "Apio", Allergen.CELERY)
    private val butter = ingredient(5, "Mantequilla", Allergen.MILK)
    private val catalogue = listOf(flour, egg, sugar, celery, butter).associateBy { it.id }

    private fun recipe(id: Long, vararg lines: RecipeLine) = RecipeWithDetails(
        recipe = Recipe(id = id, name = "R$id", yieldQuantity = 1.0, yieldUnit = MeasureUnit.UD),
        lines = lines.toList(),
        extras = emptyList(),
    )

    private fun uses(ingredientId: Long) = RecipeLine(ingredientId = ingredientId, quantity = 1.0, unit = MeasureUnit.KG)

    private fun usesRecipe(recipeId: Long) = RecipeLine(subRecipeId = recipeId, quantity = 1.0, unit = MeasureUnit.UD)

    /** The criterion of phase 8: a recipe with flour and egg shows gluten and eggs. */
    @Test
    fun `a recipe with flour and egg has gluten and eggs`() {
        val dough = recipe(10, uses(1), uses(2), uses(3))

        val summary = RecipeSummaryLogic.summarize(dough, catalogue)

        assertEquals(listOf(Allergen.GLUTEN, Allergen.EGGS), summary.allergens.toList())
    }

    @Test
    fun `a recipe inherits the allergens of its sub-recipes at any depth`() {
        val stock = recipe(20, uses(4))
        val sauce = recipe(21, uses(5), usesRecipe(20))
        val dish = recipe(22, uses(1), usesRecipe(21))
        val all = listOf(stock, sauce, dish).associateBy { it.recipe.id }

        val allergens = RecipeSummaryLogic.allergensOf(dish, catalogue, all)

        // In declaration order: gluten, milk, celery.
        assertEquals(listOf(Allergen.GLUTEN, Allergen.MILK, Allergen.CELERY), allergens.toList())
    }

    @Test
    fun `an allergen present twice is listed once`() {
        val bread = recipe(30, uses(1))
        val sandwich = recipe(31, uses(1), usesRecipe(30))

        val allergens = RecipeSummaryLogic.allergensOf(sandwich, catalogue, mapOf(30L to bread, 31L to sandwich))

        assertEquals(setOf(Allergen.GLUTEN), allergens)
    }

    @Test
    fun `a recipe without allergens has none`() {
        assertEquals(emptySet<Allergen>(), RecipeSummaryLogic.allergensOf(recipe(40, uses(3)), catalogue, emptyMap()))
    }

    @Test
    fun `a line that cannot be costed still brings its allergens`() {
        // Flour in units: the unit does not match, so it is not costed, but the gluten is there.
        val odd = recipe(50, RecipeLine(ingredientId = 1, quantity = 1.0, unit = MeasureUnit.UD))

        val summary = RecipeSummaryLogic.summarize(odd, catalogue)

        assertEquals(setOf(Allergen.GLUTEN), summary.allergens)
    }

    @Test
    fun `stored data with a loop does not hang`() {
        val a = recipe(60, uses(1), usesRecipe(61))
        val b = recipe(61, uses(2), usesRecipe(60))

        val allergens = RecipeSummaryLogic.allergensOf(a, catalogue, mapOf(60L to a, 61L to b))

        assertEquals(listOf(Allergen.GLUTEN, Allergen.EGGS), allergens.toList())
    }
}
