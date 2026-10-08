package de.mm20.launcher2.network.impl

import android.content.Context
import de.mm20.launcher2.network.api.NetworkSettings
import de.mm20.launcher2.network.api.NetworkSettingsValues
import de.mm20.launcher2.network.util.PersistedState
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/** Settings of Telos Network in `filesDir/network/settings.json`. */
internal class DefaultNetworkSettings(context: Context) : NetworkSettings {
    private val store = PersistedState(
        file = File(File(context.filesDir, "network"), "settings.json"),
        serializer = NetworkSettingsValues.serializer(),
        default = { NetworkSettingsValues() },
        debounceMs = 150,
    )

    override val values: StateFlow<NetworkSettingsValues> = store.state

    override fun update(transform: (NetworkSettingsValues) -> NetworkSettingsValues) {
        store.update(transform)
    }

    override fun flush() = store.saveNow()
}
