package com.jboden.costingpro.data

import kotlinx.coroutines.flow.Flow

/** Outcome of trying to delete an ingredient or a recipe. */
sealed interface DeleteResult {
    data object Deleted : DeleteResult

    /** Nothing was deleted because these recipes use the item. */
    data class InUse(val recipes: List<Recipe>) : DeleteResult
}

class IngredientRepository(
    private val dao: IngredientDao,
    private val now: () -> Long = System::currentTimeMillis,
) {

    fun observeAll(): Flow<List<Ingredient>> = dao.observeAll()

    fun observeCount(): Flow<Int> = dao.observeCount()

    suspend fun get(id: Long): Ingredient? = dao.getById(id)

    /** Finds an ingredient by name, ignoring case. */
    suspend fun findByName(name: String): Ingredient? = dao.getByName(name.trim())

    /**
     * Inserts the ingredient (id 0) or updates it, and returns its id.
     * `updatedAt` is set on insert and whenever the purchase format changes.
     */
    suspend fun save(ingredient: Ingredient): Long {
        val clean = ingredient.copy(name = ingredient.name.trim())
        if (clean.id == 0L) {
            return dao.insert(clean.copy(updatedAt = now()))
        }
        val previous = dao.getById(clean.id)
        val priceChanged = previous == null ||
            previous.purchasePrice != clean.purchasePrice ||
            previous.purchaseQuantity != clean.purchaseQuantity ||
            previous.purchaseUnit != clean.purchaseUnit
        dao.update(clean.copy(updatedAt = if (priceChanged) now() else previous!!.updatedAt))
        return clean.id
    }

    suspend fun recipesUsing(ingredientId: Long): List<Recipe> = dao.recipesUsing(ingredientId)

    /** Deletes the ingredient unless a recipe uses it. */
    suspend fun delete(ingredientId: Long): DeleteResult {
        val recipes = dao.recipesUsing(ingredientId)
        if (recipes.isNotEmpty()) return DeleteResult.InUse(recipes)
        dao.deleteById(ingredientId)
        return DeleteResult.Deleted
    }
}
