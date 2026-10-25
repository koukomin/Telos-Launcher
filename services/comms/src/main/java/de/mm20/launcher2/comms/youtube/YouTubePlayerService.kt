package de.mm20.launcher2.comms.youtube

import android.app.PendingIntent
import android.content.Intent
import android.os.Handler
import android.os.Bundle
import android.net.Uri
import androidx.media3.common.MediaMetadata
import kotlinx.coroutines.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
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
class YouTubePlayerService : MediaSessionService(), KoinComponent {
    private val repository: YouTubeRepository by inject()
    private val library: YouTubeLibrary by inject()
    private val playbackScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var advance: Job? = null
    private var session: MediaSession? = null
    private val handler = Handler(Looper.getMainLooper())
    private val idleStop = Runnable { if (session?.player?.playWhenReady != true) pauseAllPlayersAndStopSelf() }
    override fun onCreate() {
        super.onCreate()
        setMediaNotificationProvider(androidx.media3.session.DefaultMediaNotificationProvider.Builder(this)
            .setNotificationId(de.mm20.launcher2.comms.media.MediaPlaybackNotifications.YOUTUBE).build().apply { setSmallIcon(de.mm20.launcher2.search.VirtualAppAppearance.glyph("telos_youtube_app")!!) })
        val default = DefaultMediaSourceFactory(DefaultDataSource.Factory(this,
            DefaultHttpDataSource.Factory().setUserAgent("Mozilla/5.0").setAllowCrossProtocolRedirects(true)))
        val sources = object : MediaSource.Factory by default {
            override fun createMediaSource(item: MediaItem): MediaSource {
                val video = default.createMediaSource(item)
                val audio = item.mediaMetadata.extras?.getString(EXTRA_AUDIO_URL) ?: return video
                return MergingMediaSource(video, default.createMediaSource(MediaItem.fromUri(audio)))
            }
        }
        val player = ExoPlayer.Builder(this, de.mm20.launcher2.comms.media.MediaDecoderPack.renderersFactory(this)).setMediaSourceFactory(sources)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            .setHandleAudioBecomingNoisy(true).setWakeMode(C.WAKE_MODE_NETWORK).build()
        player.addListener(object : Player.Listener {
            private var effectSession = -1
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                de.mm20.launcher2.comms.media.MediaAudioFx.detachFromSession(effectSession)
                effectSession = audioSessionId
                de.mm20.launcher2.comms.media.MediaAudioFx.attachToSession(audioSessionId)
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { advance?.cancel() }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState != Player.STATE_ENDED) return
                val old = player.currentMediaItem ?: return
                val extras = old.mediaMetadata.extras ?: return
                val queue = YouTubeQueue.decode(extras.getString(EXTRA_QUEUE))
                val index = extras.getInt(EXTRA_QUEUE_INDEX, -1) + 1
                val entry = queue.getOrNull(index) ?: return
                advance?.cancel()
                advance = playbackScope.launch {
                    try {
                        val music = extras.getBoolean("audioOnly")
                        val next = repository.resolve(entry, music)
                        if (player.currentMediaItem !== old || player.playbackState != Player.STATE_ENDED) return@launch
                        val nextExtras = Bundle(extras).apply {
                            putInt(EXTRA_QUEUE_INDEX, index)
                            remove(EXTRA_AUDIO_URL)
                            next.audioUrl?.let { putString(EXTRA_AUDIO_URL, it) }
                        }
                        val item = MediaItem.Builder().setMediaId(entry.url).setUri(next.streamUrl)
                            .setMediaMetadata(MediaMetadata.Builder().setTitle(next.entry.title).setArtist(next.entry.artist)
                                .setArtworkUri(next.entry.thumbnail?.let(Uri::parse)).setExtras(nextExtras).build()).build()
                        library.remember(next.entry, music)
                        // The transition callback cancels the previous resolver; assignment is already complete.
                        advance = null
                        player.setMediaItem(item); player.prepare(); player.play()
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { player.pause() }
                }
            }
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
        playbackScope.cancel()
        handler.removeCallbacksAndMessages(null)
        PlaybackCoordinator.unregister(KIND)
        session?.run { de.mm20.launcher2.comms.media.MediaAudioFx.detachFromSession((player as? ExoPlayer)?.audioSessionId ?: -1); player.release(); release() }; session = null
        super.onDestroy()
    }
    companion object { const val EXTRA_QUEUE = "telos.youtube.queue"; const val EXTRA_QUEUE_INDEX = "telos.youtube.queue_index"; const val EXTRA_AUDIO_URL = "telos.youtube.audio_url"; const val KIND = "youtube" }
}
