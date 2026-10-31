package de.mm20.launcher2.network.privatedns
import de.mm20.launcher2.i18n.R as I18nR

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import de.mm20.launcher2.applock.SettingsDeepLinkContract
import de.mm20.launcher2.network.R
import de.mm20.launcher2.network.api.PrivateDnsMode
import de.mm20.launcher2.network.api.PrivateDnsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class PrivateDnsTileService : TileService(), KoinComponent {
    private val privateDns: PrivateDnsController by inject()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var operation: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        if (operation?.isActive == true) return
        operation = serviceScope.launch {
            when (val current = privateDns.observe()) {
                is PrivateDnsResult.Success -> {
                    val target = PrivateDnsTilePolicy.nextMode(current.state)
                    when (val result = privateDns.setMode(target)) {
                        is PrivateDnsResult.Success -> render(result.state)
                        is PrivateDnsResult.Failure -> openSettings()
                    }
                }
                is PrivateDnsResult.Failure -> openSettings()
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun refresh() {
        if (operation?.isActive == true) return
        operation = serviceScope.launch {
            when (val result = privateDns.observe()) {
                is PrivateDnsResult.Success -> render(result.state)
                is PrivateDnsResult.Failure -> render(null)
            }
        }
    }

    private fun render(state: PrivateDnsState?) {
        val tile = qsTile ?: return
        tile.label = getString(I18nR.string.net_dns_system)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_network_notification)
        tile.state = when {
            state == null -> Tile.STATE_UNAVAILABLE
            state.mode == PrivateDnsMode.OFF -> Tile.STATE_INACTIVE
            else -> Tile.STATE_ACTIVE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = when {
                state == null -> getString(I18nR.string.net_wifi_dns_permission_error)
                state.mode == PrivateDnsMode.PROVIDER -> state.hostname.orEmpty()
                state.mode == PrivateDnsMode.OFF -> getString(I18nR.string.hc_off)
                else -> getString(I18nR.string.desktop_mode_orientation_auto)
            }
        }
        tile.updateTile()
    }

    private fun openSettings() {
        val intent = Intent().setClassName(
            packageName,
            SettingsDeepLinkContract.ACTIVITY_CLASS_NAME,
        ).putExtra(
            SettingsDeepLinkContract.EXTRA_ROUTE,
            SettingsDeepLinkContract.ROUTE_NETWORK_PRIVATE_DNS,
        ).setData(android.net.Uri.parse("telos://tile/network-private-dns"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                TILE_PENDING_INTENT_REQUEST,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private companion object {
        const val TILE_PENDING_INTENT_REQUEST = 7112
    }
}

internal object PrivateDnsTilePolicy {
    fun nextMode(current: PrivateDnsState): PrivateDnsMode =
        if (current.mode == PrivateDnsMode.OFF) PrivateDnsMode.AUTOMATIC else PrivateDnsMode.OFF
}
