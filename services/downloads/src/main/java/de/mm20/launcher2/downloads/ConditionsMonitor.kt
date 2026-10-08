package de.mm20.launcher2.downloads

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import de.mm20.launcher2.downloads.logic.Conditions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** Network type and battery level as a flow, for the queue rules. Starts when the first download needs it. */
class ConditionsMonitor(private val context: Context) {
    private val _conditions = MutableStateFlow(Conditions())
    val conditions: StateFlow<Conditions> = _conditions
    private var started = false
    @Volatile private var current: Network? = null

    @Synchronized
    fun start() {
        if (started) return
        started = true
        try {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            // the callback below is silent when there is no network at all: start from what is true now
            val caps0 = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
            _conditions.update {
                it.copy(
                    online = caps0?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true,
                    unmetered = caps0?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true,
                )
            }
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    current = network
                    _conditions.update {
                        it.copy(
                            online = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
                            unmetered = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
                        )
                    }
                }

                override fun onLost(network: Network) {
                    // the old network of a switch (Wi-Fi to mobile) can be reported lost after the new one is up
                    if (current != null && current != network) return
                    current = null
                    _conditions.update { it.copy(online = false) }
                }
            })
        } catch (_: Exception) {
            // without the network state we assume that we are online
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) = readBattery(intent)
        }
        val sticky = ContextCompat.registerReceiver(
            context, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        if (sticky != null) readBattery(sticky)
    }

    private fun readBattery(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        if (level >= 0 && scale > 0) {
            _conditions.update { it.copy(batteryPercent = level * 100 / scale, charging = charging) }
        }
    }
}
