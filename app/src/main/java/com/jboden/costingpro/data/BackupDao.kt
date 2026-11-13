package com.jboden.costingpro.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

/** Reads every table as it is, and replaces all of them at once or adds to them, for the backup. */
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

    /**
     * Adds the given rows to the data already there, in one transaction. The rows get new ids,
     * and the lines and extras are pointed at them. An ingredient whose name already exists
     * (regardless of case) is not added: the lines use the existing one, with its own price.
     * The ids of the given rows only link them to each other.
     */
    @Transaction
    open suspend fun addAll(
        ingredients: List<Ingredient>,
        recipes: List<Recipe>,
        lines: List<RecipeLine>,
        extras: List<ExtraCost>,
    ) {
        val ingredientIds = ingredients.associate { it.id to (ingredientIdByName(it.name) ?: insertIngredient(it.copy(id = 0))) }
        val recipeIds = recipes.associate { it.id to insertRecipe(it.copy(id = 0)) }
        insertLines(
            lines.map { line ->
                line.copy(
                    id = 0,
                    recipeId = recipeIds.getValue(line.recipeId),
                    ingredientId = line.ingredientId?.let(ingredientIds::getValue),
                    subRecipeId = line.subRecipeId?.let(recipeIds::getValue),
                )
            },
        )
        insertExtras(extras.map { it.copy(id = 0, recipeId = recipeIds.getValue(it.recipeId)) })
    }

    /** The NOCASE collation of the column makes this match regardless of case. */
    @Query("SELECT id FROM ingredient WHERE name = :name LIMIT 1")
    protected abstract suspend fun ingredientIdByName(name: String): Long?

    @Insert
    protected abstract suspend fun insertIngredient(row: Ingredient): Long

    @Insert
    protected abstract suspend fun insertRecipe(row: Recipe): Long

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
