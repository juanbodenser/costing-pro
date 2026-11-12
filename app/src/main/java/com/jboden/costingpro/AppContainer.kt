package com.jboden.costingpro

import android.content.Context
import com.jboden.costingpro.data.CostingDatabase
import com.jboden.costingpro.data.IngredientRepository
import com.jboden.costingpro.data.RecipeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual dependency injection: the single place where app-wide objects are created. */
class AppContainer(context: Context) {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val database: CostingDatabase by lazy { CostingDatabase.create(context, applicationScope) }

    val ingredientRepository: IngredientRepository by lazy { IngredientRepository(database.ingredientDao()) }

    val recipeRepository: RecipeRepository by lazy { RecipeRepository(database.recipeDao()) }
}
