package com.jboden.costingpro.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jboden.costingpro.data.CostingDatabase
import kotlinx.coroutines.cancel
import org.junit.rules.ExternalResource

/**
 * An in-memory database for one test.
 *
 * Put it first in a [org.junit.rules.RuleChain], before the compose rule, so that it is closed last:
 * once the screens have been torn down and the view models [track]ed here have stopped observing it.
 * Closing it earlier makes a flow that is still running fail with "connection is closed".
 */
class InMemoryDatabaseRule : ExternalResource() {

    lateinit var database: CostingDatabase
        private set

    private val viewModels = mutableListOf<ViewModel>()

    override fun before() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            CostingDatabase::class.java,
        ).build()
    }

    /** Makes sure [viewModel] stops using the database when the test ends. Returns it. */
    fun <T : ViewModel> track(viewModel: T): T {
        viewModels += viewModel
        return viewModel
    }

    override fun after() {
        viewModels.forEach { it.viewModelScope.cancel() }
        database.close()
    }
}
