package de.mm20.launcher2.comms.media

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Keeps a network stream (radio, video) alive: when the connection is lost it tries again until
 * playback resumes or the user stops/pauses. No Koin; works in the isolated player process.
 *
 * While offline nothing is retried (the default network callback wakes the loop up when the network
 * is back); while online but failing the attempts follow the [ReconnectPolicy] backoff (1, 2, 4, 8, 15 s).
 * Must be created and used on the main thread. Call [release] before releasing the player.
 *
 * @param isLive true when a lost stream continues at the live edge (default position) instead of where it was
 */
class StreamReconnector(
    context: Context,
    private val player: Player,
    private val isLive: () -> Boolean = { player.isCurrentMediaItemLive || player.duration == androidx.media3.common.C.TIME_UNSET },
    private val policy: ReconnectPolicy = ReconnectPolicy(),
) : Player.Listener {

    private val appContext = context.applicationContext
    private val connectivity = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val handler = Handler(Looper.getMainLooper())

    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline

    private val _reconnecting = MutableStateFlow(false)
    /** True from the connection loss until the stream plays again (or the loop is cancelled) */
    val reconnecting: StateFlow<Boolean> = _reconnecting

    private val _waiting = MutableStateFlow(false)
    /** True while reconnecting and the device is offline */
    val waiting: StateFlow<Boolean> = _waiting

    private var released = false
    private var callbackRegistered = false
    private var loopMediaId: String? = null
    private var policyMediaId: String? = null

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            handler.post { onNetwork(true) }
        }

        override fun onLost(network: Network) {
            handler.post { onNetwork(false) }
        }
    }

    private val retry = Runnable {
        if (released || !_reconnecting.value) return@Runnable
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            cancel()
            return@Runnable
        }
        if (!_isOnline.value) {
            update()
            return@Runnable
        }
        if (isLive()) player.seekToDefaultPosition()
        player.prepare()
        player.play()
        // a connection that neither plays nor fails must not hang the loop
        handler.removeCallbacks(watchdog)
        handler.postDelayed(watchdog, WATCHDOG_MS)
    }

    private val watchdog = Runnable {
        if (released || !_reconnecting.value) return@Runnable
        if (player.playbackState == Player.STATE_READY) return@Runnable
        if (!_isOnline.value) { update(); return@Runnable }
        if (!policy.onError(ReconnectPolicy.Kind.NETWORK)) { cancel(); return@Runnable }
        player.stop()
        scheduleNext()
    }

    fun attach() {
        player.addListener(this)
    }

    /** The user stopped, or the station/video was switched: ends the loop without touching the player */
    fun cancel() {
        handler.removeCallbacks(retry)
        handler.removeCallbacks(watchdog)
        unregisterCallback()
        loopMediaId = null
        _reconnecting.value = false
        _waiting.value = false
    }

    fun release() {
        released = true
        cancel()
        player.removeListener(this)
    }

    private fun onNetwork(online: Boolean) {
        if (released) return
        val wasOnline = _isOnline.value
        _isOnline.value = online
        if (!_reconnecting.value) return
        if (online && !wasOnline) {
            // back online: try right away
            handler.removeCallbacks(retry)
            handler.removeCallbacks(watchdog)
            handler.post(retry)
        } else if (!online) {
            handler.removeCallbacks(retry)
            handler.removeCallbacks(watchdog)
        }
        update()
    }

    private fun update() {
        _waiting.value = _reconnecting.value && !_isOnline.value
    }

    private fun queryOnline(): Boolean {
        val cm = connectivity ?: return true
        return runCatching {
            cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        }.getOrDefault(true)
    }

    private fun registerCallback() {
        if (callbackRegistered) return
        val cm = connectivity ?: return
        callbackRegistered = runCatching { cm.registerDefaultNetworkCallback(callback); true }.getOrDefault(false)
    }

    private fun unregisterCallback() {
        if (!callbackRegistered) return
        callbackRegistered = false
        runCatching { connectivity?.unregisterNetworkCallback(callback) }
    }

    private fun scheduleNext() {
        handler.removeCallbacks(retry)
        handler.removeCallbacks(watchdog)
        val delay = policy.delayMs(_isOnline.value)
        if (delay != null) handler.postDelayed(retry, delay)
        update()
    }

    private fun onConnectionProblem(kind: ReconnectPolicy.Kind) {
        if (released || !player.playWhenReady || player.mediaItemCount == 0) return
        val id = player.currentMediaItem?.mediaId
        if (id != policyMediaId) {
            policy.reset()
            policyMediaId = id
        }
        if (!policy.onError(kind)) {
            // not a connection problem and tried often enough: the error is shown as before
            cancel()
            return
        }
        if (!_reconnecting.value) {
            loopMediaId = id
            _isOnline.value = queryOnline()
            registerCallback()
            _reconnecting.value = true
        }
        scheduleNext()
    }

    override fun onPlayerError(error: PlaybackException) {
        var status: Int? = null
        var io = false
        var cause: Throwable? = error
        var depth = 0
        while (cause != null && depth++ < 10) {
            if (cause is HttpDataSource.InvalidResponseCodeException) status = cause.responseCode
            if (cause is java.io.IOException) io = true
            cause = cause.cause
        }
        onConnectionProblem(ReconnectPolicy.classify(error.errorCode, status, io))
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        when (playbackState) {
            Player.STATE_READY -> if (player.playWhenReady) {
                policy.onReady()
                if (_reconnecting.value) cancel()
            }
            // a live stream that ends was cut by the server or the connection
            Player.STATE_ENDED -> if (!_reconnecting.value && isLive()) onConnectionProblem(ReconnectPolicy.Kind.NETWORK)
        }
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        // an explicit pause or stop by the user ends the loop (our own retries never change playWhenReady)
        if (!playWhenReady && _reconnecting.value) cancel()
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        if (_reconnecting.value && mediaItem?.mediaId != loopMediaId) cancel()
    }

    private companion object {
        const val WATCHDOG_MS = 20_000L
    }
}
