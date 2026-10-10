package de.mm20.launcher2.ui.media.tv

import android.text.format.DateFormat
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import de.mm20.launcher2.comms.tv.TvPlayerError
import de.mm20.launcher2.comms.tv.TvProgramme
import de.mm20.launcher2.ui.R
import kotlinx.coroutines.delay

/**
 * Full screen TV player in a dialog window (so it covers the hub) with the system bars hidden and the
 * screen kept on. Tap shows or hides the controls, a vertical swipe zaps to the next / previous
 * channel of the list. Back minimizes: the channel keeps playing in the mini player.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TvPlayerDialog(
    viewModel: TvViewModel,
    favoriteIds: Set<String>,
    reduceAnimations: Boolean,
    onMinimize: () -> Unit,
) {
    val controller = viewModel.controller
    val player by controller.player.collectAsStateWithLifecycle()
    val channel by controller.currentChannel.collectAsStateWithLifecycle()
    val playing by controller.isPlaying.collectAsStateWithLifecycle()
    val buffering by controller.isBuffering.collectAsStateWithLifecycle()
    val recovering by controller.isRecovering.collectAsStateWithLifecycle()
    val error by controller.error.collectAsStateWithLifecycle()
    val streamIndex by controller.streamIndex.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val epgKey = rememberTvEpgKey(viewModel, refresh = false)
    var epgSheet by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onMinimize,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            window?.let { w ->
                WindowCompat.setDecorFitsSystemWindows(w, false)
                w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                if (android.os.Build.VERSION.SDK_INT >= 28) {
                    w.attributes = w.attributes.apply {
                        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    }
                }
                WindowInsetsControllerCompat(w, w.decorView).apply {
                    hide(WindowInsetsCompat.Type.systemBars())
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            }
        }
        DisposableEffect(window) {
            onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        }

        var controls by remember { mutableStateOf(true) }
        // controls fade out after a while when the picture plays
        LaunchedEffect(controls, playing, channel?.id, epgSheet) {
            if (controls && playing && !epgSheet) {
                delay(4000)
                controls = false
            }
        }

        val current = channel
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setKeepContentOnPlayerReset(true)
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                        keepScreenOn = true
                        layoutDirection = View.LAYOUT_DIRECTION_LTR
                    }
                },
                update = { view -> if (view.player !== player) view.player = player },
                onRelease = { view -> view.player = null },
            )

            // no picture and nothing left to try: analog TV noise
            if (error != null) {
                TvStatic(Modifier.fillMaxSize(), reduceAnimations = reduceAnimations)
            }

            // gestures: tap toggles the controls, swipe up / down zaps
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures(onTap = { controls = !controls }) }
                    .pointerInput(Unit) {
                        var total = 0f
                        detectVerticalDragGestures(
                            onDragStart = { total = 0f },
                            onDragEnd = {
                                if (total < -ZAP_DISTANCE) {
                                    if (controller.next()) controls = true
                                } else if (total > ZAP_DISTANCE) {
                                    if (controller.previous()) controls = true
                                }
                            },
                            onVerticalDrag = { _, dy -> total += dy },
                        )
                    },
            )

            if (current != null) {
                val showStatus = error != null || recovering || (buffering && !playing)
                if (showStatus) {
                    Column(
                        Modifier.align(Alignment.Center).padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (error != null) {
                            OfflinePanel(onRetry = { controller.resume() }, onBack = onMinimize)
                        } else {
                            CircularProgressIndicator(color = Color.White)
                            if (recovering) {
                                Text(
                                    stringResource(R.string.au11_tvdata_recovering),
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }

                val enter = if (reduceAnimations) EnterTransition.None else fadeIn()
                val exit = if (reduceAnimations) ExitTransition.None else fadeOut()
                AnimatedVisibility(visible = controls || error != null, enter = enter, exit = exit) {
                    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                        val favorite = current.id in favoriteIds
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                                .background(Brush.verticalGradient(listOf(Color(0xB3000000), Color.Transparent)))
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(onClick = onMinimize) {
                                Icon(
                                    painterResource(R.drawable.keyboard_arrow_down_24px),
                                    contentDescription = stringResource(R.string.au12_tvui_minimize),
                                    tint = Color.White,
                                )
                            }
                            TvLogo(current, Modifier.size(40.dp), corner = 10.dp)
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(
                                    current.name,
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (streamIndex >= 0 && current.streams.size > 1) {
                                    Text(
                                        stringResource(R.string.au12_tvui_stream_info, streamIndex + 1, current.streams.size),
                                        color = Color(0xCCFFFFFF),
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                            IconButton(onClick = { viewModel.setFavorite(current.id, !favorite) }) {
                                Icon(
                                    painterResource(if (favorite) R.drawable.star_24px_filled else R.drawable.star_24px_outlined),
                                    contentDescription = stringResource(
                                        if (favorite) R.string.au12_tvui_favorite_remove else R.string.au12_tvui_favorite_add
                                    ),
                                    tint = Color.White,
                                )
                            }
                            IconButton(onClick = { viewModel.stop() }) {
                                Icon(
                                    painterResource(R.drawable.close_24px),
                                    contentDescription = stringResource(R.string.hc_stop),
                                    tint = Color.White,
                                )
                            }
                        }
                        val programmes = if (error == null) remember(epgKey, current.id) { viewModel.nowNext(current.id) } else null
                        val nowProg = programmes?.current
                        val nextProg = programmes?.next
                        if (nowProg != null || nextProg != null) {
                            val timeFormat = remember { DateFormat.getTimeFormat(context) }
                            Column(
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xB3000000))))
                                    .navigationBarsPadding()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                if (nowProg != null) {
                                    Text(
                                        stringResource(
                                            R.string.au13_tvextraui_player_now, nowProg.title,
                                            timeFormat.format(java.util.Date(nowProg.start)),
                                            timeFormat.format(java.util.Date(nowProg.stop)),
                                        ),
                                        color = Color.White,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.fillMaxWidth().clickable { epgSheet = true },
                                    )
                                    val span = (nowProg.stop - nowProg.start).coerceAtLeast(1L)
                                    val done = ((System.currentTimeMillis() - nowProg.start).toFloat() / span).coerceIn(0f, 1f)
                                    LinearProgressIndicator(
                                        progress = { done },
                                        modifier = Modifier.fillMaxWidth().height(3.dp),
                                        color = Color.White,
                                        trackColor = Color(0x44FFFFFF),
                                    )
                                }
                                if (nextProg != null) {
                                    Text(
                                        stringResource(
                                            R.string.au13_tvextraui_player_next, nextProg.title,
                                            timeFormat.format(java.util.Date(nextProg.start)),
                                        ),
                                        color = Color(0xCCFFFFFF),
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            if (epgSheet && nowProg != null) {
                                EpgSheet(nowProg, timeFormat, onDismiss = { epgSheet = false })
                            }
                        }
                        Row(
                            Modifier
                                .align(if (error != null) Alignment.BottomCenter else Alignment.Center)
                                .padding(bottom = if (error != null) 24.dp else 0.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                        ) {
                            RoundControl(R.drawable.skip_previous_24px, stringResource(R.string.hc_previous), 48.dp) { controller.previous() }
                            RoundControl(
                                if (playing) R.drawable.pause_24px else R.drawable.play_arrow_24px,
                                stringResource(if (playing) R.string.au_music_pause else R.string.hc_play),
                                64.dp,
                            ) { if (playing) controller.pause() else controller.resume() }
                            RoundControl(R.drawable.skip_next_24px, stringResource(R.string.hc_next), 48.dp) { controller.next() }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EpgSheet(p: TvProgramme, timeFormat: java.text.DateFormat, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(p.title, style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(
                    R.string.au13_tvextraui_epg_sheet_range,
                    timeFormat.format(java.util.Date(p.start)), timeFormat.format(java.util.Date(p.stop)),
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            if (p.category.isNotBlank()) {
                Text(p.category, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (p.description.isNotBlank()) {
                Text(p.description, style = MaterialTheme.typography.bodyMedium)
            }
            Box(Modifier.height(16.dp))
        }
    }
}

/** Translucent panel over the static: the channel is offline after every stream was tried */
@Composable
private fun OfflinePanel(onRetry: () -> Unit, onBack: () -> Unit) {
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xB3000000))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            painterResource(R.drawable.wifi_off_24px),
            contentDescription = stringResource(R.string.au13_tvextraui_static_description),
            tint = Color.White,
            modifier = Modifier.size(32.dp),
        )
        Text(
            stringResource(R.string.au13_tvextraui_offline_title),
            color = Color.White,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            stringResource(R.string.au13_tvextraui_offline_hint),
            color = Color(0xCCFFFFFF),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onRetry) { Text(stringResource(R.string.hc_retry)) }
            OutlinedButton(onClick = onBack) { Text(stringResource(R.string.au13_tvextraui_offline_back)) }
        }
    }
}

@Composable
private fun RoundControl(icon: Int, description: String, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    Box(
        Modifier.size(size).clip(CircleShape).background(Color(0x66000000)),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(onClick = onClick, modifier = Modifier.fillMaxSize()) {
            Icon(painterResource(icon), contentDescription = description, tint = Color.White, modifier = Modifier.size(size / 2))
        }
    }
}

private const val ZAP_DISTANCE = 140f
