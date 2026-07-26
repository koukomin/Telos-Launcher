package de.mm20.launcher2.appmanagement

import kotlinx.coroutines.flow.map

class FossUpdateRepository internal constructor(
    private val dataStore: FossUpdateDataStore
) {
    val pendingUpdates = dataStore.data.map { it.pendingUpdates }

    fun addUpdate(packageName: String) {
        dataStore.update { it.copy(pendingUpdates = it.pendingUpdates + packageName) }
    }

    fun removeUpdate(packageName: String) {
        dataStore.update { it.copy(pendingUpdates = it.pendingUpdates - packageName) }
    }

    fun clearAll() {
        dataStore.update { it.copy(pendingUpdates = emptySet()) }
    }
}
