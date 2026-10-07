package de.mm20.launcher2.ui.calculator

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import de.mm20.launcher2.applock.SettingsDeepLinkContract

/** Quick Settings tile that opens Telos Calculator */
class CalculatorTileService : TileService() {

    override fun onStartListening() {
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = "Calculator"
            updateTile()
        }
    }

    override fun onClick() {
        val intent = Intent().apply {
            setClassName(packageName, SettingsDeepLinkContract.ACTIVITY_CLASS_NAME)
            putExtra(SettingsDeepLinkContract.EXTRA_ROUTE, SettingsDeepLinkContract.ROUTE_CALCULATOR)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
