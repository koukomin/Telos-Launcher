package de.mm20.launcher2.comms.tv

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import de.mm20.launcher2.applock.SettingsDeepLinkContract
import de.mm20.launcher2.base.containedScope
import de.mm20.launcher2.i18n.R as I18nR
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Foreground media service that keeps Telos TV playing with the screen off or after the app was left
 * ("Keep playing in the background", [TvSettings.keepPlayingInBackground]). It does not own the player:
 * [TvPlayerController] does, and this service only wraps the controller's ExoPlayer in a MediaSession
 * (Media3 then shows the notification with play / pause / previous / next, plus a stop button, and
 * answers lock screen and Bluetooth controls). Controls are routed to the controller, so the failover
 * logic and the audio focus rules (music and radio pause when TV starts, and vice versa) stay in one place.
 *
 * The service ends when the setting is switched off, when TV is stopped (stop button or in the app),
 * and after [TvBackgroundPolicy.IDLE_STOP_MS] without playback (it then stops TV).
 */
class TvPlaybackService : MediaSessionService(), KoinComponent {
    private val controller: TvPlayerController by inject()
    private val settings: TvSettings by inject()
    private val scope = containedScope(Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())

    private var session: MediaSession? = null
    private var wrapped: Player? = null
    private var idleSince = -1L

    private val idleCheck = object : Runnable {
        override fun run() {
            val now = SystemClock.elapsedRealtime()
            if (TvBackgroundPolicy.shouldStopAfterIdle(
                    controller.isPlaying.value, controller.isBuffering.value, idleSince, now,
                    reconnecting = controller.reconnecting.value,
                )
            ) {
                controller.stop()
                return
            }
            if (idleSince >= 0) handler.postDelayed(this, IDLE_CHECK_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(CHANNEL_ID)
                .setChannelName(I18nR.string.au15_tvbg_channel_name)
                .build()
        )
        scope.launch {
            combine(controller.player, settings.keepPlayingInBackground) { p, keep -> if (keep) p else null }
                .collect { onPlayer(it) }
        }
        scope.launch {
            combine(controller.isPlaying, controller.isBuffering, controller.reconnecting) { playing, buffering, reconnecting ->
                playing || buffering || reconnecting
            }
                .collect { active ->
                    handler.removeCallbacks(idleCheck)
                    if (active) {
                        idleSince = -1L
                    } else {
                        idleSince = SystemClock.elapsedRealtime()
                        handler.postDelayed(idleCheck, IDLE_CHECK_MS)
                    }
                }
        }
    }

    private fun onPlayer(p: Player?) {
        if (p === wrapped) return
        releaseSession()
        if (p == null) {
            stopSelf()
            return
        }
        wrapped = p
        val s = runCatching {
            MediaSession.Builder(this, ControllerPlayer(p))
                .setId("telos_tv") // Media3 throws "Session ID must be unique" when music/radio use the default id
                .setCallback(SessionCallback())
                .setSessionActivity(openTvIntent())
                .setCustomLayout(ImmutableList.of(stopButton()))
                .build()
        }.getOrNull()
        if (s == null) {
            wrapped = null
            stopSelf()
            return
        }
        session = s
        runCatching { addSession(s) }
    }

    private fun releaseSession() {
        session?.let {
            removeSession(it)
            // the ExoPlayer belongs to the controller: only the session and its wrapper are released
            it.release()
        }
        session = null
        wrapped = null
    }

    private fun openTvIntent(): PendingIntent {
        val intent = Intent().apply {
            setClassName(packageName, SettingsDeepLinkContract.ACTIVITY_CLASS_NAME)
            putExtra(SettingsDeepLinkContract.EXTRA_ROUTE, SettingsDeepLinkContract.ROUTE_MEDIA)
            putExtra(SettingsDeepLinkContract.EXTRA_MEDIA_SPACE, "tv")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    @Suppress("DEPRECATION")
    private fun stopButton(): CommandButton = CommandButton.Builder()
        .setDisplayName(getString(I18nR.string.au15_tvbg_stop))
        .setIconResId(android.R.drawable.ic_menu_close_clear_cancel)
        .setSessionCommand(SessionCommand(ACTION_STOP, Bundle.EMPTY))
        .build()

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    private inner class SessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            // only the system (lock screen, Bluetooth, headset) and this app may control TV
            if (!controller.isTrusted && controller.packageName != packageName) {
                return MediaSession.ConnectionResult.reject()
            }
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand(ACTION_STOP, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == ACTION_STOP) this@TvPlaybackService.controller.stop()
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    /** Routes the session's controls (notification, lock screen, headset) to [TvPlayerController] */
    private inner class ControllerPlayer(exo: Player) : ForwardingPlayer(exo) {
        override fun getAvailableCommands(): Player.Commands = super.getAvailableCommands().buildUpon()
            .addAll(
                Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            )
            // a live channel cannot be scrubbed
            .removeAll(
                Player.COMMAND_SEEK_BACK, Player.COMMAND_SEEK_FORWARD,
                Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
            )
            .build()

        override fun isCommandAvailable(command: Int): Boolean = availableCommands.contains(command)
        override fun hasNextMediaItem(): Boolean = true
        override fun hasPreviousMediaItem(): Boolean = true
        override fun play() = this@TvPlaybackService.controller.resume()
        override fun pause() = this@TvPlaybackService.controller.pause()
        override fun stop() = this@TvPlaybackService.controller.stop()
        override fun seekToNext() { this@TvPlaybackService.controller.next() }
        override fun seekToNextMediaItem() { this@TvPlaybackService.controller.next() }
        override fun seekToPrevious() { this@TvPlaybackService.controller.previous() }
        override fun seekToPreviousMediaItem() { this@TvPlaybackService.controller.previous() }
    }

    override fun onDestroy() {
        handler.removeCallbacks(idleCheck)
        releaseSession()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "telos_tv_playback"
        private const val ACTION_STOP = "de.mm20.launcher2.comms.tv.STOP"
        private const val IDLE_CHECK_MS = 15_000L
    }
}
