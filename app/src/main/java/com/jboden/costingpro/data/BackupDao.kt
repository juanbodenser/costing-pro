package com.jboden.costingpro.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

/** Reads every table as it is, and replaces all of them at once, for the backup. */
@Dao
abstract class BackupDao {

    @Query("SELECT * FROM ingredient ORDER BY id")
    abstract suspend fun ingredients(): List<Ingredient>

    @Query("SELECT * FROM recipe ORDER BY id")
    abstract suspend fun recipes(): List<Recipe>

    @Query("SELECT * FROM recipe_line ORDER BY recipeId, position, id")
    abstract suspend fun lines(): List<RecipeLine>

    @Query("SELECT * FROM extra_cost ORDER BY recipeId, id")
    abstract suspend fun extras(): List<ExtraCost>

    /**
     * Deletes all the data and inserts the given rows with their own ids, in one transaction:
     * if a row breaks a constraint (for example a line that points to a missing ingredient),
     * nothing changes.
     */
    @Transaction
    open suspend fun replaceAll(
        ingredients: List<Ingredient>,
        recipes: List<Recipe>,
        lines: List<RecipeLine>,
        extras: List<ExtraCost>,
    ) {
        deleteLines()
        deleteExtras()
        deleteRecipes()
        deleteIngredients()
        insertIngredients(ingredients)
        insertRecipes(recipes)
        insertLines(lines)
        insertExtras(extras)
    }

    @Query("DELETE FROM recipe_line")
    protected abstract suspend fun deleteLines()

    @Query("DELETE FROM extra_cost")
    protected abstract suspend fun deleteExtras()

    @Query("DELETE FROM recipe")
    protected abstract suspend fun deleteRecipes()

    @Query("DELETE FROM ingredient")
    protected abstract suspend fun deleteIngredients()

    @Insert
    protected abstract suspend fun insertIngredients(rows: List<Ingredient>)

    @Insert
    protected abstract suspend fun insertRecipes(rows: List<Recipe>)

    @Insert
    protected abstract suspend fun insertLines(rows: List<RecipeLine>)

    @Insert
    protected abstract suspend fun insertExtras(rows: List<ExtraCost>)
}
