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
import de.mm20.launcher2.preferences.comms.CommsSettings
import de.mm20.launcher2.ui.R
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
    Tv("tv", "telos_tv_app://tv", R.drawable.tv_24px, R.string.au12_tvui_space_tv),
    Video("video", "telos_video_app://video", R.drawable.video_library_24px, R.string.au9_mediahub_space_video),
}

/**
 * Telos Media: Telos Music, Telos Radio, Telos TV and Telos Video as four spaces, switched with a pill at
 * the top. The screens are the ones of the three apps. Only the open space is composed, its
 * view models (library, player connection) stay while another space is open, and its saved state
 * (tab, search) is restored when it comes back. A mini player for music, radio and TV sits at the
 * bottom (not on the video space, and not on the TV space while the full TV player is open).
 */
@Composable
fun MediaHubScreen(initialSpace: String = "") {
    val context = LocalContext.current
    val settings: CommsSettings = koinInject()
    val backStack = LocalBackStack.current

    // All four spaces are always offered: Telos Media is one app, and hiding the older Music or Radio entries
    // in the Store only removes their icons (they are hidden by default), never the space itself
    val available = MediaSpace.entries.toList()

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
    val reduceAnimations by koinInject<PerformanceSettings>().reduceAnimations.collectAsState(false)

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

    Surface(
        color = scheme.surface,
        contentColor = scheme.onSurface,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.14f), scheme.surface)))
                .statusBarsPadding()
        ) {
            // soft scrim behind the floating controls
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(scheme.surface.copy(alpha = 0.55f), Color.Transparent)))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    onClick = { backStack.removeLastOrNull() },
                    shape = CircleShape,
                    color = scheme.surfaceContainerHigh.copy(alpha = 0.8f),
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size(44.dp)
                        .border(1.dp, scheme.outline.copy(alpha = 0.2f), CircleShape),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.arrow_back_24px), contentDescription = stringResource(R.string.hc_back))
                    }
                }
                if (current != null) {
                    SpacePill(
                        spaces = available,
                        selected = current,
                        onSelect = { space = it.id },
                        reduceAnimations = reduceAnimations,
                        modifier = Modifier.padding(start = 52.dp).wrapContentWidth(Alignment.CenterHorizontally),
                    )
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
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
                            CompositionLocalProvider(LocalInMediaHub provides true) {
                                when (target) {
                                    MediaSpace.Music -> MusicScreen(
                                        openNowPlaying = openMusicPlayer,
                                        onOpenNowPlayingConsumed = { openMusicPlayer = false },
                                    )
                                    MediaSpace.Radio -> RadioDashboardScreen()
                                    MediaSpace.Tv -> TvScreen()
                                    MediaSpace.Video -> VideoScreen()
                                }
                            }
                        }
                    }
                }
            }

            if (current != null) {
                HubMiniPlayer(
                    space = current,
                    musicVm = musicVm,
                    radioVm = radioVm,
                    tvVm = tvVm,
                    reduceAnimations = reduceAnimations,
                    onOpen = { target ->
                        if (target == MediaSpace.Music) openMusicPlayer = true
                        if (target == MediaSpace.Tv) tvVm.openPlayer()
                        space = target.id
                    },
                    modifier = Modifier.navigationBarsPadding(),
                )
            } else {
                Spacer(Modifier.navigationBarsPadding())
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
    reduceAnimations: Boolean,
    modifier: Modifier = Modifier,
) {
    // four labels do not fit next to the back button on a 360dp phone
    val compact = LocalConfiguration.current.screenWidthDp < 420
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
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (item in spaces) {
                    val isSelected = item == selected
                    val content by animateColorAsState(
                        if (isSelected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                        label = "mediaHubSegmentContent",
                    )
                    val showLabel = isSelected || !compact
                    val label = stringResource(item.label)
                    Row(
                        modifier = Modifier
                            .height(36.dp)
                            .onGloballyPositioned {
                                xs[item] = it.positionInParent().x
                                ws[item] = it.size.width.toFloat()
                            }
                            .clip(CircleShape)
                            .semantics { contentDescription = label; this.selected = isSelected }
                            .clickable(role = Role.Tab) {
                                if (!isSelected) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelect(item)
                            }
                            .padding(horizontal = 12.dp)
                            .animateContentSize(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painterResource(item.icon),
                            contentDescription = null,
                            tint = content,
                            modifier = Modifier.size(18.dp),
                        )
                        if (showLabel) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                color = content,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
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

    val tvChannel by tvVm.controller.currentChannel.collectAsStateWithLifecycle()
    val tvPlaying by tvVm.controller.isPlaying.collectAsStateWithLifecycle()
    val tvPlayerOpen by tvVm.playerOpen.collectAsStateWithLifecycle()

    val hasMusic = musicNow != null
    val hasTv = tvChannel != null
    val shown: MediaSpace? = when {
        space == MediaSpace.Video -> null
        space == MediaSpace.Tv && tvPlayerOpen -> null
        space == MediaSpace.Music && hasMusic -> MediaSpace.Music
        space == MediaSpace.Radio && radioVisible -> MediaSpace.Radio
        space == MediaSpace.Tv && hasTv -> MediaSpace.Tv
        musicPlaying && hasMusic -> MediaSpace.Music
        radioPlaying && radioVisible -> MediaSpace.Radio
        tvPlaying && hasTv -> MediaSpace.Tv
        hasMusic -> MediaSpace.Music
        radioVisible -> MediaSpace.Radio
        hasTv -> MediaSpace.Tv
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
        val cardShape = RoundedCornerShape(24.dp)
        val haptic = LocalHapticFeedback.current
        val duration = musicNow?.durationMs ?: 0L

        Surface(
            shape = cardShape,
            color = scheme.surfaceContainerHigh.copy(alpha = 0.92f),
            tonalElevation = 3.dp,
            shadowElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .clip(cardShape)
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
                    Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(scheme.secondaryContainer),
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
                    color = scheme.primary,
                    contentColor = scheme.onPrimary,
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
