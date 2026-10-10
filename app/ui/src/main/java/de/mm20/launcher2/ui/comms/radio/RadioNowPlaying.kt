package de.mm20.launcher2.ui.comms.radio

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.comms.model.RadioStation
import de.mm20.launcher2.comms.radio.RadioRecorder
import de.mm20.launcher2.preferences.ui.PerformanceSettings
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.comms.RadioViewModel
import org.koin.compose.koinInject

/** Click / long click with a spring press scale (0.97). No animation when [reduce]. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun Modifier.pressScale(reduce: Boolean, onClick: () -> Unit, onLongClick: (() -> Unit)? = null): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed && !reduce) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "pressScale",
    )
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .combinedClickable(interactionSource = source, indication = null, onLongClick = onLongClick, onClick = onClick)
}

/** A rounded skeleton box with a moving highlight (static when [reduce]) */
@Composable
internal fun RadioShimmerBox(modifier: Modifier, reduce: Boolean, shape: Shape = RoundedCornerShape(24.dp)) {
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val light = MaterialTheme.colorScheme.surfaceContainerHighest
    val shift = if (reduce) 0f else {
        val t = rememberInfiniteTransition(label = "shimmer")
        val v by t.animateFloat(-1f, 2f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "shimmerShift")
        v
    }
    Box(
        modifier.clip(shape).drawBehind {
            val w = size.width.coerceAtLeast(1f)
            val x = shift * w
            drawRect(Brush.linearGradient(listOf(base, light, base), Offset(x, 0f), Offset(x + w, 0f)))
        },
    )
}

/** Skeleton rows shown while the Radio Browser search runs */
@Composable
internal fun RadioRowsSkeleton(reduce: Boolean, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(4) { RadioShimmerBox(Modifier.fillMaxWidth().aspectRatio(5f), reduce, RoundedCornerShape(20.dp)) }
    }
}

/** Empty state: big tinted icon, a title and a line */
@Composable
internal fun RadioEmptyState(title: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(painterResource(R.drawable.radio_24px), null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

private fun stationOf(id: String, stations: List<RadioStation>) = stations.firstOrNull { it.id == id }

/**
 * Floating "now playing" card at the bottom of the radio screen. Shown while a station is loaded;
 * a tap opens the full sheet via [onOpen].
 */
@Composable
fun RadioNowPlayingBar(stations: List<RadioStation>, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val player: RadioViewModel = viewModel()
    val reduce by koinInject<PerformanceSettings>().reduceAnimations.collectAsState(false)
    val visible by player.isVisible.collectAsStateWithLifecycle()
    val stationId by player.stationId.collectAsStateWithLifecycle()
    val name by player.stationName.collectAsStateWithLifecycle()
    val track by player.nowPlayingMetadata.collectAsStateWithLifecycle()
    val isPlaying by player.isPlaying.collectAsStateWithLifecycle()
    val station = stationOf(stationId, stations)
    val logoColor = rememberLogoColor(station?.faviconUrl.orEmpty(), true) ?: MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surfaceContainerHigh
    AnimatedVisibility(
        visible = visible,
        enter = if (reduce) fadeIn(tween(0)) else slideInVertically(spring(0.8f, 400f)) { it } + fadeIn(),
        exit = if (reduce) fadeOut(tween(0)) else slideOutVertically { it } + fadeOut(),
        modifier = modifier,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.horizontalGradient(listOf(logoColor.copy(alpha = 0.35f).compositeOver(surface), surface)))
                .pressScale(reduce, onOpen)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (station != null) RadioLogo(station, Modifier.size(56.dp), corner = 12.dp, pad = 6.dp)
            else Box(Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.radio_24px), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    name.ifEmpty { stringResource(R.string.au_radio_unknown_station) },
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    track.ifEmpty { stringResource(R.string.au_radio_streaming_live) },
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = { player.togglePlayPause() }) {
                Icon(
                    painterResource(if (isPlaying) R.drawable.pause_24px else R.drawable.play_arrow_24px),
                    stringResource(if (isPlaying) R.string.au_radio_pause else R.string.hc_play),
                )
            }
            IconButton(onClick = { player.stop() }) {
                Icon(painterResource(R.drawable.close_24px), stringResource(R.string.hc_stop))
            }
        }
    }
}

/**
 * Full-screen "now playing" sheet: big logo on a logo-colour gradient, station and track, play/pause,
 * record, sleep timer, share and favorite. [stations] is used to find the logo and the share/favorite target.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun RadioNowPlayingSheet(
    onDismiss: () -> Unit,
    stations: List<RadioStation> = emptyList(),
    favoriteIds: Set<String> = emptySet(),
    onToggleFavorite: ((RadioStation) -> Unit)? = null,
) {
    val player: RadioViewModel = viewModel()
    val context = LocalContext.current
    val reduce by koinInject<PerformanceSettings>().reduceAnimations.collectAsState(false)
    val stationId by player.stationId.collectAsStateWithLifecycle()
    val name by player.stationName.collectAsStateWithLifecycle()
    val track by player.nowPlayingMetadata.collectAsStateWithLifecycle()
    val playWhenReady by player.playWhenReady.collectAsStateWithLifecycle()
    val loading by player.isLoading.collectAsStateWithLifecycle()
    val error by player.error.collectAsStateWithLifecycle()
    val reconnecting by de.mm20.launcher2.comms.radio.RadioPlayerService.reconnecting.collectAsStateWithLifecycle()
    val waitingForNetwork by de.mm20.launcher2.comms.radio.RadioPlayerService.waiting.collectAsStateWithLifecycle()
    val sleepEndsAt by player.sleepEndsAt.collectAsStateWithLifecycle()
    val recording by player.recordingState.collectAsStateWithLifecycle()
    val visible by player.isVisible.collectAsStateWithLifecycle()
    val isRecording = recording is RadioRecorder.State.Recording
    val station = stationOf(stationId, stations)
    val isFavorite = station != null && station.id in favoriteIds
    val logoColor = rememberLogoColor(station?.faviconUrl.orEmpty(), true) ?: MaterialTheme.colorScheme.primary
    var sleepMenu by remember { mutableStateOf(false) }

    // the station was stopped from the notification or the bar: nothing left to show
    if (!visible && stationId.isEmpty()) {
        androidx.compose.runtime.LaunchedEffect(Unit) { onDismiss() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        val low = MaterialTheme.colorScheme.surfaceContainerLow
        Column(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(logoColor.copy(alpha = 0.35f).compositeOver(low), low)))
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.au14_radioui_now_playing),
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (station != null) {
                RadioLogo(station, Modifier.widthIn(max = 280.dp).fillMaxWidth().aspectRatio(1f), corner = 28.dp, pad = 28.dp)
            } else {
                Box(
                    Modifier.widthIn(max = 280.dp).fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(28.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) { Icon(painterResource(R.drawable.radio_24px), null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    name.ifEmpty { stringResource(R.string.au_radio_unknown_station) },
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    (if (reconnecting) stringResource(if (waitingForNetwork) R.string.au16_reconnect_waiting else R.string.au16_reconnect_label) else null)
                        ?: error?.let { stringResource(it) } ?: track.ifEmpty { stringResource(R.string.au_radio_streaming_live) },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (error != null && !reconnecting) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
            // big play / pause with a loading ring
            Box(
                Modifier.size(80.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)
                    .clickable { player.togglePlayPause() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(if (playWhenReady) R.drawable.pause_24px else R.drawable.play_arrow_24px),
                    stringResource(if (playWhenReady) R.string.au_radio_pause else R.string.hc_play),
                    Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onPrimary,
                )
                if (loading && playWhenReady) {
                    CircularProgressIndicator(Modifier.size(72.dp), strokeWidth = 3.dp, color = MaterialTheme.colorScheme.onPrimary)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    player.toggleRecording(context)?.let { Toast.makeText(context, context.getString(it), Toast.LENGTH_SHORT).show() }
                }) {
                    Icon(
                        painterResource(if (isRecording) R.drawable.radio_button_checked_24px else R.drawable.radio_button_unchecked_24px),
                        stringResource(if (isRecording) R.string.au2_radio2_stop_recording else R.string.au2_radio2_record),
                        tint = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box {
                    IconButton(onClick = { sleepMenu = true }) {
                        Icon(
                            painterResource(R.drawable.timer_24px), stringResource(R.string.hc_sleep_timer),
                            tint = if (sleepEndsAt > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DropdownMenu(expanded = sleepMenu, onDismissRequest = { sleepMenu = false }, shape = RoundedCornerShape(20.dp)) {
                        listOf(15, 30, 45, 60, 90).forEach { minutes ->
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.hc_stop_playback_in_minutes, minutes)) },
                                onClick = { player.setSleepTimer(minutes); sleepMenu = false },
                            )
                        }
                        if (sleepEndsAt > 0) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.hc_turn_timer_off)) },
                                onClick = { player.cancelSleepTimer(); sleepMenu = false },
                            )
                        }
                    }
                }
                if (station != null) {
                    IconButton(onClick = { RadioShare.share(context, station) }) {
                        Icon(painterResource(R.drawable.share_24px), stringResource(R.string.au10_radio_share), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (onToggleFavorite != null) {
                        IconButton(onClick = { onToggleFavorite(station) }) {
                            Icon(
                                painterResource(if (isFavorite) R.drawable.star_24px_filled else R.drawable.star_24px),
                                stringResource(R.string.hc_favorite),
                                tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
