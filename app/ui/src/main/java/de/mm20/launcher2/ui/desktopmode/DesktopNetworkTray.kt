package de.mm20.launcher2.ui.desktopmode

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.getSystemService
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.ui.R
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

private enum class DesktopNetworkTransport { Wifi, Cellular, Ethernet, Other }

private data class DesktopNetworkState(
    val connected: Boolean = false,
    val transport: DesktopNetworkTransport? = null,
)

@Composable
internal fun DesktopNetworkTrayIcon() {
    val context = LocalContext.current
    val state by remember { networkStateFlow(context) }.collectAsState(DesktopNetworkState())

    DesktopTrayIconButton(
        icon = if (!state.connected) {
            R.drawable.wifi_off_24px
        } else if (state.transport == DesktopNetworkTransport.Ethernet) {
            R.drawable.lan_24px
        } else {
            R.drawable.wifi_24px
        },
        contentDescription = stringResource(R.string.desktop_mode_tray_network),
        onClick = {
            context.tryStartActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
        },
    )
}

private fun stateFor(capabilities: NetworkCapabilities?): DesktopNetworkState {
    if (capabilities == null) return DesktopNetworkState(connected = false)
    val transport = when {
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> DesktopNetworkTransport.Wifi
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> DesktopNetworkTransport.Cellular
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> DesktopNetworkTransport.Ethernet
        else -> DesktopNetworkTransport.Other
    }
    return DesktopNetworkState(connected = true, transport = transport)
}

private fun networkStateFlow(context: Context): Flow<DesktopNetworkState> = callbackFlow {
    val connectivityManager: ConnectivityManager = context.getSystemService() ?: return@callbackFlow

    trySendBlocking(
        stateFor(connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork))
    )

    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            if (network == connectivityManager.activeNetwork) {
                trySendBlocking(stateFor(networkCapabilities))
            }
        }

        override fun onAvailable(network: Network) {
            trySendBlocking(stateFor(connectivityManager.getNetworkCapabilities(network)))
        }

        override fun onLost(network: Network) {
            trySendBlocking(
                stateFor(connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork))
            )
        }
    }

    val request = NetworkRequest.Builder()
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .build()
    connectivityManager.registerNetworkCallback(request, callback)

    awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
}
