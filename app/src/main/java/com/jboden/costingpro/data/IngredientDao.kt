package com.jboden.costingpro.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface IngredientDao {

    @Query("SELECT * FROM ingredient ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Ingredient>>

    @Query("SELECT * FROM ingredient ORDER BY name COLLATE NOCASE")
    suspend fun getAll(): List<Ingredient>

    @Query("SELECT * FROM ingredient WHERE id = :id")
    suspend fun getById(id: Long): Ingredient?

    @Query("SELECT * FROM ingredient WHERE name = :name")
    suspend fun getByName(name: String): Ingredient?

    @Query("SELECT COUNT(*) FROM ingredient")
    fun observeCount(): Flow<Int>

    @Insert
    suspend fun insert(ingredient: Ingredient): Long

    @Insert
    suspend fun insertAll(ingredients: List<Ingredient>): List<Long>

    @Update
    suspend fun update(ingredient: Ingredient)

    @Query("DELETE FROM ingredient WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Recipes that have a line with this ingredient. */
    @Query(
        """
        SELECT DISTINCT recipe.* FROM recipe
        JOIN recipe_line ON recipe_line.recipeId = recipe.id
        WHERE recipe_line.ingredientId = :ingredientId
        ORDER BY recipe.name COLLATE NOCASE
        """,
    )
    suspend fun recipesUsing(ingredientId: Long): List<Recipe>
}
