package de.mm20.launcher2.comms.media

import android.content.Context
import android.content.Intent
import android.util.AtomicFile
import android.view.KeyEvent
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File

/** Routes Android media-button play requests among the live Telos sessions in this process. */
@androidx.annotation.OptIn(UnstableApi::class)
object HeadsetPlayback {
    enum class Source(val enabledByDefault: Boolean) {
        Music(true), Radio(true), Tv(true), YouTube(true), YouTubeMusic(true), Video(false), Stremio(false),
    }

    data class Options(
        val enabled: Boolean = true,
        val sources: Set<Source> = Source.entries.filter { it.enabledByDefault }.toSet(),
    )

    private fun optionsFile(context: Context) =
        AtomicFile(File(context.filesDir, "headset_playback.json"))

    fun load(context: Context): Options = runCatching {
        val data = JSONObject(optionsFile(context).baseFile.readBytes().decodeToString())
        Options(data.optBoolean("enabled", true), Source.entries.filter {
            data.optBoolean(it.name, it.enabledByDefault)
        }.toSet())
    }.getOrDefault(Options())

    @Synchronized
    fun save(context: Context, options: Options) {
        val data = JSONObject().put("enabled", options.enabled)
        Source.entries.forEach { data.put(it.name, it in options.sources) }
        val file = optionsFile(context)
        val stream = file.startWrite()
        try {
            stream.write(data.toString().encodeToByteArray())
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
    }

    private data class Entry(
        val source: (Player) -> Source,
        val player: Player,
        val listener: Player.Listener,
        var played: Long,
    )

    private val sessions = linkedMapOf<MediaSession, Entry>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var sequence = 0L
    private val mediaKeys = Mutex()

    /** All session lifecycle operations and player calls run on the main looper. */
    fun register(session: MediaSession, source: Source) = register(session) { source }

    fun register(session: MediaSession, source: (Player) -> Source) {
        unregister(session)
        val player = session.player
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) sessions[session]?.played = ++sequence
            }
        }
        sessions[session] = Entry(source, player, listener, if (player.isPlaying) ++sequence else 0L)
        player.addListener(listener)
    }

    fun unregister(session: MediaSession) {
        sessions.remove(session)?.let { it.player.removeListener(it.listener) }
    }

    fun callback(context: Context) = object : MediaSession.Callback {
        override fun onMediaButtonEvent(
            session: MediaSession,
            controllerInfo: MediaSession.ControllerInfo,
            intent: Intent,
        ): Boolean = handle(context, session, controllerInfo, intent)
    }

    @Suppress("DEPRECATION")
    fun handle(
        context: Context,
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        intent: Intent,
    ): Boolean {
        if (!controller.isTrusted) return false
        val event = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT) ?: return false
        val toggle = event.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
            event.keyCode == KeyEvent.KEYCODE_HEADSETHOOK
        if (!toggle && event.keyCode != KeyEvent.KEYCODE_MEDIA_PLAY) return false
        val current = sessions[session] ?: return false
        // Keep Media3's normal button and skip handling for a player already playing.
        if (current.player.playWhenReady) return false
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount != 0) return true
        val app = context.applicationContext
        scope.launch {
            mediaKeys.withLock {
                val options = withContext(Dispatchers.IO) { load(app) }
                val origin = sessions[session] ?: return@withLock
                val active = sessions.values.maxByOrNull { if (it.player.playWhenReady) it.played else -1L }
                    ?.takeIf { it.player.playWhenReady }
                if (toggle && active != null) {
                    (if (options.enabled) active else origin).player.pause()
                } else {
                    val selected = if (!options.enabled) origin else if (active != null) null else
                        sessions.values.filter {
                            it.source(it.player) in options.sources && it.player.isCommandAvailable(Player.COMMAND_PLAY_PAUSE) &&
                                it.player.mediaItemCount > 0 &&
                                (it.played > 0L || it === origin)
                        }.maxByOrNull { it.played }
                    selected?.player?.let { player ->
                        if (player.playbackState == Player.STATE_IDLE && player.isCommandAvailable(Player.COMMAND_PREPARE)) {
                            player.prepare()
                        }
                        if (player.playbackState == Player.STATE_ENDED && player.isCommandAvailable(Player.COMMAND_SEEK_TO_DEFAULT_POSITION)) {
                            player.seekToDefaultPosition()
                        }
                        player.play()
                    }
                }
            }
        }
        return true
    }
}
