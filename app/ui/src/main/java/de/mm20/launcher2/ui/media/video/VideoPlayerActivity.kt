package de.mm20.launcher2.ui.media.video

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import de.mm20.launcher2.comms.media.video.torrent.TorrentStreamer
import androidx.appcompat.app.AppCompatActivity
import de.mm20.launcher2.ui.base.ProvideCompositionLocals
import de.mm20.launcher2.ui.theme.LauncherTheme

/** Full screen video player. Also opens when another app shares or opens a video with Telos. */
open class VideoPlayerActivity : AppCompatActivity() {

    private var isPlayingState = false
    private var inPictureInPicture by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            // the picture runs into the camera cutout in landscape instead of leaving a black bar
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        val uris: List<Uri>
        val titles: List<String>
        val startIndex: Int
        var torrentSource: String? = null
        val extraUris = intent.getStringArrayListExtra(EXTRA_URIS)
        // a magnet link or address shared as text from a browser or another app
        val shared = if (intent.action == android.content.Intent.ACTION_SEND) {
            intent.getStringExtra(android.content.Intent.EXTRA_TEXT)?.trim()?.lineSequence()?.firstOrNull { l ->
                l.startsWith("magnet:", true) || l.startsWith("http", true)
            }
        } else null
        val typed = intent.getStringExtra(EXTRA_SOURCE) ?: shared
        val viewed = intent.data?.toString()
        val torrentCandidate = typed ?: viewed
        if (torrentCandidate != null &&
            (TorrentStreamer.isTorrent(torrentCandidate) || intent.type == "application/x-bittorrent" ||
                intent.getBooleanExtra(EXTRA_IS_TORRENT, false))
        ) {
            torrentSource = torrentCandidate
            uris = emptyList()
            titles = emptyList()
            startIndex = 0
        } else if (typed != null) {
            // a web address typed or pasted by the user
            val uri = Uri.parse(typed)
            uris = listOf(uri)
            titles = listOf(uri.lastPathSegment?.substringBeforeLast('.') ?: typed)
            startIndex = 0
        } else if (extraUris != null && extraUris.isNotEmpty()) {
            uris = extraUris.map { Uri.parse(it) }
            titles = intent.getStringArrayListExtra(EXTRA_TITLES) ?: uris.map { it.lastPathSegment.orEmpty() }
            startIndex = intent.getIntExtra(EXTRA_INDEX, 0).coerceIn(0, uris.size - 1)
        } else {
            // a video shared to Telos Video comes as a stream
            @Suppress("DEPRECATION")
            val data = intent.data ?: intent.getParcelableExtra<Uri>(android.content.Intent.EXTRA_STREAM)
            if (data == null) {
                finish()
                return
            }
            uris = listOf(data)
            titles = listOf(data.lastPathSegment.orEmpty().substringAfterLast('/').substringBeforeLast('.'))
            startIndex = 0
        }

        setContent {
            Themed {
                    VideoPlayerScreen(
                        uris = uris,
                        titles = titles,
                        startIndex = startIndex,
                        torrentSource = torrentSource,
                        onClose = { finish() },
                        onPlayingChanged = { isPlayingState = it },
                        inPictureInPicture = inPictureInPicture,
                    )
            }
        }
    }

    /** True in the activity that runs in the player process, which has no launcher around it */
    protected open val isolated: Boolean get() = false

    @androidx.compose.runtime.Composable
    protected open fun Themed(content: @androidx.compose.runtime.Composable () -> Unit) {
        ProvideCompositionLocals { LauncherTheme { content() } }
    }

    override fun onStart() {
        super.onStart()
        if (!isolated) de.mm20.launcher2.base.VirtualAppGuard.enter(this, "telos_video_app://video")
    }

    override fun onStop() {
        if (!isolated) de.mm20.launcher2.base.VirtualAppGuard.leave(this, "telos_video_app://video")
        super.onStop()
    }

    override fun onDestroy() {
        // stops a running torrent and deletes what it downloaded
        if (isolated) {
            // the process only exists for this player: clean up and end it, so the next start
            // reads fresh data (and nothing stale stays in memory)
            val finishing = isFinishing
            super.onDestroy()
            if (finishing) {
                Thread {
                    runCatching { TorrentStreamer.close() }
                    android.os.Process.killProcess(android.os.Process.myPid())
                }.start()
            }
            return
        }
        TorrentStreamer.release(this)
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // the bars come back after a dialog, the permission prompt or the picture-in-picture window
        if (hasFocus) {
            WindowInsetsControllerCompat(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isPlayingState) {
            runCatching {
                enterPictureInPictureMode(
                    PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()
                )
            }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPictureInPicture = isInPictureInPictureMode
    }

    companion object {
        const val EXTRA_URIS = "de.mm20.launcher2.video.URIS"
        const val EXTRA_TITLES = "de.mm20.launcher2.video.TITLES"
        const val EXTRA_INDEX = "de.mm20.launcher2.video.INDEX"
        /** A web address, magnet link or .torrent address typed by the user */
        const val EXTRA_SOURCE = "de.mm20.launcher2.video.SOURCE"
        const val EXTRA_IS_TORRENT = "de.mm20.launcher2.video.IS_TORRENT"
    }
}
