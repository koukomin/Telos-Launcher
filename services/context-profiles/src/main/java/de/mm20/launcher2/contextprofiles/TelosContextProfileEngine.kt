package de.mm20.launcher2.contextprofiles

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar

/**
 * Context Profile Engine for Telos Launcher.
 * Evaluates environmental triggers (Current Time, connected Wi-Fi SSID, Location)
 * to automatically switch active home screen profiles (e.g. Home, Work, Motion).
 */
object TelosContextProfileEngine {

    private const val TAG = "TelosContextProfileEngine"

    enum class ProfileContext {
        HOME,
        WORK,
        COMMUTE,
        NIGHT
    }

    data class ContextState(
        val activeContext: ProfileContext = ProfileContext.HOME,
        val connectedSsid: String? = null,
        val currentHourOfDay: Int = 12
    )

    private val _contextState = MutableStateFlow(ContextState())
    val contextState: StateFlow<ContextState> = _contextState.asStateFlow()

    fun evaluateContext(context: Context, workSsid: String? = null, homeSsid: String? = null): ProfileContext {
        val appContext = context.applicationContext
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentSsid = getConnectedSsid(appContext)

        val active = when {
            hour in 23..24 || hour in 0..6 -> ProfileContext.NIGHT
            workSsid != null && currentSsid == workSsid -> ProfileContext.WORK
            homeSsid != null && currentSsid == homeSsid -> ProfileContext.HOME
            hour in 9..17 -> ProfileContext.WORK
            else -> ProfileContext.HOME
        }

        _contextState.value = ContextState(
            activeContext = active,
            connectedSsid = currentSsid,
            currentHourOfDay = hour
        )
        Log.d(TAG, "Context evaluated: $active (SSID: $currentSsid, Hour: $hour)")
        return active
    }

    @Suppress("DEPRECATION")
    private fun getConnectedSsid(context: Context): String? {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null
        val ssid = wifiManager.connectionInfo.ssid?.removeSurrounding("\"")
        return if (ssid == "<unknown ssid>") null else ssid
    }
}
