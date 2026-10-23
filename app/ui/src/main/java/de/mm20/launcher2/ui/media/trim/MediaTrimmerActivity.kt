package de.mm20.launcher2.ui.media.trim

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import de.mm20.launcher2.ui.base.BaseActivity
import de.mm20.launcher2.ui.theme.LauncherTheme

class MediaTrimmerActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uriStr = intent.getStringExtra(EXTRA_URI)
        val isVideo = intent.getBooleanExtra(EXTRA_IS_VIDEO, true)

        val uri = uriStr?.let { Uri.parse(it) }

        setContent {
            LauncherTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    if (uri != null) {
                        MediaTrimmerScreen(
                            inputUri = uri,
                            isVideo = isVideo,
                            onClose = { finish() },
                        )
                    } else {
                        finish()
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_URI = "extra_uri"
        const val EXTRA_IS_VIDEO = "extra_is_video"

        fun start(context: Context, uri: Uri, isVideo: Boolean) {
            val intent = Intent(context, MediaTrimmerActivity::class.java).apply {
                putExtra(EXTRA_URI, uri.toString())
                putExtra(EXTRA_IS_VIDEO, isVideo)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
        }
    }
}
