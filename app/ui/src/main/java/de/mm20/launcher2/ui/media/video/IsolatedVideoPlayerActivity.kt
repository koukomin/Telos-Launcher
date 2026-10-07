package de.mm20.launcher2.ui.media.video

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * The video player in its own process (see the manifest). That process does not start the
 * launcher, so a crash in a decoder or in the torrent engine ends only the player.
 */
class IsolatedVideoPlayerActivity : VideoPlayerActivity() {
    override val isolated: Boolean get() = true

    @Composable
    override fun Themed(content: @Composable () -> Unit) {
        // the player draws its own black screen and white controls; the launcher theme needs the launcher
        MaterialTheme(colorScheme = darkColorScheme(), content = content)
    }
}
