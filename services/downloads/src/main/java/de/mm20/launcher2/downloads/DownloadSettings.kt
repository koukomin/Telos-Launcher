package de.mm20.launcher2.downloads

import android.content.Context
import android.content.SharedPreferences
import de.mm20.launcher2.downloads.logic.QueueSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class AfterFinish { Nothing, Open, Share }

enum class ProxyType { None, Http, Socks }

data class DownloadSettingsValues(
    val maxParallel: Int = 3,
    /** Connections per download, 1 to 16 */
    val connections: Int = 8,
    /** All downloads together, KB/s, 0 = no limit */
    val speedLimitKBps: Int = 0,
    val wifiOnly: Boolean = false,
    val pauseOnLowBattery: Boolean = false,
    val lowBatteryPercent: Int = 15,
    /** tree uri of the default folder, empty: Downloads/Telos */
    val defaultFolder: String = "",
    val notifications: Boolean = true,
    val maxRetries: Int = 5,
    /** empty: a browser like default */
    val userAgent: String = "",
    val proxyType: ProxyType = ProxyType.None,
    val proxyHost: String = "",
    val proxyPort: Int = 0,
    val afterFinish: AfterFinish = AfterFinish.Nothing,
) {
    val queue: QueueSettings
        get() = QueueSettings(maxParallel, wifiOnly, pauseOnLowBattery, lowBatteryPercent)

    val effectiveUserAgent: String get() = userAgent.ifBlank { DEFAULT_USER_AGENT }

    companion object {
        const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36 TelosDownloads"
    }
}

/** Settings of Telos Downloads. Plain preferences so they are easy to put into a backup later. */
class DownloadSettings(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("telos_downloads", Context.MODE_PRIVATE)
    private val _values = MutableStateFlow(read())
    val values: StateFlow<DownloadSettingsValues> = _values

    val current: DownloadSettingsValues get() = _values.value

    @Synchronized
    fun update(transform: (DownloadSettingsValues) -> DownloadSettingsValues) {
        val v = sanitize(transform(_values.value))
        prefs.edit()
            .putInt("maxParallel", v.maxParallel)
            .putInt("connections", v.connections)
            .putInt("speedLimitKBps", v.speedLimitKBps)
            .putBoolean("wifiOnly", v.wifiOnly)
            .putBoolean("pauseOnLowBattery", v.pauseOnLowBattery)
            .putInt("lowBatteryPercent", v.lowBatteryPercent)
            .putString("defaultFolder", v.defaultFolder)
            .putBoolean("notifications", v.notifications)
            .putInt("maxRetries", v.maxRetries)
            .putString("userAgent", v.userAgent)
            .putString("proxyType", v.proxyType.name)
            .putString("proxyHost", v.proxyHost)
            .putInt("proxyPort", v.proxyPort)
            .putString("afterFinish", v.afterFinish.name)
            .apply()
        _values.value = v
    }

    private fun read(): DownloadSettingsValues {
        val d = DownloadSettingsValues()
        return sanitize(
            DownloadSettingsValues(
                maxParallel = prefs.getInt("maxParallel", d.maxParallel),
                connections = prefs.getInt("connections", d.connections),
                speedLimitKBps = prefs.getInt("speedLimitKBps", d.speedLimitKBps),
                wifiOnly = prefs.getBoolean("wifiOnly", d.wifiOnly),
                pauseOnLowBattery = prefs.getBoolean("pauseOnLowBattery", d.pauseOnLowBattery),
                lowBatteryPercent = prefs.getInt("lowBatteryPercent", d.lowBatteryPercent),
                defaultFolder = prefs.getString("defaultFolder", d.defaultFolder).orEmpty(),
                notifications = prefs.getBoolean("notifications", d.notifications),
                maxRetries = prefs.getInt("maxRetries", d.maxRetries),
                userAgent = prefs.getString("userAgent", d.userAgent).orEmpty(),
                proxyType = runCatching { ProxyType.valueOf(prefs.getString("proxyType", "None")!!) }.getOrDefault(ProxyType.None),
                proxyHost = prefs.getString("proxyHost", "").orEmpty(),
                proxyPort = prefs.getInt("proxyPort", 0),
                afterFinish = runCatching { AfterFinish.valueOf(prefs.getString("afterFinish", "Nothing")!!) }.getOrDefault(AfterFinish.Nothing),
            )
        )
    }

    private fun sanitize(v: DownloadSettingsValues) = v.copy(
        maxParallel = v.maxParallel.coerceIn(1, 10),
        connections = v.connections.coerceIn(1, 16),
        speedLimitKBps = v.speedLimitKBps.coerceAtLeast(0),
        lowBatteryPercent = v.lowBatteryPercent.coerceIn(5, 50),
        maxRetries = v.maxRetries.coerceIn(0, 20),
        proxyPort = v.proxyPort.coerceIn(0, 65535),
    )
}
