package com.jboden.costingpro

import android.content.Context
import com.jboden.costingpro.data.CostingDatabase
import com.jboden.costingpro.data.IngredientRepository
import com.jboden.costingpro.data.RecipeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Manual dependency injection: the single place where app-wide objects are created.
 *
 * @param createDatabase opens the database the first time it is needed. Tests pass an in-memory one.
 */
class AppContainer(createDatabase: () -> CostingDatabase) {

    /** The container of the real app, over the database file on the device. */
    constructor(context: Context) : this(
        { CostingDatabase.create(context, CoroutineScope(SupervisorJob() + Dispatchers.IO)) },
    )

    private val database: CostingDatabase by lazy(createDatabase)

    val ingredientRepository: IngredientRepository by lazy { IngredientRepository(database.ingredientDao()) }

    val recipeRepository: RecipeRepository by lazy { RecipeRepository(database.recipeDao()) }
}
