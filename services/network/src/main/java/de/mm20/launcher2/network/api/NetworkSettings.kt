package de.mm20.launcher2.network.api

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.coroutines.flow.map

/** All settings of Telos Network in one immutable snapshot. Defaults are the safe, quiet choice. */
@Serializable
data class NetworkSettingsValues(
    /**
     * Start the VPN after the device booted. Off by default. Even when on, the VPN only starts if
     * the Android VPN permission was granted before and the user left it switched on ([engineWanted]).
     */
    val startOnBoot: Boolean = false,
    /**
     * True while the user wants the VPN on. Set by [de.mm20.launcher2.network.NetworkEngine.start]
     * and cleared by `stop()`. Together with [startOnBoot] it decides whether to start after a boot.
     * Not meant to be edited by the UI.
     */
    val engineWanted: Boolean = false,
    /** Which IP families are routed into the tunnel. */
    val ipMode: IpMode = IpMode.V46,
    /** Let apps bind sockets to other networks and bypass the VPN (`VpnService.Builder.allowBypass`). */
    val allowBypass: Boolean = true,
    /** Keep Telos itself out of the tunnel. Strongly recommended, otherwise Telos' own traffic loops. */
    val excludeSelf: Boolean = true,
    /** Send traffic to the local network through the tunnel too, so LAN rules can apply. When false LAN ranges bypass it. */
    val routeLan: Boolean = true,
    /** Tell Android the VPN connection is metered. */
    val vpnMetered: Boolean = false,
    /** Tunnel MTU. 0 = automatic (the smallest MTU of the underlying networks, at least 1280). */
    val mtu: Int = 0,
    /**
     * When no network is available: false (default) = fail-open, the tunnel stays routed so
     * that traffic resumes when a network returns. true = block until a network is back.
     */
    val stallOnNoNetwork: Boolean = false,
    /** How much the notification of the running VPN shows. */
    val notificationDetail: NotificationDetail = NotificationDetail.Minimal,
    /** Hide the notification content on the lock screen. */
    val notificationHideOnLockScreen: Boolean = true,
    /** Show an alert notification when the VPN stopped by itself (crash, revoked, failed to start). */
    val notifyOnFailure: Boolean = true,
    /** Write connection logs. */
    val logConnections: Boolean = true,
    /** Write DNS logs. */
    val logDns: Boolean = true,
    /** Upper bound for the number of entries of each log; older entries are dropped. */
    val logMaxEntries: Int = 5000,
    /** Entries older than this many days are dropped. 0 = keep until [logMaxEntries]. */
    val logRetentionDays: Int = 7,
)

/**
 * Persistent settings of Telos Network. Implementations must be cheap to read from any thread:
 * [values] always holds the current value and never blocks.
 */
interface NetworkSettings {
    /** The current settings. Emits a new value after each change. */
    val values: StateFlow<NetworkSettingsValues>

    /** The current value, same as `values.value`. */
    val current: NetworkSettingsValues get() = values.value

    /** Writes pending changes to disk now. Used for flags that must survive a crash. */
    fun flush() {}

    /** Atomically changes the settings: [transform] receives the current value and returns the new one. */
    fun update(transform: (NetworkSettingsValues) -> NetworkSettingsValues)

    val startOnBoot: Flow<Boolean> get() = values.map { it.startOnBoot }
    val ipMode: Flow<IpMode> get() = values.map { it.ipMode }
    val allowBypass: Flow<Boolean> get() = values.map { it.allowBypass }

    fun setStartOnBoot(value: Boolean) = update { it.copy(startOnBoot = value) }
    fun setIpMode(value: IpMode) = update { it.copy(ipMode = value) }
    fun setAllowBypass(value: Boolean) = update { it.copy(allowBypass = value) }
    fun setExcludeSelf(value: Boolean) = update { it.copy(excludeSelf = value) }
    fun setRouteLan(value: Boolean) = update { it.copy(routeLan = value) }
    fun setVpnMetered(value: Boolean) = update { it.copy(vpnMetered = value) }
    fun setMtu(value: Int) = update { it.copy(mtu = value.coerceIn(0, 9000)) }
    fun setStallOnNoNetwork(value: Boolean) = update { it.copy(stallOnNoNetwork = value) }
    fun setNotificationDetail(value: NotificationDetail) = update { it.copy(notificationDetail = value) }
    fun setNotificationHideOnLockScreen(value: Boolean) = update { it.copy(notificationHideOnLockScreen = value) }
    fun setNotifyOnFailure(value: Boolean) = update { it.copy(notifyOnFailure = value) }
    fun setLogConnections(value: Boolean) = update { it.copy(logConnections = value) }
    fun setLogDns(value: Boolean) = update { it.copy(logDns = value) }
    fun setLogMaxEntries(value: Int) = update { it.copy(logMaxEntries = value.coerceIn(100, 100_000)) }
    fun setLogRetentionDays(value: Int) = update { it.copy(logRetentionDays = value.coerceIn(0, 365)) }
}
