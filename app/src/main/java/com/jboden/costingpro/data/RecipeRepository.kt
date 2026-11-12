package com.jboden.costingpro.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RecipeRepository(
    private val dao: RecipeDao,
    private val now: () -> Long = System::currentTimeMillis,
) {

    fun observeAll(): Flow<List<RecipeWithDetails>> =
        dao.observeAllWithDetails().map { recipes -> recipes.map { it.sorted() } }

    fun observe(id: Long): Flow<RecipeWithDetails?> = dao.observeWithDetails(id).map { it?.sorted() }

    fun observeCount(): Flow<Int> = dao.observeCount()

    suspend fun getAll(): List<RecipeWithDetails> = dao.getAllWithDetails().map { it.sorted() }

    suspend fun get(id: Long): RecipeWithDetails? = dao.getWithDetails(id)?.sorted()

    /**
     * Inserts the recipe (id 0) or updates it, replacing all its lines and extras,
     * and returns its id. Lines are stored in the order given.
     *
     * @throws IllegalArgumentException if a line does not have exactly one of
     * ingredient and sub-recipe, or uses the recipe itself as sub-recipe.
     */
    suspend fun save(recipe: Recipe, lines: List<RecipeLine>, extras: List<ExtraCost>): Long {
        lines.forEach { line ->
            require((line.ingredientId == null) != (line.subRecipeId == null)) {
                "A recipe line needs exactly one of ingredientId and subRecipeId"
            }
            require(recipe.id == 0L || line.subRecipeId != recipe.id) {
                "A recipe cannot contain itself"
            }
        }
        val timestamp = now()
        val createdAt = if (recipe.id == 0L) timestamp else dao.getCreatedAt(recipe.id) ?: timestamp
        return dao.save(
            recipe = recipe.copy(name = recipe.name.trim(), createdAt = createdAt, updatedAt = timestamp),
            lines = lines.mapIndexed { index, line -> line.copy(position = index) },
            extras = extras,
        )
    }

    /** Recipes that use this one as a sub-recipe. */
    suspend fun recipesUsing(recipeId: Long): List<Recipe> = dao.recipesUsing(recipeId)

    /** Deletes the recipe with its lines and extras, unless another recipe uses it. */
    suspend fun delete(recipeId: Long): DeleteResult {
        val recipes = dao.recipesUsing(recipeId)
        if (recipes.isNotEmpty()) return DeleteResult.InUse(recipes)
        dao.deleteById(recipeId)
        return DeleteResult.Deleted
    }

    private fun RecipeWithDetails.sorted() = copy(
        lines = lines.sortedBy { it.position },
        extras = extras.sortedBy { it.id },
    )
}
