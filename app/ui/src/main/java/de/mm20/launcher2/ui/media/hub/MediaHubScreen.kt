package de.mm20.launcher2.ui.media.hub

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
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
import de.mm20.launcher2.ui.media.video.VideoScreen
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

/** Telos Media. [space] is music, radio or video; empty = the space that was open last. */
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
}

/**
 * Telos Media: Telos Music, Telos Radio and Telos Video as three spaces, switched with a pill at
 * the top. The screens are the ones of the three apps. Only the open space is composed, its
 * view models (library, player connection) stay while another space is open, and its saved state
 * (tab, search) is restored when it comes back. A mini player for music and radio sits at the
 * bottom (not on the video space).
 */
@Composable
fun MediaHubScreen(initialSpace: String = "") {
    val context = LocalContext.current
    val settings: CommsSettings = koinInject()
    val backStack = LocalBackStack.current
    val disabled by settings.disabledVirtualApps.collectAsState(emptySet())

    // spaces whose app was removed in the Store are not offered (all of them if none is left)
    val available = MediaSpace.entries.filter { it.appKey !in disabled }.ifEmpty { MediaSpace.entries.toList() }

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
    // the mini player follows the players also while their space is not open
    LaunchedEffect(Unit) {
        runCatching { musicVm.connect(context) }
        runCatching { radioVm.initialize(context) }
    }

    var openMusicPlayer by remember { mutableStateOf(false) }
    val stateHolder = rememberSaveableStateHolder()

    Surface(
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) {
                IconButton(onClick = { backStack.removeLastOrNull() }, modifier = Modifier.align(Alignment.CenterStart)) {
                    Icon(painterResource(R.drawable.arrow_back_24px), contentDescription = stringResource(R.string.hc_back))
                }
                if (current != null) {
                    SpacePill(
                        spaces = available,
                        selected = current,
                        onSelect = { space = it.id },
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (current != null) {
                    stateHolder.SaveableStateProvider(current.id) {
                        CompositionLocalProvider(LocalInMediaHub provides true) {
                            when (current) {
                                MediaSpace.Music -> MusicScreen(
                                    openNowPlaying = openMusicPlayer,
                                    onOpenNowPlayingConsumed = { openMusicPlayer = false },
                                )
                                MediaSpace.Radio -> RadioDashboardScreen()
                                MediaSpace.Video -> VideoScreen()
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
                    onOpen = { target ->
                        if (target == MediaSpace.Music) openMusicPlayer = true
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

/** The three-segment switch, with the selected segment filled (animated) */
@Composable
private fun SpacePill(
    spaces: List<MediaSpace>,
    selected: MediaSpace,
    onSelect: (MediaSpace) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier,
    ) {
        Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            for (item in spaces) {
                val isSelected = item == selected
                val container by animateColorAsState(
                    if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                    label = "mediaHubSegment",
                )
                val content by animateColorAsState(
                    if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "mediaHubSegmentContent",
                )
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(container)
                        .clickable(role = Role.Tab) { onSelect(item) }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .animateContentSize(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(painterResource(item.icon), contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(item.label),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = content,
                        maxLines = 1,
                    )
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
    onOpen: (MediaSpace) -> Unit,
    modifier: Modifier = Modifier,
) {
    val musicNow by musicVm.nowPlaying.collectAsStateWithLifecycle()
    val musicPlaying by musicVm.isPlaying.collectAsStateWithLifecycle()
    val radioVisible by radioVm.isVisible.collectAsStateWithLifecycle()
    val radioPlaying by radioVm.isPlaying.collectAsStateWithLifecycle()
    val stationName by radioVm.stationName.collectAsStateWithLifecycle()
    val radioMeta by radioVm.nowPlayingMetadata.collectAsStateWithLifecycle()
    val radioError by radioVm.error.collectAsStateWithLifecycle()

    val hasMusic = musicNow != null
    val shown: MediaSpace? = when {
        space == MediaSpace.Video -> null
        space == MediaSpace.Music && hasMusic -> MediaSpace.Music
        space == MediaSpace.Radio && radioVisible -> MediaSpace.Radio
        musicPlaying && hasMusic -> MediaSpace.Music
        radioPlaying && radioVisible -> MediaSpace.Radio
        hasMusic -> MediaSpace.Music
        radioVisible -> MediaSpace.Radio
        else -> null
    }

    // keeps the last content while the bar slides out
    var last by remember { mutableStateOf(MediaSpace.Music) }
    LaunchedEffect(shown) { if (shown != null) last = shown }
    val display = shown ?: last

    AnimatedVisibility(
        visible = shown != null,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier,
    ) {
        val isMusic = display == MediaSpace.Music
        val playing = if (isMusic) musicPlaying else radioPlaying
        val unknownStation = stringResource(R.string.au_radio_unknown_station)
        val unknownTitle = stringResource(R.string.au9_mediahub_unknown_title)
        val title = if (isMusic) musicNow?.title?.ifBlank { unknownTitle } ?: unknownTitle
        else stationName.ifEmpty { unknownStation }
        val subtitle = if (isMusic) musicNow?.artist.orEmpty()
        else radioError?.let { stringResource(it) } ?: radioMeta

        Surface(
            shape = RoundedCornerShape(20.dp),
            // translucent, the lists below show through a little
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
            tonalElevation = 3.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .clickable(onClickLabel = stringResource(R.string.au9_mediahub_open_player)) { onOpen(display) },
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(if (isMusic) R.drawable.music_note_24px else R.drawable.ic_glyph_radio),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    val art = if (isMusic) musicNow?.artUri?.toString() else null
                    if (art != null) AsyncImage(model = art, contentDescription = null, modifier = Modifier.fillMaxSize())
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (subtitle.isNotEmpty()) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (!isMusic && radioError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                IconButton(onClick = { if (isMusic) musicVm.togglePlayPause() else radioVm.togglePlayPause() }) {
                    Icon(
                        painterResource(if (playing) R.drawable.pause_24px else R.drawable.play_arrow_24px),
                        contentDescription = stringResource(if (playing) R.string.au_music_pause else R.string.hc_play),
                    )
                }
                if (isMusic) {
                    IconButton(onClick = { musicVm.next() }) {
                        Icon(painterResource(R.drawable.skip_next_24px), contentDescription = stringResource(R.string.hc_next))
                    }
                } else {
                    // a radio stream has no next track: stop ends the radio and removes the bar
                    IconButton(onClick = { radioVm.stop() }) {
                        Icon(painterResource(R.drawable.close_24px), contentDescription = stringResource(R.string.hc_stop))
                    }
                }
            }
        }
    }
}
