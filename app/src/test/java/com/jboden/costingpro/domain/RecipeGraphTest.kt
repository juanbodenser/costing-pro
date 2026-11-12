package com.jboden.costingpro.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeGraphTest {

    // Dish (1) uses sauce (2); sauce uses stock (3); dessert (4) uses nothing.
    private val graph = mapOf(
        1L to setOf(2L),
        2L to setOf(3L),
        3L to emptySet(),
        4L to emptySet(),
    )

    @Test
    fun `a recipe cannot contain itself`() {
        assertTrue(RecipeGraph.wouldCreateCycle(2, 2, graph))
    }

    @Test
    fun `a recipe cannot contain one that uses it directly`() {
        // Stock (3) using the sauce (2), which already uses the stock.
        assertTrue(RecipeGraph.wouldCreateCycle(3, 2, graph))
    }

    @Test
    fun `a recipe cannot contain one that uses it indirectly`() {
        // Stock (3) using the dish (1): dish -> sauce -> stock -> dish.
        assertTrue(RecipeGraph.wouldCreateCycle(3, 1, graph))
    }

    @Test
    fun `unrelated and downward uses are fine`() {
        assertFalse(RecipeGraph.wouldCreateCycle(1, 4, graph))
        assertFalse(RecipeGraph.wouldCreateCycle(1, 3, graph))
        assertFalse(RecipeGraph.wouldCreateCycle(4, 1, graph))
        assertFalse(RecipeGraph.wouldCreateCycle(2, 4, graph))
    }

    @Test
    fun `two recipes using the same sub-recipe are not a cycle`() {
        val diamond = mapOf(1L to setOf(2L, 3L), 2L to setOf(4L), 3L to setOf(4L), 4L to emptySet())

        assertFalse(RecipeGraph.wouldCreateCycle(1, 4, diamond))
        assertTrue(RecipeGraph.wouldCreateCycle(4, 1, diamond))
    }

    @Test
    fun `a recipe that is not saved yet cannot make a cycle`() {
        assertFalse(RecipeGraph.wouldCreateCycle(RecipeGraph.NO_RECIPE, 1, graph))
    }

    @Test
    fun `the recipes that cannot be used inside one are itself and its containers`() {
        assertEquals(setOf(3L, 2L, 1L), RecipeGraph.cannotBeUsedIn(3, graph))
        assertEquals(setOf(2L, 1L), RecipeGraph.cannotBeUsedIn(2, graph))
        assertEquals(setOf(1L), RecipeGraph.cannotBeUsedIn(1, graph))
        assertEquals(setOf(4L), RecipeGraph.cannotBeUsedIn(4, graph))
        assertEquals(emptySet<Long>(), RecipeGraph.cannotBeUsedIn(RecipeGraph.NO_RECIPE, graph))
    }
}
