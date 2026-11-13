package com.jboden.costingpro

import android.content.Context
import com.jboden.costingpro.data.CostingDatabase
import com.jboden.costingpro.data.IngredientRepository
import com.jboden.costingpro.data.PhotoStore
import com.jboden.costingpro.data.RecipeRepository
import com.jboden.costingpro.data.SettingsStore
import com.jboden.costingpro.export.BackupManager
import com.jboden.costingpro.export.RecipePdf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Manual dependency injection: the single place where app-wide objects are created.
 *
 * @param createDatabase opens the database the first time it is needed. Tests pass an in-memory one.
 * @param createPhotoStore creates the photo store the first time it is needed.
 * @param createSettingsStore creates the settings. Tests get settings that live in memory.
 */
class AppContainer(
    createDatabase: () -> CostingDatabase,
    createPhotoStore: () -> PhotoStore = { error("This container has no photo store") },
    createSettingsStore: () -> SettingsStore = { SettingsStore() },
) {

    /** The container of the real app, over the database file and the files directory of the device. */
    constructor(context: Context) : this(
        { CostingDatabase.create(context, CoroutineScope(SupervisorJob() + Dispatchers.IO)) },
        { PhotoStore(context.applicationContext) },
        { SettingsStore.create(context) },
    )

    private val database: CostingDatabase by lazy(createDatabase)

    val photoStore: PhotoStore by lazy(createPhotoStore)

    val settings: SettingsStore by lazy(createSettingsStore)

    val ingredientRepository: IngredientRepository by lazy { IngredientRepository(database.ingredientDao()) }

    val recipeRepository: RecipeRepository by lazy { RecipeRepository(database.recipeDao()) }

    val backupManager: BackupManager by lazy {
        BackupManager(photoStore.context, database.backupDao(), photoStore, settings)
    }

    val recipePdf: RecipePdf by lazy { RecipePdf(photoStore.context, photoStore) }
}
