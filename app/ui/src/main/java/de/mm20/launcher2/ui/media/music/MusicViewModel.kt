package de.mm20.launcher2.ui.media.music

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import de.mm20.launcher2.comms.media.MusicLibrary
import de.mm20.launcher2.comms.media.MusicPlayerService
import de.mm20.launcher2.comms.media.MusicSleepTimer
import de.mm20.launcher2.comms.media.MusicTrack
import de.mm20.launcher2.comms.media.TagEditor
import de.mm20.launcher2.ui.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import de.mm20.launcher2.comms.media.LyricsClient
import de.mm20.launcher2.comms.media.Lyrics
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class NowPlaying(
    val uri: Uri?,
    val title: String,
    val artist: String,
    val artUri: Uri?,
    val durationMs: Long,
)

class MusicViewModel : ViewModel() {

    private var controller: MediaController? = null
    private var appContext: Context? = null
    private var lyricsJob: Job? = null
    private var lyricsKey = ""

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    val sleepEndsAt: StateFlow<Long> = MusicSleepTimer.endsAt

    fun setSleepTimer(minutes: Int) = MusicSleepTimer.start(minutes)

    fun consumeMessage() {
        _message.value = null
    }

    suspend fun readTags(context: Context, uri: Uri): TagEditor.Tags? =
        withContext(Dispatchers.IO) { TagEditor.read(context, uri) }

    fun saveTags(context: Context, uri: Uri, tags: TagEditor.Tags) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { TagEditor.write(context, uri, tags) }
            _message.value = if (ok) context.getString(R.string.au_music_tags_saved) else context.getString(R.string.au_music_tags_failed)
            if (ok) loadLibrary(context)
        }
    }

    fun saveCover(context: Context, uri: Uri, image: Uri) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                val bytes = context.contentResolver.openInputStream(image)?.use { it.readBytes() }
                val mime = context.contentResolver.getType(image) ?: "image/jpeg"
                bytes != null && TagEditor.writeCover(context, uri, bytes, mime)
            }
            _message.value = if (ok) context.getString(R.string.au_music_cover_saved) else context.getString(R.string.au_music_cover_failed)
            if (ok) loadLibrary(context)
        }
    }

    private val _lyrics = MutableStateFlow<Lyrics?>(null)
    val lyrics: StateFlow<Lyrics?> = _lyrics

    private val _tracks = MutableStateFlow<List<MusicTrack>>(emptyList())
    val tracks: StateFlow<List<MusicTrack>> = _tracks

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs

    private val _shuffle = MutableStateFlow(false)
    val shuffle: StateFlow<Boolean> = _shuffle

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode

    private var connecting = false
    private var cleared = false
    private var future: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null
    private var positionJob: Job? = null
    private var pendingPlay: Pair<List<MusicTrack>, Int>? = null

    fun connect(context: Context) {
        if (controller != null || connecting) return
        connecting = true
        appContext = context.applicationContext
        val token = SessionToken(context, ComponentName(context, MusicPlayerService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        this.future = future
        future.addListener({
            connecting = false
            val c = runCatching { future.get() }.getOrNull() ?: return@addListener
            if (cleared) {
                // the screen was left while connecting: nobody else would release this controller
                c.release()
                return@addListener
            }
            controller = c
            c.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _isPlaying.value = isPlaying
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = refresh()
                override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) = refresh()
                override fun onPlaybackStateChanged(playbackState: Int) = refresh()
                override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                    _shuffle.value = shuffleModeEnabled
                }

                override fun onRepeatModeChanged(repeatMode: Int) {
                    _repeatMode.value = repeatMode
                }
            })
            _isPlaying.value = c.isPlaying
            _shuffle.value = c.shuffleModeEnabled
            _repeatMode.value = c.repeatMode
            refresh()
            pendingPlay?.let { (l, i) -> pendingPlay = null; play(l, i) }
        }, MoreExecutors.directExecutor())

        // one loop for the whole life of the screen (connect() runs again after a failed connection),
        // and slow while nothing plays: a seek or a new track updates the position by itself
        if (positionJob == null) positionJob = viewModelScope.launch {
            while (true) {
                controller?.let { _positionMs.value = it.currentPosition.coerceAtLeast(0L) }
                delay(if (_isPlaying.value) 500 else 2000)
            }
        }
    }

    private fun refresh() {
        val c = controller ?: return
        val item = c.currentMediaItem
        if (item == null) {
            _nowPlaying.value = null
            _lyrics.value = null
            lyricsKey = ""
            return
        }
        val md = c.mediaMetadata
        _positionMs.value = c.currentPosition.coerceAtLeast(0L)
        val playing = NowPlaying(
            uri = item.localConfiguration?.uri,
            title = md.title?.toString().orEmpty(),
            artist = md.artist?.toString().orEmpty(),
            artUri = md.artworkUri,
            durationMs = c.duration.takeIf { it > 0 } ?: 0L,
        )
        _nowPlaying.value = playing
        loadLyrics(playing, md.albumTitle?.toString().orEmpty())
    }

    private fun loadLyrics(playing: NowPlaying, album: String) {
        val context = appContext ?: return
        val key = playing.artist + "|" + playing.title
        if (key == lyricsKey) return
        lyricsKey = key
        lyricsJob?.cancel()
        _lyrics.value = null
        lyricsJob = viewModelScope.launch {
            _lyrics.value = LyricsClient.fetch(context, playing.artist, playing.title, album, playing.durationMs)
        }
    }

    fun loadLibrary(context: Context) {
        viewModelScope.launch {
            _loading.value = true
            try {
                _tracks.value = MusicLibrary.load(context)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // e.g. the audio permission was revoked: show an empty library instead of crashing
                _tracks.value = emptyList()
            } finally {
                _loading.value = false
            }
        }
    }

    /** Plays [list] as the queue, starting with the track at [index] */
    fun play(list: List<MusicTrack>, index: Int) {
        val c = controller ?: run { pendingPlay = list to index; return }
        val items = list.map { track ->
            MediaItem.Builder()
                .setUri(track.uri)
                .setMediaId(track.id.toString())
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(track.artist)
                        .setAlbumTitle(track.album)
                        .setArtworkUri(track.uri) // the notification loads the cover of the file (see AlbumArtBitmapLoader)
                        .build()
                )
                .build()
        }
        c.setMediaItems(items, index.coerceIn(0, (items.size - 1).coerceAtLeast(0)), 0L)
        c.prepare()
        c.play()
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() {
        controller?.seekToNextMediaItem()
    }

    fun previous() {
        val c = controller ?: return
        if (c.currentPosition > 3000) c.seekTo(0) else c.seekToPreviousMediaItem()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
        _positionMs.value = positionMs
    }

    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
    }

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun stop() {
        controller?.stop()
        controller?.clearMediaItems()
        _nowPlaying.value = null
    }

    override fun onCleared() {
        super.onCleared()
        cleared = true
        controller?.release()
        controller = null
        if (connecting) future?.let { MediaController.releaseFuture(it) }
    }
}
