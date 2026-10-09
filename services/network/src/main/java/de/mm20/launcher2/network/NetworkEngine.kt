package de.mm20.launcher2.network

import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat
import com.celzero.firestack.intra.Tunnel
import de.mm20.launcher2.i18n.R as I18nR
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
 *  - It never starts by itself. The only automatic start is after a reboot when the user enabled
 *    "start on boot" (see [de.mm20.launcher2.network.vpn.BootReceiver]).
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

    private var watchdog: Job? = null

    /**
     * Turns the VPN on. Call this only as the result of a user action.
     *
     * If Android needs the user's consent, [state] becomes [NetState.NeedsPermission]; show the
     * intent from there and call [start] again after the user agreed. Does nothing while
     * [NetState.Starting] or [NetState.On].
     */
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
        settings.update { it.copy(engineWanted = true) }
        settings.flush()
        _state.value = NetState.Starting
        watchdog?.cancel()
        watchdog = scope.launch {
            delay(START_TIMEOUT_MS)
            if (_state.value is NetState.Starting) {
                TelosVpnService.current?.requestStop(StopReason.Failure, "timeout")
                if (_state.value is NetState.Starting) fail(StopReason.Failure, "timeout")
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

    /** Turns the VPN off. Safe to call in any state. The internet works normally again right after. */
    fun stop() {
        settings.update { it.copy(engineWanted = false) }
        settings.flush()
        val service = TelosVpnService.current
        if (service != null) {
            service.requestStop(StopReason.User, null)
        } else {
            watchdog?.cancel()
            _tunnel.value = null
            _state.value = NetState.Off
        }
    }

    /** Stops the VPN and starts it again, for example to apply changes that need a new interface. */
    fun restart() {
        scope.launch {
            stop()
            withTimeoutOrNull(RESTART_WAIT_MS) { _state.first { it is NetState.Off } }
            start()
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
    }

    internal fun onServiceStopped(reason: StopReason, detail: String?) {
        watchdog?.cancel()
        _tunnel.value = null
        scope.launch {
            components.forEach { c ->
                try {
                    c.onTunnelDisconnected()
                } catch (e: Throwable) {
                    // a broken component must not keep the others from cleaning up
                }
            }
        }
        when (reason) {
            StopReason.User -> _state.value = NetState.Off
            StopReason.Failure, StopReason.Revoked -> {
                settings.update { it.copy(engineWanted = false) }
                settings.flush()
                _state.value = NetState.Error(messageFor(reason, detail))
            }
        }
    }

    /** Hands the tunnel to the components so they can apply their configuration. */
    internal suspend fun applyComponents(tunnel: Tunnel) {
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
