package com.jboden.costingpro.domain

/**
 * Which recipe uses which as a sub-recipe. Pure Kotlin.
 *
 * Every function takes the graph as a map from a recipe id to the ids of the recipes
 * it uses directly as sub-recipes.
 */
object RecipeGraph {

    /**
     * True if giving [recipeId] a line that uses [candidateId] would make a recipe contain
     * itself: [candidateId] is the recipe itself, or it already contains it, directly or
     * through other recipes. A recipe that does not exist yet (id 0) cannot be part of a cycle.
     */
    fun wouldCreateCycle(recipeId: Long, candidateId: Long, subRecipesOf: Map<Long, Set<Long>>): Boolean {
        if (recipeId == NO_RECIPE) return false
        return candidateId == recipeId || recipeId in reachableFrom(candidateId, subRecipesOf)
    }

    /**
     * The recipes that cannot be used inside [recipeId]: itself and every recipe that contains
     * it, directly or not. Using any of them would close a cycle.
     */
    fun cannotBeUsedIn(recipeId: Long, subRecipesOf: Map<Long, Set<Long>>): Set<Long> {
        if (recipeId == NO_RECIPE) return emptySet()
        val containers = mutableSetOf(recipeId)
        // Grows until no recipe outside the set uses one inside it.
        var changed = true
        while (changed) {
            changed = false
            for ((id, subs) in subRecipesOf) {
                if (id !in containers && subs.any { it in containers }) {
                    containers += id
                    changed = true
                }
            }
        }
        return containers
    }

    /** Every recipe that [recipeId] contains, directly or through other recipes. */
    private fun reachableFrom(recipeId: Long, subRecipesOf: Map<Long, Set<Long>>): Set<Long> {
        val seen = mutableSetOf<Long>()
        val pending = ArrayDeque(listOf(recipeId))
        while (pending.isNotEmpty()) {
            for (sub in subRecipesOf[pending.removeFirst()].orEmpty()) {
                if (seen.add(sub)) pending.addLast(sub)
            }
        }
        return seen
    }

    /** The id a recipe has before it is saved. */
    const val NO_RECIPE = 0L
}
