package de.mm20.launcher2.comms.youtube

import android.app.PendingIntent
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import de.mm20.launcher2.applock.SettingsDeepLinkContract
import de.mm20.launcher2.comms.media.PlaybackCoordinator

/** One shared audio/video session; playback continues after leaving the screen. */
class YouTubePlayerService : MediaSessionService() {
    private var session: MediaSession? = null
    private val handler = Handler(Looper.getMainLooper())
    private val idleStop = Runnable { if (session?.player?.playWhenReady != true) pauseAllPlayersAndStopSelf() }
    override fun onCreate() {
        super.onCreate()
        val default = DefaultMediaSourceFactory(DefaultDataSource.Factory(this,
            DefaultHttpDataSource.Factory().setUserAgent("Mozilla/5.0").setAllowCrossProtocolRedirects(true)))
        val sources = object : MediaSource.Factory by default {
            override fun createMediaSource(item: MediaItem): MediaSource {
                val video = default.createMediaSource(item)
                val audio = item.mediaMetadata.extras?.getString(EXTRA_AUDIO_URL) ?: return video
                return MergingMediaSource(video, default.createMediaSource(MediaItem.fromUri(audio)))
            }
        }
        val player = ExoPlayer.Builder(this).setMediaSourceFactory(sources)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            .setHandleAudioBecomingNoisy(true).setWakeMode(C.WAKE_MODE_NETWORK).build()
        player.addListener(object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                handler.removeCallbacks(idleStop)
                if (!playWhenReady) handler.postDelayed(idleStop, 5 * 60 * 1000L)
                if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS)
                    PlaybackCoordinator.onFocusLoss(this@YouTubePlayerService, KIND)
            }
        })
        PlaybackCoordinator.register(KIND, PlaybackCoordinator.Participant({ player.playWhenReady }, { player.pause() }, { player.play() }))
        val open = Intent().setClassName(packageName, SettingsDeepLinkContract.ACTIVITY_CLASS_NAME)
            .putExtra(SettingsDeepLinkContract.EXTRA_ROUTE, SettingsDeepLinkContract.ROUTE_MEDIA)
            .putExtra(SettingsDeepLinkContract.EXTRA_MEDIA_SPACE, "youtube")
        session = MediaSession.Builder(this, player).setId("telos_youtube")
            .setSessionActivity(PendingIntent.getActivity(this, 2301, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)).build()
        handler.postDelayed(idleStop, 5 * 60 * 1000L)
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        PlaybackCoordinator.unregister(KIND)
        session?.run { player.release(); release() }; session = null
        super.onDestroy()
    }
    companion object { const val EXTRA_AUDIO_URL = "telos.youtube.audio_url"; const val KIND = "youtube" }
}
