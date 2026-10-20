package de.mm20.launcher2.ui.media.hub

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.State
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.zIndex
import de.mm20.launcher2.preferences.ui.PerformanceSettings
import de.mm20.launcher2.ui.media.music.rememberArtworkTint
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import de.mm20.launcher2.ui.component.AdaptiveNavigationBar
import de.mm20.launcher2.ui.component.AdaptiveNavigationItem
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import coil.compose.AsyncImage
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.preferences.comms.CommsSettings
import de.mm20.launcher2.preferences.ui.UiSettings
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.theme.transparency.transparency
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import de.mm20.launcher2.ui.comms.RadioViewModel
import de.mm20.launcher2.ui.comms.radio.RadioDashboardScreen
import de.mm20.launcher2.ui.locals.LocalBackStack
import de.mm20.launcher2.ui.media.LocalInMediaHub
import de.mm20.launcher2.ui.media.music.MusicScreen
import de.mm20.launcher2.ui.media.music.MusicViewModel
import de.mm20.launcher2.ui.media.tv.TvScreen
import de.mm20.launcher2.ui.media.tv.TvViewModel
import de.mm20.launcher2.ui.media.video.VideoScreen
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

/** Telos Media. [space] is music, radio, tv or video; empty = the space that was open last. */
@Serializable
data class MediaHubRoute(val space: String = "") : NavKey

private enum class MediaSpace(
    val id: String,
    val appKey: String,
    @DrawableRes val icon: Int,
    @StringRes val label: Int,
) {
    Music("music", "telos_music_app://music", R.drawable.headphones_24px, R.string.au9_mediahub_space_music),
    Radio("radio", "telos_radio_app://radio", R.drawable.ic_glyph_radio, R.string.au9_mediahub_space_radio),
    Video("video", "telos_video_app://video", R.drawable.video_library_24px, R.string.au9_mediahub_space_video),
    Tv("tv", "telos_tv_app://tv", R.drawable.tv_24px, R.string.au12_tvui_space_tv),
    YouTube("youtube", "telos_youtube_app://youtube", de.mm20.launcher2.base.R.drawable.ic_app_youtube_fg, R.string.telos_youtube_label),
    YouTubeMusic("youtube_music", "telos_youtube_music_app://youtube_music", de.mm20.launcher2.base.R.drawable.ic_app_youtube_music_fg, R.string.telos_youtube_music_label),
    Stremio("stremio", "telos_media_app://stremio", R.drawable.theaters_24px, R.string.au22_stremio_label),
}

private val MediaSpace.group: String get() = when (this) {
    MediaSpace.Music, MediaSpace.YouTubeMusic -> "listen"
    MediaSpace.Video, MediaSpace.YouTube, MediaSpace.Stremio -> "watch"
    MediaSpace.Radio, MediaSpace.Tv -> "live"
}
private val MediaSpace.groupLabel: Int get() = when (group) {
    "listen" -> R.string.telos_media_listen
    "watch" -> R.string.telos_media_watch
    else -> R.string.telos_media_live
}
private val MediaSpace.groupIcon: Int get() = when (group) {
    "listen" -> R.drawable.headphones_24px
    "watch" -> R.drawable.video_library_24px
    else -> R.drawable.tv_24px
}

/**
 * The unified media library. A single source toolbar hosts each screen's contextual actions;
 * source selection and ordering live in its menu, leaving the viewport for media.
 * Only the open source is composed, while view models and saveable navigation state survive switches.
 */
@Composable
fun MediaHubScreen(initialSpace: String = "") {
    val context = LocalContext.current
    val settings: CommsSettings = koinInject()
    val backStack = LocalBackStack.current

    // All four spaces are always offered: Telos Media is one app, and hiding the older Music or Radio entries
    // in the Store only removes their icons (they are hidden by default), never the space itself
    // order: the user's (long press and drag in the pill), spaces missing from it keep the default order
    val orderFlow = remember(settings) { settings.mediaHubOrder }
    val savedOrder by orderFlow.collectAsState(emptyList())
    val available = remember(savedOrder) {
        val byId = MediaSpace.entries.associateBy { it.id }
        val chosen = savedOrder.mapNotNull { byId[it] }.distinct()
        chosen + MediaSpace.entries.filter { it !in chosen }
    }

    var space by rememberSaveable { mutableStateOf(initialSpace) }
    LaunchedEffect(Unit) {
        if (MediaSpace.entries.none { it.id == space }) {
            val saved = settings.mediaHubSpace.first()
            space = MediaSpace.entries.firstOrNull { it.id == saved }?.id ?: MediaSpace.Music.id
        }
    }
    val current = available.firstOrNull { it.id == space }
        ?: if (MediaSpace.entries.none { it.id == space }) null else available.first()
    LaunchedEffect(current) { current?.let { settings.setMediaHubSpace(it.id) } }

    val groupSelections = remember { mutableStateMapOf<String, MediaSpace>() }
    LaunchedEffect(current) { current?.let { groupSelections[it.group] = it } }
    val groupSpaces = available.distinctBy { it.group }.map { groupSelections[it.group] ?: it }
    val youtubeVm: de.mm20.launcher2.ui.media.youtube.YouTubeViewModel = viewModel()
    LaunchedEffect(current) { if (current == MediaSpace.YouTube || current == MediaSpace.YouTubeMusic) youtubeVm.reconnect(context) }
    val musicVm: MusicViewModel = viewModel()
    val radioVm: RadioViewModel = viewModel()
    val tvVm: TvViewModel = viewModel()
    // the mini player follows the players also while their space is not open
    LaunchedEffect(Unit) {
        runCatching { musicVm.connect(context) }
        runCatching { radioVm.initialize(context) }
    }

    var openMusicPlayer by remember { mutableStateOf(false) }
    val stateHolder = rememberSaveableStateHolder()
    val performanceSettings: PerformanceSettings = koinInject()
    // the getter builds a new flow on every call: remember it, or the collection restarts on each recomposition
    val reduceFlow = remember(performanceSettings) { performanceSettings.reduceAnimations }
    val reduceAnimations by reduceFlow.collectAsState(false)

    val musicNow by musicVm.nowPlaying.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme
    val artTint by rememberArtworkTint(musicNow?.artUri?.toString(), scheme.primary, reduceAnimations)
    val accentTarget = when (current) {
        MediaSpace.Music -> artTint
        MediaSpace.Radio -> scheme.tertiary
        MediaSpace.Tv -> scheme.secondary
        else -> scheme.primary
    }
    val accent by animateColorAsState(
        accentTarget,
        animationSpec = if (reduceAnimations) snap() else tween(500),
        label = "mediaHubAccent",
    )
    val uiSettings: UiSettings = koinInject()
    val blurRadius by uiSettings.drawerBackgroundBlur.collectAsState(32)
    val t = MaterialTheme.transparency
    val hazeState = rememberHazeState(blurEnabled = isAtLeastApiLevel(33) && blurRadius > 0)

    Surface(
        color = Color.Transparent,
        contentColor = scheme.onSurface,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            accent.copy(alpha = 0.10f * t.surface),
                            scheme.surface.copy(alpha = t.background),
                        )
                    )
                )
                .statusBarsPadding()
        ) {
            Box(Modifier.weight(1f).fillMaxWidth().hazeSource(hazeState)) {
                AnimatedContent(
                    targetState = current,
                    transitionSpec = {
                        val t = if (reduceAnimations) {
                            EnterTransition.None togetherWith ExitTransition.None
                        } else {
                            val dir = if ((targetState?.ordinal ?: 0) >= (initialState?.ordinal ?: 0)) 1 else -1
                            (fadeIn(tween(220)) + slideInHorizontally(spring(0.8f, 400f)) { dir * it / 12 }) togetherWith
                                (fadeOut(tween(120)) + slideOutHorizontally(spring(0.8f, 400f)) { -dir * it / 12 })
                        }
                        t.using(SizeTransform(clip = false))
                    },
                    label = "mediaHubSpace",
                    modifier = Modifier.fillMaxSize(),
                ) { target ->
                    if (target != null) {
                        stateHolder.SaveableStateProvider(target.id) {
                            CompositionLocalProvider(
                                LocalInMediaHub provides true,
                                de.mm20.launcher2.ui.media.LocalMediaHubToolbar provides { actions ->
                                    HubToolbar(
                                        current = target, available = available, groupSpaces = groupSpaces,
                                        onSelect = { space = it.id },
                                        onReorder = { reordered ->
                                            settings.setMediaHubOrder(reordered.flatMap { group -> available.filter { it.group == group.group }.map { it.id } })
                                        },
                                        reduceAnimations = reduceAnimations,
                                        onBack = { backStack.removeLastOrNull() },
                                        actions = actions,
                                        haze = hazeState,
                                        blurRadius = blurRadius,
                                    )
                                },
                            ) {
                                when (target) {
                                    MediaSpace.Music -> MusicScreen(
                                        openNowPlaying = openMusicPlayer,
                                        onOpenNowPlayingConsumed = { openMusicPlayer = false },
                                    )
                                    MediaSpace.Radio -> RadioDashboardScreen()
                                    MediaSpace.Tv -> TvScreen()
                                    MediaSpace.Video -> VideoScreen()
                                    MediaSpace.YouTube -> de.mm20.launcher2.ui.media.youtube.YouTubeScreen(false, youtubeVm)
                                    MediaSpace.YouTubeMusic -> de.mm20.launcher2.ui.media.youtube.YouTubeScreen(true, youtubeVm)
                                    MediaSpace.Stremio -> de.mm20.launcher2.ui.media.stremio.StremioScreen()
                                }
                            }
                        }
                    }
                }
            }

            if (current == MediaSpace.YouTube || current == MediaSpace.YouTubeMusic) de.mm20.launcher2.ui.media.youtube.YouTubeMiniPlayer(youtubeVm, current == MediaSpace.YouTubeMusic) { music ->
                space = if (music) MediaSpace.YouTubeMusic.id else MediaSpace.YouTube.id
            }
            if (current != null) {
                HubMiniPlayer(
                    space = current,
                    musicVm = musicVm,
                    radioVm = radioVm,
                    tvVm = tvVm,
                    reduceAnimations = reduceAnimations,
                    haze = hazeState,
                    blurRadius = blurRadius,
                    onOpen = { target ->
                        if (target == MediaSpace.Music) openMusicPlayer = true
                        if (target == MediaSpace.Tv) tvVm.openPlayer()
                        space = target.id
                    },
                )
                HubBottomBar(
                    current = current,
                    groupSpaces = groupSpaces,
                    haze = hazeState,
                    blurRadius = blurRadius,
                    onSelect = { dest ->
                        space = (groupSelections[dest.group] ?: dest).id
                    },
                )
            }
        }
    }
}

/** Back, compact destination title, source chips, then the open screen's actions. */
@Composable
private fun HubToolbar(
    current: MediaSpace,
    available: List<MediaSpace>,
    groupSpaces: List<MediaSpace>,
    onSelect: (MediaSpace) -> Unit,
    onReorder: (List<MediaSpace>) -> Unit,
    reduceAnimations: Boolean,
    onBack: () -> Unit,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
    haze: HazeState,
    blurRadius: Int,
) {
    val sources = available.filter { it.group == current.group }
    val t = MaterialTheme.transparency
    val glass = MaterialTheme.colorScheme.surface.copy(alpha = t.elevatedSurface)
    Column(
        Modifier
            .fillMaxWidth()
            .hubGlass(haze, blurRadius, glass)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.arrow_back_24px), stringResource(R.string.hc_back))
            }
            Text(
                stringResource(current.groupLabel),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            actions()
        }
        if (sources.size > 1) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                sources.forEach { source ->
                    FilterChip(
                        selected = source == current,
                        onClick = { onSelect(source) },
                        label = {
                            Text(stringResource(source.label), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        },
                        leadingIcon = {
                            Icon(painterResource(source.icon), null, modifier = Modifier.size(16.dp))
                        },
                    )
                }
            }
        }
    }
}

/** Floating glass pill with four segments and a sliding tonal indicator; compact screens show the label of the selected segment only */
@Composable
private fun SpacePill(
    spaces: List<MediaSpace>,
    selected: MediaSpace,
    onSelect: (MediaSpace) -> Unit,
    onReorder: (List<MediaSpace>) -> Unit,
    reduceAnimations: Boolean,
    modifier: Modifier = Modifier,
) {
    // The selected space has a full-size title below the icon pill.
    val currentSpaces by androidx.compose.runtime.rememberUpdatedState(spaces)
    val currentOnReorder by androidx.compose.runtime.rememberUpdatedState(onReorder)
    var dragItem by remember { mutableStateOf<MediaSpace?>(null) }
    var dragDx by remember { mutableStateOf(0f) }
    val haptic = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(28.dp)
    val xs = remember { mutableStateMapOf<MediaSpace, Float>() }
    val ws = remember { mutableStateMapOf<MediaSpace, Float>() }
    val targetX = xs[selected] ?: 0f
    val targetW = ws[selected] ?: 0f
    val ix by animateFloatAsState(
        targetX, if (reduceAnimations) snap() else spring(0.8f, 400f), label = "mediaHubIndicatorX",
    )
    val iw by animateFloatAsState(
        targetW, if (reduceAnimations) snap() else spring(0.8f, 400f), label = "mediaHubIndicatorW",
    )
    val density = LocalDensity.current
    Surface(
        shape = shape,
        color = scheme.surfaceContainerHigh.copy(alpha = 0.85f),
        modifier = modifier.border(1.dp, scheme.outline.copy(alpha = 0.2f), shape),
    ) {
        Box(Modifier.padding(4.dp)) {
            if (iw > 0f) {
                Box(
                    Modifier
                        .offset(x = with(density) { ix.toDp() })
                        .width(with(density) { iw.toDp() })
                        .height(36.dp)
                        .clip(CircleShape)
                        .background(scheme.primaryContainer)
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (item in spaces) key(item) {
                    val isSelected = item == selected
                    val content by animateColorAsState(
                        if (isSelected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                        label = "mediaHubSegmentContent",
                    )
                    val label = stringResource(item.groupLabel)
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .onGloballyPositioned {
                                xs[item] = it.positionInParent().x
                                ws[item] = it.size.width.toFloat()
                            }
                            .graphicsLayer { translationX = if (dragItem == item) dragDx else 0f }
                            .clip(CircleShape)
                            .semantics { contentDescription = label; this.selected = isSelected }
                            .clickable(role = Role.Tab) {
                                if (!isSelected) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelect(item)
                            }
                            // long press and drag sideways to change the order
                            .pointerInput(item) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        dragItem = item
                                        dragDx = 0f
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragDx += amount.x
                                        val list = currentSpaces
                                        val i = list.indexOf(item)
                                        val w = ws[item] ?: 0f
                                        if (i >= 0 && w > 0f) {
                                            if (dragDx > w / 2 && i < list.lastIndex) {
                                                dragDx -= ws[list[i + 1]] ?: w
                                                currentOnReorder(list.toMutableList().also { it.add(i + 1, it.removeAt(i)) })
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            } else if (dragDx < -w / 2 && i > 0) {
                                                dragDx += ws[list[i - 1]] ?: w
                                                currentOnReorder(list.toMutableList().also { it.add(i - 1, it.removeAt(i)) })
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }
                                        }
                                    },
                                    onDragEnd = { dragItem = null; dragDx = 0f },
                                    onDragCancel = { dragItem = null; dragDx = 0f },
                                )
                            }
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painterResource(item.groupIcon),
                            contentDescription = null,
                            tint = content,
                            modifier = Modifier.size(18.dp),
                        )

                    }
                }
            }
        }
    }
}

@Composable
private fun HubBottomBar(
    current: MediaSpace,
    groupSpaces: List<MediaSpace>,
    haze: HazeState,
    blurRadius: Int,
    onSelect: (MediaSpace) -> Unit,
) {
    val dests = listOf("listen", "watch", "live").mapNotNull { id ->
        groupSpaces.firstOrNull { it.group == id }
            ?: MediaSpace.entries.firstOrNull { it.group == id }
    }.distinctBy { it.group }
    val t = MaterialTheme.transparency
    val glass = MaterialTheme.colorScheme.surface.copy(alpha = t.elevatedSurface)
    AdaptiveNavigationBar(
        modifier = Modifier.hubGlass(haze, blurRadius, glass),
        containerColor = Color.Transparent,
    ) {
        dests.forEach { dest ->
            val selected = dest.group == current.group
            AdaptiveNavigationItem(
                selected = selected,
                onClick = { onSelect(dest) },
                icon = {
                    Icon(painterResource(dest.groupIcon), contentDescription = null, modifier = Modifier.size(22.dp))
                },
                label = {
                    Text(stringResource(dest.groupLabel))
                },
            )
        }
    }
}

/**
 * Mini player for music and radio (they are separate players with separate sessions). Shows the
 * player of the open space, or else the one that plays. Hidden on the video space.
 */
@Composable
private fun HubMiniPlayer(
    space: MediaSpace,
    musicVm: MusicViewModel,
    radioVm: RadioViewModel,
    tvVm: TvViewModel,
    reduceAnimations: Boolean,
    haze: HazeState,
    blurRadius: Int,
    onOpen: (MediaSpace) -> Unit,
    modifier: Modifier = Modifier,
) {
    val positionMs by musicVm.positionMs.collectAsStateWithLifecycle()
    val musicNow by musicVm.nowPlaying.collectAsStateWithLifecycle()
    val musicPlaying by musicVm.isPlaying.collectAsStateWithLifecycle()
    val radioVisible by radioVm.isVisible.collectAsStateWithLifecycle()
    val radioPlaying by radioVm.isPlaying.collectAsStateWithLifecycle()
    val stationName by radioVm.stationName.collectAsStateWithLifecycle()
    val radioMeta by radioVm.nowPlayingMetadata.collectAsStateWithLifecycle()
    val radioError by radioVm.error.collectAsStateWithLifecycle()
    val recordingState by radioVm.recordingState.collectAsStateWithLifecycle()
    val isRecording = recordingState is de.mm20.launcher2.comms.radio.RadioRecorder.State.Recording
    val context = LocalContext.current

    val tvChannel by tvVm.controller.currentChannel.collectAsStateWithLifecycle()
    val tvPlaying by tvVm.controller.isPlaying.collectAsStateWithLifecycle()
    val tvPlayerOpen by tvVm.playerOpen.collectAsStateWithLifecycle()

    val hasMusic = musicNow != null
    val hasTv = tvChannel != null
    val shown: MediaSpace? = when {
        space == MediaSpace.Music && hasMusic -> MediaSpace.Music
        // RadioDashboard owns its station bar (including scrollable controls).
        space == MediaSpace.Radio -> null
        space == MediaSpace.Tv && hasTv && !tvPlayerOpen -> MediaSpace.Tv
        else -> null
    }

    // keeps the last content while the bar slides out
    var last by remember { mutableStateOf(MediaSpace.Music) }
    LaunchedEffect(shown) { if (shown != null) last = shown }
    val display = shown ?: last

    AnimatedVisibility(
        visible = shown != null,
        enter = if (reduceAnimations) EnterTransition.None else slideInVertically(spring(0.8f, 400f)) { it } + fadeIn(),
        exit = if (reduceAnimations) ExitTransition.None else slideOutVertically(spring(0.8f, 400f)) { it } + fadeOut(),
        modifier = modifier,
    ) {
        val isMusic = display == MediaSpace.Music
        val isTv = display == MediaSpace.Tv
        val playing = if (isMusic) musicPlaying else if (isTv) tvPlaying else radioPlaying
        val unknownStation = stringResource(R.string.au_radio_unknown_station)
        val unknownTitle = stringResource(R.string.au9_mediahub_unknown_title)
        val tvSubtitle = stringResource(R.string.au12_tvui_mini_subtitle)
        // the last TV channel stays while the bar slides out
        var lastTv by remember { mutableStateOf<de.mm20.launcher2.comms.tv.TvChannel?>(null) }
        if (tvChannel != null) lastTv = tvChannel
        val title = if (isMusic) musicNow?.title?.ifBlank { unknownTitle } ?: unknownTitle
        else if (isTv) lastTv?.name.orEmpty()
        else stationName.ifEmpty { unknownStation }
        val subtitle = if (isMusic) musicNow?.artist.orEmpty()
        else if (isTv) tvSubtitle
        else radioError?.let { stringResource(it) } ?: radioMeta

        val scheme = MaterialTheme.colorScheme
        val artUrl = if (isMusic) musicNow?.artUri?.toString() else null
        val fallbackTint = if (isMusic) scheme.primary else if (isTv) scheme.secondary else scheme.tertiary
        val tint by rememberArtworkTint(artUrl, fallbackTint, reduceAnimations)
        val cardShape = RoundedCornerShape(14.dp)
        val haptic = LocalHapticFeedback.current
        val duration = musicNow?.durationMs ?: 0L

        val glass = scheme.surfaceContainerHigh.copy(alpha = MaterialTheme.transparency.elevatedSurface)
        Surface(
            shape = cardShape,
            color = Color.Transparent,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .clip(cardShape)
                .hubGlass(haze, blurRadius, glass)
                .clickable(onClickLabel = stringResource(R.string.au9_mediahub_open_player)) { onOpen(display) },
        ) {
            Box(
                Modifier.background(
                    Brush.horizontalGradient(listOf(tint.copy(alpha = 0.28f), tint.copy(alpha = 0.06f)))
                )
            ) {
            Row(
                Modifier.padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)).background(scheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(if (isMusic) R.drawable.music_note_24px else if (isTv) R.drawable.tv_24px else R.drawable.ic_glyph_radio),
                        contentDescription = null,
                        tint = scheme.onSecondaryContainer,
                    )
                    val art = artUrl
                    if (art != null) AsyncImage(model = art, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    val logo = if (isTv) lastTv?.logoUrl?.takeIf { it.isNotBlank() } else null
                    if (logo != null) {
                        AsyncImage(
                            model = logo,
                            contentDescription = null,
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().padding(4.dp),
                        )
                    }
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (subtitle.isNotEmpty()) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (!isMusic && !isTv && radioError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        when {
                            isMusic -> musicVm.togglePlayPause()
                            isTv -> if (tvPlaying) tvVm.controller.pause() else tvVm.controller.resume()
                            else -> radioVm.togglePlayPause()
                        }
                    },
                    shape = CircleShape,
                    color = scheme.onSurface,
                    contentColor = scheme.surface,
                    modifier = Modifier.size(48.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painterResource(if (playing) R.drawable.pause_24px else R.drawable.play_arrow_24px),
                            contentDescription = stringResource(if (playing) R.string.au_music_pause else R.string.hc_play),
                        )
                    }
                }
                if (isMusic) {
                    IconButton(onClick = { musicVm.next() }) {
                        Icon(painterResource(R.drawable.skip_next_24px), contentDescription = stringResource(R.string.hc_next))
                    }
                } else {
                    if (!isTv) {
                        IconButton(onClick = {
                            radioVm.toggleRecording(context)?.let { android.widget.Toast.makeText(context, context.getString(it), android.widget.Toast.LENGTH_SHORT).show() }
                        }) {
                            Icon(
                                painterResource(if (isRecording) R.drawable.radio_button_checked_24px else R.drawable.radio_button_unchecked_24px),
                                contentDescription = stringResource(if (isRecording) R.string.au2_radio2_stop_recording else R.string.au2_radio2_record),
                                tint = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    // a radio or TV stream has no next track: stop ends it and removes the bar
                    IconButton(onClick = { if (isTv) tvVm.stop() else radioVm.stop() }) {
                        Icon(painterResource(R.drawable.close_24px), contentDescription = stringResource(R.string.hc_stop))
                    }
                }
            }
            if (isMusic && duration > 0L) {
                val frac = (positionMs.toFloat() / duration).coerceIn(0f, 1f)
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(scheme.onSurface.copy(alpha = 0.08f))) {
                    Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(scheme.primary))
                }
            }
            }
        }
    }
}

private fun Modifier.hubGlass(haze: HazeState, blur: Int, color: Color): Modifier {
    return if (blur > 0) {
        hazeEffect(haze) {
            blurRadius = blur.dp
            backgroundColor = color
        }.background(color)
    } else {
        background(color)
    }
}
