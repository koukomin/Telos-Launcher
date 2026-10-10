package de.mm20.launcher2.comms.media

import androidx.media3.common.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import org.json.JSONArray
import org.json.JSONObject
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/** Plays the local music library. Media3 takes care of the notification and lock screen controls. */
class MusicPlayerService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var scrobbler: de.mm20.launcher2.comms.scrobble.ScrobbleTracker? = null
    private val handler = Handler(Looper.getMainLooper())

    // Nothing keeps the service (and the decoder) alive after music has been paused for a while
    private val idleStop: Runnable = Runnable {
        val p = mediaSession?.player
        if (de.mm20.launcher2.comms.media.PlaybackCoordinator.isWaiting(this, PlaybackCoordinator.KIND_MUSIC)) {
            handler.postDelayed(idleStop, IDLE_STOP_MS) // a video paused the music, it may continue
        } else if (p?.isPlaying != true && p?.playWhenReady != true) pauseAllPlayersAndStopSelf()
    }

    override fun onCreate() {
        super.onCreate()
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            // keeps the CPU (and for a stream the Wi-Fi) awake while playing with the screen off
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                handler.removeCallbacks(idleStop)
                if (!isPlaying) {
                    handler.postDelayed(idleStop, IDLE_STOP_MS)
                    saveState(player) // position is only written when playback stops
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS) {
                    PlaybackCoordinator.onFocusLoss(this@MusicPlayerService, PlaybackCoordinator.KIND_MUSIC)
                }
            }

            override fun onEvents(player: Player, events: Player.Events) {
                if (events.containsAny(
                        Player.EVENT_TIMELINE_CHANGED,
                        Player.EVENT_MEDIA_ITEM_TRANSITION,
                        Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                        Player.EVENT_REPEAT_MODE_CHANGED,
                    )
                ) saveState(player)
            }
        })
        restoreState(player)
        PlaybackCoordinator.register(
            PlaybackCoordinator.KIND_MUSIC,
            PlaybackCoordinator.Participant(
                isPlaying = { player.isPlaying || player.playWhenReady },
                pause = { player.pause() },
                resume = { if (player.mediaItemCount > 0) player.play() },
            )
        )
        handler.postDelayed(idleStop, IDLE_STOP_MS)
        mediaSession = MediaSession.Builder(this, player)
            .setId("telos_music") // Media3 throws "Session ID must be unique" when radio (default id) is alive in the same process
            .setBitmapLoader(AlbumArtBitmapLoader(this))
            .build()
        MusicSleepTimer.onExpire = { player.pause() }
        scrobbler = de.mm20.launcher2.comms.scrobble.ScrobbleTracker(this, player).also { it.attach() }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    // ---- queue, shuffle and repeat survive the service being stopped ----

    private fun saveState(player: Player) {
        runCatching {
            val prefs = getSharedPreferences(STATE_PREFS, MODE_PRIVATE)
            val count = player.mediaItemCount
            if (count == 0) {
                prefs.edit().clear().apply()
                return
            }
            val items = JSONArray()
            for (i in 0 until minOf(count, MAX_SAVED_ITEMS)) {
                val item = player.getMediaItemAt(i)
                val uri = item.localConfiguration?.uri ?: continue
                items.put(
                    JSONObject()
                        .put("id", item.mediaId)
                        .put("uri", uri.toString())
                        .put("title", item.mediaMetadata.title?.toString().orEmpty())
                        .put("artist", item.mediaMetadata.artist?.toString().orEmpty())
                        .put("album", item.mediaMetadata.albumTitle?.toString().orEmpty())
                        .put("art", item.mediaMetadata.artworkUri?.toString().orEmpty())
                )
            }
            prefs.edit()
                .putString("items", items.toString())
                .putInt("index", player.currentMediaItemIndex)
                .putLong("position", player.currentPosition.coerceAtLeast(0L))
                .putBoolean("shuffle", player.shuffleModeEnabled)
                .putInt("repeat", player.repeatMode)
                .apply()
        }
    }

    private fun restoreState(player: Player) {
        runCatching {
            val prefs = getSharedPreferences(STATE_PREFS, MODE_PRIVATE)
            val array = JSONArray(prefs.getString("items", null) ?: return)
            val items = (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                val uri = o.optString("uri").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                MediaItem.Builder()
                    .setUri(Uri.parse(uri))
                    .setMediaId(o.optString("id"))
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(o.optString("title"))
                            .setArtist(o.optString("artist"))
                            .setAlbumTitle(o.optString("album"))
                            .setArtworkUri(o.optString("art").takeIf { it.isNotBlank() }?.let { Uri.parse(it) })
                            .build()
                    )
                    .build()
            }
            if (items.isEmpty()) return
            player.shuffleModeEnabled = prefs.getBoolean("shuffle", false)
            player.repeatMode = prefs.getInt("repeat", Player.REPEAT_MODE_OFF)
            val index = prefs.getInt("index", 0).coerceIn(0, items.size - 1)
            // paused: the person decides when the music starts again
            player.setMediaItems(items, index, prefs.getLong("position", 0L))
            player.prepare()
        }
    }

    override fun onDestroy() {
        PlaybackCoordinator.unregister(PlaybackCoordinator.KIND_MUSIC)
        mediaSession?.player?.let { saveState(it) }
        handler.removeCallbacks(idleStop)
        scrobbler?.release()
        scrobbler = null
        MusicSleepTimer.onExpire = null
        MusicSleepTimer.cancel() // no timer without a player to stop
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    private companion object {
        const val IDLE_STOP_MS = 5 * 60 * 1000L
        const val STATE_PREFS = "music_player_state"
        const val MAX_SAVED_ITEMS = 1000
    }
}
