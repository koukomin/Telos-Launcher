package de.mm20.launcher2.ui.desktopmode

import android.media.AudioManager
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.core.content.getSystemService
import de.mm20.launcher2.ui.R

/**
 * Volume tray icon + popup slider. Deliberately a plain horizontal Slider rather than a
 * vertical one: the common Compose trick for a vertical slider (rotating a horizontal Slider
 * 90deg with a graphicsLayer) doesn't rotate its drag-gesture coordinate handling along with the
 * rendering, which is exactly the kind of subtle interaction bug that's unverifiable without a
 * real touchscreen to test dragging on.
 */
@Composable
internal fun DesktopVolumeTrayIcon() {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService<AudioManager>() }
    val maxVolume = remember { audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 1 }
    var volume by remember {
        mutableFloatStateOf((audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0).toFloat())
    }
    var showSlider by remember { mutableStateOf(false) }

    DesktopTrayIconButton(
        icon = if (volume <= 0f) R.drawable.volume_off_24px else R.drawable.volume_up_24px,
        contentDescription = stringResource(R.string.desktop_mode_tray_volume),
        active = showSlider,
        onClick = { showSlider = !showSlider },
    )

    if (showSlider) {
        Popup(
            onDismissRequest = { showSlider = false },
            properties = PopupProperties(focusable = true),
        ) {
            Surface(
                modifier = Modifier.padding(bottom = DesktopTaskbarHeight + 4.dp),
                shape = MaterialTheme.shapes.medium,
                tonalElevation = 4.dp,
                shadowElevation = 4.dp,
            ) {
                Slider(
                    value = volume,
                    valueRange = 0f..maxVolume.toFloat(),
                    steps = (maxVolume - 1).coerceAtLeast(0),
                    onValueChange = {
                        volume = it
                        audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, it.toInt(), 0)
                    },
                    modifier = Modifier
                        .width(200.dp)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }
}
