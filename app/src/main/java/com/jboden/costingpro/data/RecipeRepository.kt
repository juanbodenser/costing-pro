package com.jboden.costingpro.data

import com.jboden.costingpro.domain.RecipeGraph
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
     * ingredient and sub-recipe, or the recipe would end up containing itself,
     * directly or through other recipes.
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
        checkNoCycle(recipe.id, lines)
        val timestamp = now()
        val createdAt = if (recipe.id == 0L) timestamp else dao.getCreatedAt(recipe.id) ?: timestamp
        return dao.save(
            recipe = recipe.copy(name = recipe.name.trim(), createdAt = createdAt, updatedAt = timestamp),
            lines = lines.mapIndexed { index, line -> line.copy(position = index) },
            extras = extras,
        )
    }

    /**
     * Sets or removes (null) the photo of a recipe and returns the path it had before,
     * so the caller can delete that file. Does not touch `updatedAt`: a photo is not a cost change.
     */
    suspend fun setPhoto(recipeId: Long, photoPath: String?): String? {
        val previous = dao.getPhotoPath(recipeId)
        dao.updatePhotoPath(recipeId, photoPath)
        return previous
    }

    /** Saves a copy of the recipe under [newName], with the same lines and extras and no photo. Returns its id. */
    suspend fun duplicate(recipeId: Long, newName: String): Long? {
        val original = get(recipeId) ?: return null
        val timestamp = now()
        return dao.save(
            recipe = original.recipe.copy(
                id = 0,
                name = newName.trim(),
                photoPath = null,
                createdAt = timestamp,
                updatedAt = timestamp,
            ),
            lines = original.lines.map { it.copy(id = 0) },
            extras = original.extras.map { it.copy(id = 0) },
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

    /** The new lines of [recipeId] must not close a loop with the sub-recipe uses already stored. */
    private suspend fun checkNoCycle(recipeId: Long, lines: List<RecipeLine>) {
        val subIds = lines.mapNotNull { it.subRecipeId }.toSet()
        if (recipeId == RecipeGraph.NO_RECIPE || subIds.isEmpty()) return
        // The stored lines of this recipe are about to be replaced, so they do not count.
        val others = dao.getSubRecipeEdges()
            .filter { it.recipeId != recipeId }
            .groupBy({ it.recipeId }, { it.subRecipeId })
            .mapValues { it.value.toSet() }
        require(subIds.none { RecipeGraph.wouldCreateCycle(recipeId, it, others) }) {
            "A recipe cannot contain itself, directly or through other recipes"
        }
    }

    private fun RecipeWithDetails.sorted() = copy(
        lines = lines.sortedBy { it.position },
        extras = extras.sortedBy { it.id },
    )
}
