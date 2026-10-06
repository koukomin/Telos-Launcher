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
import de.mm20.launcher2.ui.base.BaseActivity
import de.mm20.launcher2.ui.base.ProvideCompositionLocals
import de.mm20.launcher2.ui.theme.LauncherTheme

/** Full screen video player. Also opens when another app shares or opens a video with Telos. */
class VideoPlayerActivity : BaseActivity() {

    private var isPlayingState = false
    private var inPictureInPicture by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        val uris: List<Uri>
        val titles: List<String>
        val startIndex: Int
        val extraUris = intent.getStringArrayListExtra(EXTRA_URIS)
        if (extraUris != null && extraUris.isNotEmpty()) {
            uris = extraUris.map { Uri.parse(it) }
            titles = intent.getStringArrayListExtra(EXTRA_TITLES) ?: uris.map { it.lastPathSegment.orEmpty() }
            startIndex = intent.getIntExtra(EXTRA_INDEX, 0).coerceIn(0, uris.size - 1)
        } else {
            val data = intent.data
            if (data == null) {
                finish()
                return
            }
            uris = listOf(data)
            titles = listOf(data.lastPathSegment.orEmpty().substringAfterLast('/').substringBeforeLast('.'))
            startIndex = 0
        }

        setContent {
            ProvideCompositionLocals {
                LauncherTheme {
                    VideoPlayerScreen(
                        uris = uris,
                        titles = titles,
                        startIndex = startIndex,
                        onClose = { finish() },
                        onPlayingChanged = { isPlayingState = it },
                        inPictureInPicture = inPictureInPicture,
                    )
                }
            }
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
    }
}
