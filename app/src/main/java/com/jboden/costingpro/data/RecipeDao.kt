package com.jboden.costingpro.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
abstract class RecipeDao {

    @Transaction
    @Query("SELECT * FROM recipe ORDER BY name COLLATE NOCASE")
    abstract fun observeAllWithDetails(): Flow<List<RecipeWithDetails>>

    @Transaction
    @Query("SELECT * FROM recipe ORDER BY name COLLATE NOCASE")
    abstract suspend fun getAllWithDetails(): List<RecipeWithDetails>

    @Transaction
    @Query("SELECT * FROM recipe WHERE id = :id")
    abstract fun observeWithDetails(id: Long): Flow<RecipeWithDetails?>

    @Transaction
    @Query("SELECT * FROM recipe WHERE id = :id")
    abstract suspend fun getWithDetails(id: Long): RecipeWithDetails?

    @Query("SELECT createdAt FROM recipe WHERE id = :id")
    abstract suspend fun getCreatedAt(id: Long): Long?

    /** Every sub-recipe use stored: which recipe has a line that uses which other recipe. */
    @Query("SELECT recipeId, subRecipeId FROM recipe_line WHERE subRecipeId IS NOT NULL")
    abstract suspend fun getSubRecipeEdges(): List<SubRecipeEdge>

    @Query("SELECT photoPath FROM recipe WHERE id = :id")
    abstract suspend fun getPhotoPath(id: Long): String?

    @Query("UPDATE recipe SET photoPath = :photoPath WHERE id = :id")
    abstract suspend fun updatePhotoPath(id: Long, photoPath: String?)

    @Query("SELECT COUNT(*) FROM recipe")
    abstract fun observeCount(): Flow<Int>

    /** Recipes that use this recipe as a sub-recipe in one of their lines. */
    @Query(
        """
        SELECT DISTINCT recipe.* FROM recipe
        JOIN recipe_line ON recipe_line.recipeId = recipe.id
        WHERE recipe_line.subRecipeId = :recipeId
        ORDER BY recipe.name COLLATE NOCASE
        """,
    )
    abstract suspend fun recipesUsing(recipeId: Long): List<Recipe>

    @Query("DELETE FROM recipe WHERE id = :id")
    abstract suspend fun deleteById(id: Long)

    /**
     * Inserts the recipe (id 0) or updates it, and replaces all its lines and extras.
     * Returns the recipe id.
     */
    @Transaction
    open suspend fun save(recipe: Recipe, lines: List<RecipeLine>, extras: List<ExtraCost>): Long {
        val recipeId = if (recipe.id == 0L) {
            insert(recipe)
        } else {
            update(recipe)
            recipe.id
        }
        deleteLines(recipeId)
        deleteExtras(recipeId)
        insertLines(lines.map { it.copy(id = 0, recipeId = recipeId) })
        insertExtras(extras.map { it.copy(id = 0, recipeId = recipeId) })
        return recipeId
    }

    @Insert
    protected abstract suspend fun insert(recipe: Recipe): Long

    @Update
    protected abstract suspend fun update(recipe: Recipe)

    @Insert
    protected abstract suspend fun insertLines(lines: List<RecipeLine>)

    @Insert
    protected abstract suspend fun insertExtras(extras: List<ExtraCost>)

    @Query("DELETE FROM recipe_line WHERE recipeId = :recipeId")
    protected abstract suspend fun deleteLines(recipeId: Long)

    @Query("DELETE FROM extra_cost WHERE recipeId = :recipeId")
    protected abstract suspend fun deleteExtras(recipeId: Long)
}

/** One row of a recipe that uses another recipe: [recipeId] contains [subRecipeId]. */
data class SubRecipeEdge(val recipeId: Long, val subRecipeId: Long)
