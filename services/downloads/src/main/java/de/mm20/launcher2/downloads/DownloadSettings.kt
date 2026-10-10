package de.mm20.launcher2.downloads

import android.content.Context
import android.content.SharedPreferences
import de.mm20.launcher2.downloads.logic.QueueSettings
import de.mm20.launcher2.downloads.logic.ScheduleWindow
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
    /**
     * Id of a WireGuard config of Telos Network (0 = none). When set, torrents (Telos Downloads and Telos Video)
     * go only through the local proxy of that config and never use [proxyType] or connect directly.
     */
    val torrentWgConfigId: Int = 0,
    val afterFinish: AfterFinish = AfterFinish.Nothing,
    /** Offer links found in the clipboard when the app comes to the foreground (off by default) */
    val detectClipboard: Boolean = false,
    /** Only download inside a time window */
    val scheduleEnabled: Boolean = false,
    val scheduleStartMinute: Int = 22 * 60,
    val scheduleEndMinute: Int = 7 * 60,
    /** bit 0 Monday ... bit 6 Sunday */
    val scheduleDays: Int = 0b1111111,
    /** Unpack finished zip archives */
    val autoExtract: Boolean = false,
) {
    val schedule: ScheduleWindow
        get() = ScheduleWindow(scheduleEnabled, scheduleStartMinute, scheduleEndMinute, scheduleDays)

    val queue: QueueSettings
        get() = QueueSettings(maxParallel, wifiOnly, pauseOnLowBattery, lowBatteryPercent, schedule)

    /** For yt-dlp: `http://host:port` or `socks5://host:port`, null without proxy */
    val proxyUrl: String?
        get() = if (proxyHost.isBlank()) null else when (proxyType) {
            ProxyType.Http -> "http://$proxyHost:$proxyPort"
            ProxyType.Socks -> "socks5://$proxyHost:$proxyPort"
            ProxyType.None -> null
        }

    fun torrentProxy() = de.mm20.launcher2.comms.media.video.torrent.TorrentProxy(
        when (proxyType) {
            ProxyType.None -> de.mm20.launcher2.comms.media.video.torrent.TorrentProxyType.None
            ProxyType.Http -> de.mm20.launcher2.comms.media.video.torrent.TorrentProxyType.Http
            ProxyType.Socks -> de.mm20.launcher2.comms.media.video.torrent.TorrentProxyType.Socks5
        },
        proxyHost, proxyPort,
    )

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

    private val KNOWN = setOf(
        "maxParallel", "connections", "speedLimitKBps", "wifiOnly", "pauseOnLowBattery", "lowBatteryPercent", "notifications",
        "maxRetries", "userAgent", "proxyType", "proxyHost", "proxyPort", "torrentWgConfigId", "afterFinish", "detectClipboard", "scheduleEnabled",
        "scheduleStartMinute", "scheduleEndMinute", "scheduleDays", "autoExtract",
    )

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
            .putInt("torrentWgConfigId", v.torrentWgConfigId)
            .putString("afterFinish", v.afterFinish.name)
            .putBoolean("detectClipboard", v.detectClipboard)
            .putBoolean("scheduleEnabled", v.scheduleEnabled)
            .putInt("scheduleStartMinute", v.scheduleStartMinute)
            .putInt("scheduleEndMinute", v.scheduleEndMinute)
            .putInt("scheduleDays", v.scheduleDays)
            .putBoolean("autoExtract", v.autoExtract)
            .apply()
        _values.value = v
    }

    /** All settings except the folder (its permission does not survive a restore) as JSON, for the backup */
    fun exportJson(): String {
        val o = org.json.JSONObject()
        for ((k, v) in prefs.all) if (k != "defaultFolder" && v != null) o.put(k, v)
        return o.toString()
    }

    /** Restores the settings written by [exportJson]; unknown keys are ignored */
    @Synchronized
    fun importJson(text: String) {
        val o = org.json.JSONObject(text)
        val e = prefs.edit()
        for (k in o.keys()) {
            if (k == "defaultFolder") continue
            when (val v = o.get(k)) {
                is Boolean -> if (k in KNOWN) e.putBoolean(k, v)
                is Int -> if (k in KNOWN) e.putInt(k, v)
                is String -> if (k in KNOWN) e.putString(k, v)
                else -> {}
            }
        }
        e.apply()
        _values.value = read()
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
                torrentWgConfigId = prefs.getInt("torrentWgConfigId", 0),
                afterFinish = runCatching { AfterFinish.valueOf(prefs.getString("afterFinish", "Nothing")!!) }.getOrDefault(AfterFinish.Nothing),
                detectClipboard = prefs.getBoolean("detectClipboard", d.detectClipboard),
                scheduleEnabled = prefs.getBoolean("scheduleEnabled", d.scheduleEnabled),
                scheduleStartMinute = prefs.getInt("scheduleStartMinute", d.scheduleStartMinute),
                scheduleEndMinute = prefs.getInt("scheduleEndMinute", d.scheduleEndMinute),
                scheduleDays = prefs.getInt("scheduleDays", d.scheduleDays),
                autoExtract = prefs.getBoolean("autoExtract", d.autoExtract),
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
        torrentWgConfigId = v.torrentWgConfigId.coerceAtLeast(0),
        scheduleStartMinute = v.scheduleStartMinute.coerceIn(0, 1439),
        scheduleEndMinute = v.scheduleEndMinute.coerceIn(0, 1439),
        scheduleDays = v.scheduleDays and 0b1111111,
    )
}
