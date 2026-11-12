package com.jboden.costingpro.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Database(
    entities = [Ingredient::class, Recipe::class, RecipeLine::class, ExtraCost::class],
    version = 1,
    exportSchema = true,
)
abstract class CostingDatabase : RoomDatabase() {

    abstract fun ingredientDao(): IngredientDao
    abstract fun recipeDao(): RecipeDao

    companion object {
        const val FILE_NAME = "costing.db"

        /**
         * Opens the app database. The first time the file is created, it is filled
         * with [SampleData] on [scope].
         */
        fun create(context: Context, scope: CoroutineScope): CostingDatabase {
            lateinit var database: CostingDatabase
            database = Room.databaseBuilder(context.applicationContext, CostingDatabase::class.java, FILE_NAME)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        scope.launch { SampleData.insertInto(database) }
                    }
                })
                .build()
            return database
        }
    }
}
