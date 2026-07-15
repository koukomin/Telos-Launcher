package de.mm20.launcher2.ui.launcher.widgets.network

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.widgets.NetworkWidget
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

@Composable
fun NetworkWidget(widget: NetworkWidget) {
    val context = LocalContext.current
    val state by remember(context) { networkStateFlow(context) }.collectAsState(NetworkWidgetState())

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                context.tryStartActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
            }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(getNetworkIcon(state)),
            contentDescription = null,
            modifier = Modifier.size(40.dp),
        )
        Column(
            modifier = Modifier.padding(start = 16.dp),
        ) {
            Text(
                text = stringResource(getNetworkLabel(state)),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

private enum class NetworkTransport {
    Wifi, Cellular, Ethernet, Other
}

private data class NetworkWidgetState(
    val connected: Boolean = false,
    val transport: NetworkTransport? = null,
)

private fun getNetworkIcon(state: NetworkWidgetState): Int {
    if (!state.connected) {
        return if (state.transport == NetworkTransport.Cellular) R.drawable.signal_cellular_off_24px
        else R.drawable.wifi_off_24px
    }
    return when (state.transport) {
        NetworkTransport.Wifi -> R.drawable.wifi_24px
        NetworkTransport.Cellular -> R.drawable.signal_cellular_alt_24px
        NetworkTransport.Ethernet -> R.drawable.lan_24px
        else -> R.drawable.wifi_24px
    }
}

private fun getNetworkLabel(state: NetworkWidgetState): Int {
    if (!state.connected) return R.string.network_widget_disconnected
    return when (state.transport) {
        NetworkTransport.Wifi -> R.string.network_widget_wifi
        NetworkTransport.Cellular -> R.string.network_widget_cellular
        NetworkTransport.Ethernet -> R.string.network_widget_ethernet
        else -> R.string.network_widget_connected
    }
}

private fun stateFor(capabilities: NetworkCapabilities?): NetworkWidgetState {
    if (capabilities == null) return NetworkWidgetState(connected = false)
    val transport = when {
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkTransport.Wifi
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkTransport.Cellular
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkTransport.Ethernet
        else -> NetworkTransport.Other
    }
    return NetworkWidgetState(connected = true, transport = transport)
}

private fun networkStateFlow(context: Context): Flow<NetworkWidgetState> = callbackFlow {
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

    awaitClose {
        connectivityManager.unregisterNetworkCallback(callback)
    }
}
