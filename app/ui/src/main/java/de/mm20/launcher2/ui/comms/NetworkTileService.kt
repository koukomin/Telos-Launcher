package de.mm20.launcher2.ui.comms

import de.mm20.launcher2.base.containedScope
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import de.mm20.launcher2.comms.radio.CellularRadio
import de.mm20.launcher2.preferences.comms.CommsSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class NetworkTileService : TileService() {
    private val commsSettings: CommsSettings by inject()
    private val scope = containedScope(Dispatchers.Default)

    override fun onStartListening() {
        qsTile?.state = Tile.STATE_ACTIVE
        qsTile?.label = "Network"
        qsTile?.updateTile()
    }

    override fun onClick() {
        scope.launch {
            val current = commsSettings.preferredNetworkMode.first()
            val next = when (current) {
                "lte" -> "nr_lte"
                "nr_lte" -> "nr"
                "nr" -> "auto"
                else -> "lte"
            }
            commsSettings.setPreferredNetworkMode(next)
            CellularRadio.applyPreferred(applicationContext, next)
        }
        qsTile?.label = "Cycled"
        qsTile?.updateTile()
    }
}
