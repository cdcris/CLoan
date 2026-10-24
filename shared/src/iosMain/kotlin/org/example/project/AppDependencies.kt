package org.example.project

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.example.project.db.Database

fun createAppViewModel(): AppViewModel {
    val database = Database(NativeSqliteDriver(Database.Schema, "cloan.db"))
    return AppViewModel(database)
}

class AppStateSubscription(viewModel: AppViewModel, listener: AppStateListener) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    init {
        scope.launch {
            viewModel.uiState.collect { state -> listener.onStateChanged(state) }
        }
    }

    fun cancel() {
        scope.cancel()
    }
}
