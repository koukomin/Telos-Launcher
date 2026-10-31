package de.mm20.launcher2.ui.media.tv
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ListItem
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
 * screen kept on. Tap shows or hides the controls, a horizontal swipe zaps to the next / previous
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
    val reconnecting by controller.reconnecting.collectAsStateWithLifecycle()
    val waitingForNetwork by controller.waitingForNetwork.collectAsStateWithLifecycle()
    val streamIndex by controller.streamIndex.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val epgKey = rememberTvEpgKey(viewModel, refresh = true)
    var channelSheet by remember { mutableStateOf(false) }
    var channelQuery by remember { mutableStateOf("") }
    val home by viewModel.home.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val custom by viewModel.customChannels.collectAsStateWithLifecycle()
    val channels = remember(home, favorites, custom) { (favorites + custom + home.channels).distinctBy { it.id } }
    val matching = remember(channels, channelQuery) {
        de.mm20.launcher2.comms.search.TelosSearch.filter(channels, channelQuery) { listOf(it.name) }
    }
    var epgSheet by remember(channel?.id) { mutableStateOf<TvProgramme?>(null) }

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
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            }
        }
        DisposableEffect(window) {
            onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        }

        var controls by remember { mutableStateOf(true) }
        var adjustingLevels by remember { mutableStateOf(false) }
        LaunchedEffect(window, controls) {
            window?.let { w ->
                WindowInsetsControllerCompat(w, w.decorView).apply {
                    if (controls) show(WindowInsetsCompat.Type.systemBars())
                    else hide(WindowInsetsCompat.Type.systemBars())
                }
            }
        }
        // controls fade out after a while when the picture plays
        LaunchedEffect(controls, playing, channel?.id, epgSheet, adjustingLevels) {
            if (controls && playing && epgSheet == null && !adjustingLevels) {
                delay(4000)
                controls = false
            }
        }

        if (channelSheet) ModalBottomSheet(
            onDismissRequest = { channelSheet = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            de.mm20.launcher2.ui.component.TelosSearchBar(channelQuery, { channelQuery = it }, stringResource(I18nR.string.au12_tvui_search_hint))
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp).navigationBarsPadding()) {
                items(matching, key = { it.id }) { candidate ->
                    ListItem(
                        headlineContent = { Text(candidate.name) },
                        leadingContent = { TvLogo(candidate, Modifier.size(40.dp), corner = 10.dp) },
                        trailingContent = { if (candidate.id == channel?.id) Icon(painterResource(BaseR.drawable.check_24px), null) },
                        modifier = Modifier.clickable {
                            viewModel.play(candidate, channels); channelSheet = false; controls = true
                        },
                    )
                }
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
            if (error != null && !reconnecting) {
                TvStatic(Modifier.fillMaxSize(), reduceAnimations = reduceAnimations)
            }

            // gestures: tap toggles the controls, swipe left / right zaps
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures(onTap = { controls = !controls }) }
                    .pointerInput(Unit) {
                        var total = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { total = 0f },
                            onDragEnd = {
                                if (total < -ZAP_DISTANCE.dp.toPx()) {
                                    if (controller.next()) controls = true
                                } else if (total > ZAP_DISTANCE.dp.toPx()) {
                                    if (controller.previous()) controls = true
                                }
                            },
                            onHorizontalDrag = { change, dx -> change.consume(); total += dx },
                        )
                    }
                    .pointerInput(window) {
                        val audio = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
                        var left = true
                        detectVerticalDragGestures(
                            onDragStart = { offset ->
                                left = offset.x < size.width / 2f
                                adjustingLevels = true
                            },
                            onDragEnd = { adjustingLevels = false },
                            onVerticalDrag = { change, dy ->
                                change.consume()
                                if (left) {
                                    val w = window ?: return@detectVerticalDragGestures
                                    val attrs = w.attributes
                                    val current = if (attrs.screenBrightness < 0) 0.5f else attrs.screenBrightness
                                    attrs.screenBrightness = (current - dy / size.height).coerceIn(0.02f, 1f)
                                    w.attributes = attrs
                                } else {
                                    val max = audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                                    if (max > 0) {
                                        val now = audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
                                        val step = (-dy / size.height * max * 1.5f).toInt()
                                        if (step != 0) {
                                            audio.setStreamVolume(
                                                android.media.AudioManager.STREAM_MUSIC,
                                                (now + step).coerceIn(0, max),
                                                0,
                                            )
                                        }
                                    }
                                }
                            },
                        )
                    },
            )

            de.mm20.launcher2.ui.media.video.PlayerLevelControls(
                visible = controls || error != null || adjustingLevels,
                window = window,
                onAdjusting = { adjustingLevels = it },
            )
            if (current != null) {
                val showStatus = error != null || reconnecting || recovering || (buffering && !playing)
                if (showStatus) {
                    Column(
                        Modifier.align(Alignment.Center).padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (reconnecting) {
                            CircularProgressIndicator(color = Color.White)
                            Text(
                                stringResource(if (waitingForNetwork) I18nR.string.au16_reconnect_waiting else I18nR.string.au16_reconnect_label),
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        } else if (error != null) {
                            OfflinePanel(onRetry = { controller.resume() }, onBack = onMinimize)
                        } else {
                            CircularProgressIndicator(color = Color.White)
                            if (recovering) {
                                Text(
                                    stringResource(I18nR.string.au11_tvdata_recovering),
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
                            IconButton(onClick = { viewModel.stop() },
                                modifier = Modifier.background(Color(0xB31A1A1A), androidx.compose.foundation.shape.CircleShape)) {
                                Icon(painterResource(BaseR.drawable.arrow_back_24px),
                                    contentDescription = stringResource(I18nR.string.hc_back), tint = Color.White)
                            }
                            IconButton(onClick = { channelSheet = true }) {
                                Icon(painterResource(BaseR.drawable.menu_24px), stringResource(I18nR.string.au12_tvsearch_pref_title), tint = Color.White)
                            }
                            IconButton(onClick = onMinimize) {
                                Icon(
                                    painterResource(BaseR.drawable.keyboard_arrow_down_24px),
                                    contentDescription = stringResource(I18nR.string.au12_tvui_minimize),
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
                                        stringResource(I18nR.string.au12_tvui_stream_info, streamIndex + 1, current.streams.size),
                                        color = Color(0xCCFFFFFF),
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                            IconButton(onClick = { viewModel.setFavorite(current.id, !favorite) }) {
                                Icon(
                                    painterResource(if (favorite) BaseR.drawable.star_24px_filled else BaseR.drawable.star_24px_outlined),
                                    contentDescription = stringResource(
                                        if (favorite) I18nR.string.au12_tvui_favorite_remove else I18nR.string.au12_tvui_favorite_add
                                    ),
                                    tint = Color.White,
                                )
                            }
                            IconButton(onClick = { viewModel.stop() }) {
                                Icon(
                                    painterResource(BaseR.drawable.close_24px),
                                    contentDescription = stringResource(I18nR.string.hc_stop),
                                    tint = Color.White,
                                )
                            }
                        }
                        val programmes = if (error == null) remember(epgKey, current.id, controls) { viewModel.nowNext(current.id) } else null
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
                                            I18nR.string.au13_tvextraui_player_now, nowProg.title,
                                            timeFormat.format(java.util.Date(nowProg.start)),
                                            timeFormat.format(java.util.Date(nowProg.stop)),
                                        ),
                                        color = Color.White,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.fillMaxWidth().clickable { epgSheet = nowProg },
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
                                            I18nR.string.au13_tvextraui_player_next, nextProg.title,
                                            timeFormat.format(java.util.Date(nextProg.start)),
                                        ),
                                        color = Color(0xCCFFFFFF),
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.fillMaxWidth().clickable { epgSheet = nextProg },
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            epgSheet?.let { programme ->
                                EpgSheet(programme, timeFormat, onDismiss = { epgSheet = null })
                            }
                        }
                        Row(
                            Modifier
                                .align(if (error != null) Alignment.BottomCenter else Alignment.Center)
                                .padding(bottom = if (error != null) 24.dp else 0.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                        ) {
                            RoundControl(BaseR.drawable.skip_previous_24px, stringResource(I18nR.string.hc_previous), 48.dp) { controller.previous() }
                            RoundControl(
                                if (playing) BaseR.drawable.pause_24px else BaseR.drawable.play_arrow_24px,
                                stringResource(if (playing) I18nR.string.au_music_pause else I18nR.string.hc_play),
                                64.dp,
                            ) { if (playing) controller.pause() else controller.resume() }
                            RoundControl(BaseR.drawable.skip_next_24px, stringResource(I18nR.string.hc_next), 48.dp) { controller.next() }
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
                    I18nR.string.au13_tvextraui_epg_sheet_range,
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
            painterResource(BaseR.drawable.wifi_off_24px),
            contentDescription = stringResource(I18nR.string.au13_tvextraui_static_description),
            tint = Color.White,
            modifier = Modifier.size(32.dp),
        )
        Text(
            stringResource(I18nR.string.au13_tvextraui_offline_title),
            color = Color.White,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            stringResource(I18nR.string.au13_tvextraui_offline_hint),
            color = Color(0xCCFFFFFF),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onRetry) { Text(stringResource(I18nR.string.hc_retry)) }
            OutlinedButton(onClick = onBack) { Text(stringResource(I18nR.string.au13_tvextraui_offline_back)) }
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

private const val ZAP_DISTANCE = 56
