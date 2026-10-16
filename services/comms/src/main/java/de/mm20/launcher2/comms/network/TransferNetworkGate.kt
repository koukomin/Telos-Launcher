package de.mm20.launcher2.comms.network

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Process-local observers of persistent download/torrent rules and Android's default network. */
class TransferNetworkGate private constructor(context: Context, scope: String) {
    private val prefs = context.applicationContext.getSharedPreferences("transfer_network_$scope", Context.MODE_PRIVATE)
    private val mirror = android.util.AtomicFile(java.io.File(context.filesDir, "transfer_network_$scope.json"))
    private val legacyWifiOnly = if (scope == "downloads")
        context.getSharedPreferences("telos_downloads", Context.MODE_PRIVATE).getBoolean("wifiOnly", false)
    else runCatching { org.json.JSONObject(java.io.File(context.filesDir, "video_services.json").readText()).optBoolean("wifiOnly", true) }.getOrDefault(true)
    private val cm = context.getSystemService(ConnectivityManager::class.java)
    private fun mirrored() = runCatching { org.json.JSONObject(mirror.openRead().bufferedReader().use { it.readText() }) }.getOrNull()
    private fun read(): TransferNetworkPolicy {
        val json = mirrored()
        if (json != null) return TransferNetworkPolicy(json.optBoolean("wifi", true), json.optBoolean("mobile", false),
            json.optBoolean("roaming", false), json.optBoolean("metered", false))
        return TransferNetworkPolicy(prefs.getBoolean("wifi", true), prefs.getBoolean("mobile", !legacyWifiOnly),
            prefs.getBoolean("roaming", !legacyWifiOnly), prefs.getBoolean("metered", !legacyWifiOnly))
    }
    private val _policy = MutableStateFlow(read())
    val policy: StateFlow<TransferNetworkPolicy> = _policy
    private val _configured = MutableStateFlow(prefs.getBoolean("configured", false))
    val configured: StateFlow<Boolean> = _configured
    private val _allowed = MutableStateFlow(false)
    val allowed: StateFlow<Boolean> = _allowed
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        _policy.value = read()
        _configured.value = prefs.getBoolean("configured", false)
        refresh()
    }
    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
        refresh()
        runCatching { cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = refresh()
            override fun onLost(network: Network) = refresh()
        }) }
    }
    fun refresh() {
        // The isolated player reads the atomic mirror, rather than a stale cross-process preferences cache.
        _policy.value = read()
        _configured.value = mirrored() != null || prefs.getBoolean("configured", false)
        val caps = runCatching { cm.getNetworkCapabilities(cm.activeNetwork) }.getOrNull()
        _allowed.value = _policy.value.allows(
            online = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true,
            wifi = caps?.let { it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || it.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) } == true,
            mobile = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true,
            roaming = caps?.let { it.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) && !it.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING) } == true,
            metered = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) != true,
        )
    }
    fun update(value: TransferNetworkPolicy) {
        val json = org.json.JSONObject().put("wifi", value.wifi).put("mobile", value.mobile)
            .put("roaming", value.roaming).put("metered", value.metered)
        var output: java.io.FileOutputStream? = null
        try {
            output = mirror.startWrite()
            output.write(json.toString().toByteArray(Charsets.UTF_8))
            mirror.finishWrite(output)
        } catch (error: Exception) {
            output?.let { mirror.failWrite(it) }
            throw error
        }
        prefs.edit().putBoolean("wifi", value.wifi).putBoolean("mobile", value.mobile)
            .putBoolean("roaming", value.roaming).putBoolean("metered", value.metered)
            .putBoolean("configured", true).apply()
        _policy.value = value
        _configured.value = true
        refresh()
    }
    companion object {
        private val gates = mutableMapOf<String, TransferNetworkGate>()
        @Synchronized fun get(context: Context, scope: String): TransferNetworkGate =
            gates.getOrPut(scope) { TransferNetworkGate(context.applicationContext, scope) }
    }
}
