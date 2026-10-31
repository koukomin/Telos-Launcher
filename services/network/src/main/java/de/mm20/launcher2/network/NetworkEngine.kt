package de.mm20.launcher2.network
import de.mm20.launcher2.i18n.R as I18nR

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.provider.Settings
import de.mm20.launcher2.network.vpn.VpnPausePolicy
import de.mm20.launcher2.network.vpn.VpnPauseScheduler
import androidx.core.content.ContextCompat
import com.celzero.firestack.intra.Tunnel
import de.mm20.launcher2.network.api.EngineComponent
import de.mm20.launcher2.network.api.NetworkSettings
import de.mm20.launcher2.network.vpn.TelosVpnService
import de.mm20.launcher2.network.vpn.VpnNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Why the VPN service ended. */
internal enum class StopReason {
    /** The user asked for it (stop button, notification action, or Android settings). */
    User,

    /** Something failed: the VPN could not start, or the engine stopped working. */
    Failure,

    /** Android or another VPN app took the VPN away. */
    Revoked,
}

/**
 * Starts, stops and restarts Telos Network. This is the one object the rest of the app talks to.
 *
 * Safety rules (see also [TelosVpnService]):
 *  - Previously active user intent survives process death and updates. Explicit Stop and VPN
 *    revocation cancel it. Reboot restoration additionally requires "start on boot".
 *  - Whenever the engine fails to start or stops working, the VPN is torn down. The device then
 *    has its normal internet connection again (fail-open), and [state] becomes [NetState.Error].
 *  - [stop] always works, also while starting.
 */
class NetworkEngine internal constructor(
    private val context: Context,
    private val settings: NetworkSettings,
    internal val components: List<EngineComponent>,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow<NetState>(NetState.Off)

    /** What the VPN is doing. */
    val state: StateFlow<NetState> = _state

    private val _tunnel = MutableStateFlow<Tunnel?>(null)

    /**
     * The live Go tunnel while the VPN is [NetState.On], otherwise null. For controllers that
     * have to push changes into the engine at runtime. Do not close it.
     */
    val tunnel: StateFlow<Tunnel?> = _tunnel

    private val componentsLock = Mutex()
    private var watchdog: Job? = null
    private var recovery: Job? = null
    private var stableConnection: Job? = null
    private var recoveryAttempts = 0

    /**
     * Turns the VPN on. Call this only as the result of a user action.
     *
     * If Android needs the user's consent, [state] becomes [NetState.NeedsPermission]; show the
     * intent from there and call [start] again after the user agreed. Does nothing while
     * [NetState.Starting] or [NetState.On].
     */
    @Synchronized
    fun start() {
        val current = _state.value
        if (current is NetState.Starting || current is NetState.On) return
        val permissionIntent = try {
            VpnService.prepare(context)
        } catch (e: Exception) {
            fail(StopReason.Failure, e.message)
            return
        }
        if (permissionIntent != null) {
            _state.value = NetState.NeedsPermission(permissionIntent)
            return
        }
        VpnPauseScheduler.cancel(context)
        settings.update { it.copy(engineWanted = true, pauseUntilMs = 0, intentBootCount = bootCount()) }
        settings.flush()
        _state.value = NetState.Starting
        watchdog?.cancel()
        watchdog = scope.launch {
            delay(START_TIMEOUT_MS)
            if (_state.value is NetState.Starting) {
                val service = TelosVpnService.current
                if (service != null) service.requestStop(StopReason.Failure, "timeout")
                else fail(StopReason.Failure, "timeout")
            }
        }
        try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, TelosVpnService::class.java).setAction(TelosVpnService.ACTION_START),
            )
        } catch (e: Exception) {
            // for example Android 12+ refuses a foreground service start from the background
            fail(StopReason.Failure, e.message)
        }
    }

    /** Restore prior user intent without ever opening consent from a background context. */
    @Synchronized
    fun restoreIfWanted() {
        val s = settings.current
        if (VpnPausePolicy.rebootCancels(s.intentBootCount, bootCount(), s.startOnBoot)) {
            settings.update { it.copy(engineWanted = false, pauseUntilMs = 0) }
            settings.flush()
            VpnPauseScheduler.cancel(context)
            refreshStatusNotification()
            return
        }
        if (!s.engineWanted) { refreshStatusNotification(); return }
        if (runCatching { VpnService.prepare(context) != null }.getOrDefault(true)) {
            settings.update { it.copy(engineWanted = false, pauseUntilMs = 0) }
            settings.flush()
            VpnPauseScheduler.cancel(context)
            refreshStatusNotification()
            return
        }
        if (VpnPausePolicy.pending(s.engineWanted, s.pauseUntilMs, System.currentTimeMillis())) {
            VpnPauseScheduler.schedule(context, s.pauseUntilMs)
            refreshStatusNotification()
            return
        }
        start()
    }

    /** Turns the VPN off. Safe to call in any state. The internet works normally again right after. */
    @Synchronized
    fun stop() {
        recovery?.cancel()
        recoveryAttempts = 0
        VpnPauseScheduler.cancel(context)
        settings.update { it.copy(engineWanted = false, pauseUntilMs = 0) }
        settings.flush()
        val service = TelosVpnService.current
        if (service != null) {
            service.requestStop(StopReason.User, null)
        } else {
            watchdog?.cancel()
            _tunnel.value = null
            _state.value = NetState.Off
            refreshStatusNotification()
        }
    }

    /** Temporarily disconnect while retaining explicitly requested active intent. */
    @Synchronized
    fun pause(durationMs: Long) {
        if (_state.value !is NetState.On || durationMs !in VpnPausePolicy.durationsMs) return
        val deadline = System.currentTimeMillis() + durationMs
        recovery?.cancel()
        settings.update { it.copy(engineWanted = true, pauseUntilMs = deadline, intentBootCount = bootCount()) }
        settings.flush()
        VpnPauseScheduler.schedule(context, deadline)
        TelosVpnService.current?.requestStop(StopReason.User, null)
    }

    private fun bootCount(): Int = runCatching { Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT) }.getOrDefault(-1)

    private fun refreshStatusNotification() {
        if (_state.value !is NetState.On && _state.value !is NetState.Starting) VpnNotifications.inactive(context, settings.current)
    }

    /** Stops the VPN and starts it again, for example to apply changes that need a new interface. */
    fun restart() {
        if (_state.value !is NetState.On) return
        val service = TelosVpnService.current ?: return
        scope.launch {
            // Preserve user intent across configuration restart, but respect a subsequent Stop.
            service.requestStop(StopReason.User, null)
            val stopped = withTimeoutOrNull(RESTART_WAIT_MS) { _state.first { it is NetState.Off } }
            if (stopped != null) restoreIfWanted()
        }
    }

    /** Leaves [NetState.Error] and [NetState.NeedsPermission] for [NetState.Off]. */
    fun acknowledgeError() {
        val current = _state.value
        if (current is NetState.Error || current is NetState.NeedsPermission) _state.value = NetState.Off
    }

    /** Closes all open connections so that apps reconnect and new rules apply to them. */
    fun closeAllConnections() {
        try {
            _tunnel.value?.closeConns("")
        } catch (e: Exception) {
            // the tunnel went away meanwhile
        }
    }

    // --- called by the service ---

    internal fun onServiceReady(tunnel: Tunnel) {
        watchdog?.cancel()
        _tunnel.value = tunnel
        _state.value = NetState.On
        stableConnection?.cancel()
        stableConnection = scope.launch {
            delay(30_000L)
            if (_tunnel.value === tunnel) recoveryAttempts = 0
        }
    }

    internal fun onServiceStopped(reason: StopReason, detail: String?) {
        watchdog?.cancel()
        _tunnel.value = null
        stableConnection?.cancel()
        scope.launch {
            componentsLock.withLock {
                components.forEach { c ->
                    try {
                        c.onTunnelDisconnected()
                    } catch (e: Throwable) {
                        // a broken component must not keep the others from cleaning up
                    }
                }
                when (reason) {
                    StopReason.User -> _state.value = NetState.Off
                    StopReason.Failure, StopReason.Revoked -> {
                        if (reason == StopReason.Revoked) {
                            settings.update { it.copy(engineWanted = false, pauseUntilMs = 0) }
                            settings.flush()
                            recovery?.cancel()
                            VpnPauseScheduler.cancel(context)
                        }
                        _state.value = NetState.Error(messageFor(reason, detail))
                        if (reason == StopReason.Failure && settings.current.engineWanted && recoveryAttempts < de.mm20.launcher2.network.vpn.VpnRecoveryPolicy.MAX_RETRIES) {
                            val attempt = ++recoveryAttempts
                            recovery?.cancel()
                            recovery = scope.launch {
                                delay(attempt * 3000L)
                                restoreIfWanted()
                            }
                        }
                    }
                }
                refreshStatusNotification()
            }
        }
    }

    /** Hands the tunnel to the components so they can apply their configuration. */
    internal suspend fun applyComponents(tunnel: Tunnel) = componentsLock.withLock {
        components.forEach { c ->
            try {
                c.onTunnelConnected(tunnel)
            } catch (e: Throwable) {
                // one component failing does not stop the VPN; DNS falls back to the system
            }
        }
    }

    /** The alert text for a stop that was not the user's doing. */
    internal fun alertTextFor(reason: StopReason): String = when (reason) {
        StopReason.Revoked -> context.getString(I18nR.string.net_alert_revoked)
        StopReason.Failure -> context.getString(I18nR.string.net_alert_crashed)
        StopReason.User -> ""
    }

    private fun messageFor(reason: StopReason, detail: String?): String = when (reason) {
        StopReason.Revoked -> context.getString(I18nR.string.net_alert_revoked)
        else -> context.getString(
            I18nR.string.net_error_start_failed,
            detail?.takeIf { it.isNotBlank() } ?: context.getString(I18nR.string.net_error_unknown),
        )
    }

    /** Failure before or without a service (it could not be started at all). */
    private fun fail(reason: StopReason, detail: String?) {
        onServiceStopped(reason, detail)
        if (settings.current.notifyOnFailure) {
            VpnNotifications.alert(context, context.getString(I18nR.string.net_alert_start_failed))
        }
    }

    private companion object {
        const val START_TIMEOUT_MS = 30_000L
        const val RESTART_WAIT_MS = 8_000L
    }
}
