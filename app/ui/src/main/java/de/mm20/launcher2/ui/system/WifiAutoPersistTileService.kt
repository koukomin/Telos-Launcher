package de.mm20.launcher2.ui.system

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import de.mm20.launcher2.base.containedScope
import de.mm20.launcher2.ui.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WifiAutoPersistTileService : TileService() {
    private val scope = containedScope(Dispatchers.IO)

    override fun onStartListening() {
        val enabled = PrivilegedWifiStore.autoPersist(applicationContext)
        qsTile?.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        qsTile?.label = getString(R.string.telos_system_wifi_auto_persist_tile)
        qsTile?.updateTile()
    }

    override fun onClick() {
        val enabled = !PrivilegedWifiStore.autoPersist(applicationContext)
        PrivilegedWifiStore.setAutoPersist(applicationContext, enabled)
        qsTile?.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        qsTile?.updateTile()
        if (enabled) scope.launch { PrivilegedWifiStore.persistEphemeral() }
    }
}
