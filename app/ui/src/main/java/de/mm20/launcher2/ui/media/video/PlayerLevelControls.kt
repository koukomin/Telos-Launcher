package de.mm20.launcher2.ui.media.video

import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import android.view.Window
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.ui.R
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Shared controls for activity-backed videos and the TV dialog's own window. */
@Composable
internal fun PlayerLevelControls(
    visible: Boolean,
    window: Window?,
    modifier: Modifier = Modifier,
    onAdjusting: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val audio = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    fun volume(): Float {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        return if (max > 0) audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max else 0f
    }
    fun brightness(): Float = window?.attributes?.screenBrightness?.takeIf { it >= 0f }
        ?: (Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f)
    var light by remember(window) { mutableFloatStateOf(brightness().coerceIn(0.02f, 1f)) }
    var sound by remember(audio) { mutableFloatStateOf(volume()) }
    DisposableEffect(window) {
        val original = window?.attributes?.screenBrightness
        onDispose {
            if (window != null && original != null) {
                window.attributes = window.attributes.apply { screenBrightness = original }
            }
            onAdjusting(false)
        }
    }
    // Physical volume keys and existing video gestures share the same state as these sliders.
    LaunchedEffect(visible, window) {
        while (visible) {
            sound = volume()
            light = brightness().coerceIn(0.02f, 1f)
            delay(300)
        }
    }
    AnimatedVisibility(visible, modifier, enter = fadeIn(), exit = fadeOut()) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 8.dp, vertical = 72.dp)) {
            val railHeight = (maxHeight - 72.dp).coerceIn(48.dp, 168.dp)
            LevelRail(light, R.drawable.light_mode_24px,
                stringResource(R.string.au_video_brightness, (light * 100).roundToInt()),
                window != null, railHeight, Modifier.align(Alignment.CenterStart), onAdjusting) { value ->
                window?.let { it.attributes = it.attributes.apply { screenBrightness = value.coerceIn(0.02f, 1f) } }
                light = brightness().coerceIn(0.02f, 1f)
            }
            LevelRail(sound, if (sound == 0f) R.drawable.volume_off_24px else R.drawable.volume_up_24px,
                stringResource(R.string.au_video_volume, (sound * 100).roundToInt()),
                !audio.isVolumeFixed, railHeight, Modifier.align(Alignment.CenterEnd), onAdjusting) { value ->
                val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, (value * max).roundToInt().coerceIn(0, max), 0) }
                sound = volume()
            }
        }
    }
}

@Composable
private fun LevelRail(
    value: Float, icon: Int, label: String, enabled: Boolean,
    height: androidx.compose.ui.unit.Dp, modifier: Modifier,
    onAdjusting: (Boolean) -> Unit, onValueChange: (Float) -> Unit,
) {
    val interactions = remember { MutableInteractionSource() }
    val dragged by interactions.collectIsDraggedAsState()
    LaunchedEffect(dragged) { onAdjusting(dragged) }
    Column(modifier.width(48.dp).background(Color(0xB31A1A1A), RoundedCornerShape(24.dp))
        .padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(painterResource(icon), contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        Box(Modifier.width(48.dp).height(height), contentAlignment = Alignment.Center) {
            Slider(value = value, onValueChange = { onAdjusting(true); onValueChange(it) },
                onValueChangeFinished = { onAdjusting(false) }, enabled = enabled, interactionSource = interactions,
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.25f)),
                modifier = Modifier.wrapContentSize(unbounded = true).requiredWidth(height)
                    .graphicsLayer { rotationZ = -90f }.semantics { contentDescription = label })
        }
        Text("${(value * 100).roundToInt()}%", color = Color.White,
            style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}
