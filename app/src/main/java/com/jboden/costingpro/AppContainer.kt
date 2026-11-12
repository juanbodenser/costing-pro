package com.jboden.costingpro

import android.content.Context
import com.jboden.costingpro.data.CostingDatabase
import com.jboden.costingpro.data.IngredientRepository
import com.jboden.costingpro.data.PhotoStore
import com.jboden.costingpro.data.RecipeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Manual dependency injection: the single place where app-wide objects are created.
 *
 * @param createDatabase opens the database the first time it is needed. Tests pass an in-memory one.
 * @param createPhotoStore creates the photo store the first time it is needed.
 */
class AppContainer(
    createDatabase: () -> CostingDatabase,
    createPhotoStore: () -> PhotoStore = { error("This container has no photo store") },
) {

    /** The container of the real app, over the database file and the files directory of the device. */
    constructor(context: Context) : this(
        { CostingDatabase.create(context, CoroutineScope(SupervisorJob() + Dispatchers.IO)) },
        { PhotoStore(context.applicationContext) },
    )

    private val database: CostingDatabase by lazy(createDatabase)

    val photoStore: PhotoStore by lazy(createPhotoStore)

    val ingredientRepository: IngredientRepository by lazy { IngredientRepository(database.ingredientDao()) }

    val recipeRepository: RecipeRepository by lazy { RecipeRepository(database.recipeDao()) }
}
