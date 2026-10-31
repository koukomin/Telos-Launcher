package de.mm20.launcher2.ui.comms.radio
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
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
        Icon(painterResource(BaseR.drawable.radio_24px), null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
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
                .background(Brush.horizontalGradient(listOf(logoColor.copy(alpha = 0.08f).compositeOver(surface), surface)))
                .pressScale(reduce, onOpen)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (station != null) RadioLogo(station, Modifier.size(56.dp), corner = 12.dp, pad = 6.dp)
            else Box(Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                Icon(painterResource(BaseR.drawable.radio_24px), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    name.ifEmpty { stringResource(I18nR.string.au_radio_unknown_station) },
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    track.ifEmpty { stringResource(I18nR.string.au_radio_streaming_live) },
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = { player.togglePlayPause() }) {
                Icon(
                    painterResource(if (isPlaying) BaseR.drawable.pause_24px else BaseR.drawable.play_arrow_24px),
                    stringResource(if (isPlaying) I18nR.string.au_radio_pause else I18nR.string.hc_play),
                )
            }
            IconButton(onClick = { player.stop() }) {
                Icon(painterResource(BaseR.drawable.close_24px), stringResource(I18nR.string.hc_stop))
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

    val white = Color.White
    val soft = Color.White.copy(alpha = 0.70f)
    val top = logoColor.copy(alpha = 0.9f).compositeOver(Color.Black).let { Color(it.red * 0.45f, it.green * 0.45f, it.blue * 0.45f) }
    // the stations that previous / next move between: the collection, or what was passed
    val skipList = remember(stations, favoriteIds) {
        stations.filter { it.id in favoriteIds }.ifEmpty { stations }
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(top, Color(0xFF0B0B0D))))) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) {
                        Icon(painterResource(BaseR.drawable.keyboard_arrow_down_24px), stringResource(I18nR.string.hc_close), tint = white)
                    }
                    Spacer(Modifier.weight(1f))
                    Text(stringResource(I18nR.string.au14_radioui_now_playing), style = MaterialTheme.typography.labelLarge, color = soft)
                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.size(48.dp))
                }
                Spacer(Modifier.height(16.dp))
                if (station != null) {
                    RadioLogo(station, Modifier.widthIn(max = 340.dp).fillMaxWidth(0.92f).aspectRatio(1f), corner = 10.dp, pad = 28.dp)
                } else {
                    Box(
                        Modifier.widthIn(max = 340.dp).fillMaxWidth(0.92f).aspectRatio(1f).clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.10f)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(painterResource(BaseR.drawable.radio_24px), null, Modifier.size(72.dp), tint = soft) }
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    name.ifEmpty { stringResource(I18nR.string.au_radio_unknown_station) },
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = white,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    (if (reconnecting) stringResource(if (waitingForNetwork) I18nR.string.au16_reconnect_waiting else I18nR.string.au16_reconnect_label) else null)
                        ?: error?.let { stringResource(it) } ?: track.ifEmpty { stringResource(I18nR.string.au_radio_streaming_live) },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (error != null && !reconnecting) MaterialTheme.colorScheme.error else soft,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth(),
                )
                // chips like YouTube Music: favorite, record, sleep timer, share
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 16.dp).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    @Composable
                    fun chip(icon: Int, label: String, tint: Color, onClick: () -> Unit) {
                        Row(
                            Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.12f)).clickable(onClick = onClick)
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(painterResource(icon), null, Modifier.size(20.dp), tint = tint)
                            Text(label, style = MaterialTheme.typography.labelLarge, color = white, maxLines = 1)
                        }
                    }
                    if (station != null && onToggleFavorite != null) {
                        chip(if (isFavorite) BaseR.drawable.star_24px_filled else BaseR.drawable.star_24px, stringResource(I18nR.string.hc_favorite), white) { onToggleFavorite(station) }
                    }
                    chip(
                        if (isRecording) BaseR.drawable.radio_button_checked_24px else BaseR.drawable.radio_button_unchecked_24px,
                        stringResource(if (isRecording) I18nR.string.au2_radio2_stop_recording else I18nR.string.au2_radio2_record),
                        if (isRecording) MaterialTheme.colorScheme.error else white,
                    ) { player.toggleRecording(context)?.let { Toast.makeText(context, context.getString(it), Toast.LENGTH_SHORT).show() } }
                    Box {
                        chip(BaseR.drawable.timer_24px, stringResource(I18nR.string.hc_sleep_timer), if (sleepEndsAt > 0) MaterialTheme.colorScheme.primary else white) { sleepMenu = true }
                        DropdownMenu(expanded = sleepMenu, onDismissRequest = { sleepMenu = false }, shape = RoundedCornerShape(20.dp)) {
                            listOf(15, 30, 45, 60, 90).forEach { minutes ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(I18nR.string.hc_stop_playback_in_minutes, minutes)) },
                                    onClick = { player.setSleepTimer(minutes); sleepMenu = false },
                                )
                            }
                            if (sleepEndsAt > 0) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(I18nR.string.hc_turn_timer_off)) },
                                    onClick = { player.cancelSleepTimer(); sleepMenu = false },
                                )
                            }
                        }
                    }
                    if (station != null) {
                        chip(BaseR.drawable.share_24px, stringResource(I18nR.string.au10_radio_share), white) { RadioShare.share(context, station) }
                    }
                }
                // a live stream has no position: a full line with a dot at the end instead of a scrubber
                Box(Modifier.fillMaxWidth().height(14.dp), contentAlignment = Alignment.CenterStart) {
                    Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.30f)))
                    Box(Modifier.align(Alignment.CenterEnd).size(14.dp).background(white, CircleShape))
                }
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { player.skipStation(-1, skipList) }, enabled = skipList.size > 1, modifier = Modifier.size(56.dp)) {
                        Icon(painterResource(BaseR.drawable.skip_previous_24px), stringResource(I18nR.string.hc_previous), Modifier.size(32.dp), tint = if (skipList.size > 1) white else soft)
                    }
                    Box(
                        Modifier.size(72.dp).clip(CircleShape).background(white).clickable { player.togglePlayPause() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painterResource(if (playWhenReady) BaseR.drawable.pause_24px else BaseR.drawable.play_arrow_24px),
                            stringResource(if (playWhenReady) I18nR.string.au_radio_pause else I18nR.string.hc_play),
                            Modifier.size(40.dp), tint = Color.Black,
                        )
                        if (loading && playWhenReady) {
                            CircularProgressIndicator(Modifier.size(64.dp), strokeWidth = 3.dp, color = Color.Black.copy(alpha = 0.5f))
                        }
                    }
                    IconButton(onClick = { player.skipStation(1, skipList) }, enabled = skipList.size > 1, modifier = Modifier.size(56.dp)) {
                        Icon(painterResource(BaseR.drawable.skip_next_24px), stringResource(I18nR.string.hc_next), Modifier.size(32.dp), tint = if (skipList.size > 1) white else soft)
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
