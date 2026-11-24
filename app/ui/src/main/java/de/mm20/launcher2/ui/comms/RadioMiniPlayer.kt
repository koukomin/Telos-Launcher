package de.mm20.launcher2.ui.comms
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import androidx.compose.ui.res.stringResource
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import android.text.format.Formatter
import android.widget.Toast
import de.mm20.launcher2.comms.radio.RadioRecorder
import de.mm20.launcher2.ui.component.TelosCyber
import de.mm20.launcher2.ui.component.telosTechSurface
import de.mm20.launcher2.ui.R

@Composable
fun RadioMiniPlayer(modifier: Modifier = Modifier) {
    val viewModel: RadioViewModel = viewModel()
    val context = LocalContext.current
    val isVisible by viewModel.isVisible.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val stationName by viewModel.stationName.collectAsStateWithLifecycle()
    val nowPlaying by viewModel.nowPlayingMetadata.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val reconnecting by de.mm20.launcher2.comms.radio.RadioPlayerService.reconnecting.collectAsStateWithLifecycle()
    val waitingForNetwork by de.mm20.launcher2.comms.radio.RadioPlayerService.waiting.collectAsStateWithLifecycle()
    val sleepEndsAt by viewModel.sleepEndsAt.collectAsStateWithLifecycle()
    val recording by viewModel.recordingState.collectAsStateWithLifecycle()
    val recState = recording as? RadioRecorder.State.Recording
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(sleepEndsAt, recState?.startedAt) {
        while (sleepEndsAt > 0L || recState != null) {
            now = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.initialize(context)
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = androidx.compose.ui.graphics.Color.Transparent,
            modifier = Modifier
                .fillMaxWidth()
                .telosTechSurface(
                    borderColor = if (isPlaying) TelosCyber.NeonGreen else TelosCyber.NeonCyan,
                    shape = RoundedCornerShape(18.dp),
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Radio Icon with Neon Tint
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isPlaying) TelosCyber.NeonGreen.copy(alpha = 0.16f) else MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(BaseR.drawable.music_note_24px),
                        contentDescription = stringResource(I18nR.string.hc_radio),
                        tint = if (isPlaying) TelosCyber.NeonGreen else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                // Metadata Column with Cyber-Tech Telemetry HUD
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stationName.ifEmpty { stringResource(I18nR.string.au_radio_unknown_station) },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val sleepSuffix = if (sleepEndsAt > 0L) {
                        val minutes = ((sleepEndsAt - now).coerceAtLeast(0L) / 60_000L) + 1
                        "  ·  " + stringResource(I18nR.string.au_radio_sleep_in, minutes.toInt())
                    } else ""
                    val recText = recState?.let {
                        val seconds = ((now - it.startedAt).coerceAtLeast(0L) / 1000L)
                        val clock = if (seconds >= 3600) {
                            String.format(java.util.Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
                        } else {
                            String.format(java.util.Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60)
                        }
                        stringResource(I18nR.string.au2_radio2_rec_status, clock, Formatter.formatShortFileSize(context, it.bytes))
                    }
                    val statusText = (recText?.let { "$it  ·  " }.orEmpty()) +
                        ((if (reconnecting) stringResource(if (waitingForNetwork) I18nR.string.au16_reconnect_waiting else I18nR.string.au16_reconnect_label) else null)
                        ?: error?.let { stringResource(it) } ?: nowPlaying.ifEmpty { stringResource(I18nR.string.au_radio_streaming_live) }) + sleepSuffix

                    de.mm20.launcher2.ui.component.TelosDotMatrixText(
                        text = statusText.uppercase(),
                        color = if (error != null && !reconnecting || recText != null) TelosCyber.NeonMagenta else TelosCyber.NeonCyan,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                // Cyber Controls with Neon Tints
                IconButton(onClick = {
                    viewModel.toggleRecording(context)?.let {
                        Toast.makeText(context, context.getString(it), Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Icon(
                        painter = painterResource(if (recState != null) BaseR.drawable.radio_button_checked_24px else BaseR.drawable.radio_button_unchecked_24px),
                        contentDescription = stringResource(if (recState != null) I18nR.string.au2_radio2_stop_recording else I18nR.string.au2_radio2_record),
                        tint = if (recState != null) TelosCyber.NeonMagenta else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { viewModel.togglePlayPause() }) {
                    Icon(
                        painter = painterResource(if (isPlaying) BaseR.drawable.pause_24px else BaseR.drawable.play_arrow_24px),
                        contentDescription = if (isPlaying) stringResource(I18nR.string.au_radio_pause) else stringResource(I18nR.string.hc_play),
                        tint = if (isPlaying) TelosCyber.NeonGreen else MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(onClick = { viewModel.stop() }) {
                    Icon(
                        painter = painterResource(BaseR.drawable.close_24px),
                        contentDescription = stringResource(I18nR.string.hc_stop),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
