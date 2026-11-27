package de.mm20.launcher2.ui.media.stremio

import de.mm20.launcher2.ui.component.telosTechCard
import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import de.mm20.launcher2.comms.stremio.Meta
import de.mm20.launcher2.comms.stremio.StreamItem
import de.mm20.launcher2.comms.stremio.streamKey

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DetailPage(vm: StremioViewModel, d: DetailState) {
    BackHandler { vm.closeDetail() }
    val meta = d.meta
    val preview = d.preview
    val saved by vm.saved.collectAsStateWithLifecycle()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerLow, contentColor = MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().height(240.dp)) {
                AsyncImage(
                    model = meta?.background ?: meta?.poster ?: preview.poster,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.25f), MaterialTheme.colorScheme.surfaceContainerLow))))
                IconButton(onClick = { vm.closeDetail() }, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
                    Icon(painterResource(BaseR.drawable.arrow_back_24px), contentDescription = stringResource(I18nR.string.hc_back), tint = Color.White)
                }
            }
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(meta?.name ?: preview.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                if (preview.type == "movie") WatchMark(vm, preview.id)
                if (meta == null) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    val facts = listOfNotNull(meta.releaseInfo, meta.runtime, meta.imdbRating?.let { "★ $it" }, meta.genres.take(3).joinToString(", ").takeIf { it.isNotBlank() })
                    if (facts.isNotEmpty()) Text(facts.joinToString("  ·  "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!meta.isSeries) {
                            Button(onClick = { vm.showStreams(meta, null) }) {
                                Icon(painterResource(BaseR.drawable.play_arrow_24px), contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(I18nR.string.hc_play))
                            }
                        }
                        val isSaved = saved.any { it.id == meta.id }
                        FilledTonalButton(onClick = { vm.toggleSaved(meta) }) {
                            Icon(painterResource(if (isSaved) BaseR.drawable.star_24px_filled else BaseR.drawable.star_24px), contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(if (isSaved) I18nR.string.au22_stremio_saved else I18nR.string.au22_stremio_save))
                        }
                    }
                    meta.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    if (meta.cast.isNotEmpty()) Text(meta.cast.take(8).joinToString(", "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (meta.isSeries) Episodes(vm, meta)

                    LaunchedEffect(meta.id) {
                        if (!meta.isSeries) {
                            vm.showStreams(meta, null)
                        }
                    }
                    InlineStreamsSection(vm = vm, meta = meta)
                }
                Spacer(Modifier.height(96.dp))
            }
        }
    }
}

@Composable
private fun Episodes(vm: StremioViewModel, meta: Meta) {
    val videoOrder = remember(meta.videos) { de.mm20.launcher2.comms.stremio.StremioProgressState.orderedVideoIds(meta.videos) }
    val seasons = remember(meta) { meta.videos.mapNotNull { it.season }.distinct().sortedBy { if (it == 0) Int.MAX_VALUE else it } }
    var season by rememberSaveable(meta.id) { mutableIntStateOf(seasons.firstOrNull { it != 0 } ?: seasons.firstOrNull() ?: 1) }
    if (seasons.size > 1) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(seasons) { s ->
                FilterChip(selected = season == s, onClick = { season = s }, label = { Text(stringResource(I18nR.string.au22_stremio_season, s)) }, shape = CircleShape)
            }
        }
    }
    val episodes = remember(meta, season) { meta.videos.filter { (it.season ?: 1) == season }.sortedBy { it.episode ?: 0 } }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        episodes.forEach { v ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { vm.showStreams(meta, v) }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (v.thumbnail != null) {
                    AsyncImage(model = v.thumbnail, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.width(104.dp).aspectRatio(1.77f).clip(RoundedCornerShape(8.dp)))
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(listOfNotNull(v.episode?.toString(), v.title).joinToString(". "), style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    WatchMark(vm, meta.id, v.id, videoOrder, v.season, v.episode)
                    v.overview?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
    }
}

@Composable
private fun InlineStreamsSection(vm: StremioViewModel, meta: Meta) {
    val context = LocalContext.current
    val state by vm.streams.collectAsStateWithLifecycle()
    val preferences by androidx.compose.runtime.produceState<de.mm20.launcher2.comms.stremio.StremioPlaybackSettings?>(null) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            de.mm20.launcher2.comms.stremio.StremioPlaybackSettings.load(context)
        }
    }

    val items = state?.items.orEmpty()
    val visibleStreams = remember(items, preferences) {
        preferences?.let { de.mm20.launcher2.comms.stremio.StremioStreamFilter.apply(items, it) } ?: items
    }
    val loading = state?.loading == true

    Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(BaseR.drawable.play_arrow_24px), contentDescription = null, tint = de.mm20.launcher2.ui.component.TelosCyber.NeonCyan, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            TelosCyberHeading { Text(stringResource(I18nR.string.au22_stremio_inline_streams)) }
        }

        when {
            loading -> {
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(I18nR.string.au22_stremio_streams), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            state != null && visibleStreams.isEmpty() -> {
                Text(stringResource(I18nR.string.au22_stremio_no_streams), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
            }
            state != null -> {
                visibleStreams.take(15).forEach { s ->
                    val supported = s.kind != StreamItem.Kind.Unsupported
                    Surface(
                        onClick = {
                            if (supported) {
                                vm.play(context, s, state!!) { opened ->
                                    if (!opened) Toast.makeText(context, I18nR.string.au22_stremio_unsupported, Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth().telosTechCard(
                            borderColor = if (s.streamKey() == state?.lastUsed) de.mm20.launcher2.ui.component.TelosCyber.NeonCyan else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(12.dp),
                            borderWidth = 1.dp,
                            alpha = 0.5f,
                        )
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(s.name ?: s.addonName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Icon(painterResource(BaseR.drawable.play_arrow_24px), contentDescription = null, tint = de.mm20.launcher2.ui.component.TelosCyber.NeonCyan, modifier = Modifier.size(18.dp))
                            }
                            s.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                            Text(
                                when (s.kind) {
                                    StreamItem.Kind.Torrent -> stringResource(I18nR.string.au22_stremio_p2p_warning)
                                    StreamItem.Kind.External -> stringResource(I18nR.string.au22_stremio_opens_browser)
                                    StreamItem.Kind.Unsupported -> stringResource(I18nR.string.au22_stremio_unsupported)
                                    else -> s.addonName
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StreamsSheet(vm: StremioViewModel, state: StreamsState) {
    val context = LocalContext.current
    val preferences by androidx.compose.runtime.produceState<de.mm20.launcher2.comms.stremio.StremioPlaybackSettings?>(null) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            de.mm20.launcher2.comms.stremio.StremioPlaybackSettings.load(context)
        }
    }
    val visibleStreams = remember(state.items, preferences) {
        preferences?.let { de.mm20.launcher2.comms.stremio.StremioStreamFilter.apply(state.items, it) } ?: state.items
    }
    ModalBottomSheet(onDismissRequest = { vm.closeStreams() }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text(stringResource(I18nR.string.au22_stremio_streams), style = MaterialTheme.typography.titleLarge)
            Text(state.title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 8.dp))
            if (state.loading) {
                Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (state.items.isEmpty()) {
                Text(stringResource(I18nR.string.au22_stremio_no_streams), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 16.dp))
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().height(420.dp)) {
                    items(visibleStreams, key = { it.streamKey() + "#" + state.items.indexOf(it) }) { s ->
                        StreamRow(s, last = s.streamKey() == state.lastUsed, title = state.title) {
                            vm.play(context, s, state) { opened ->
                                if (opened) vm.closeStreams()
                                else Toast.makeText(context, I18nR.string.au22_stremio_unsupported, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamRow(s: StreamItem, last: Boolean, title: String, onClick: () -> Unit) {
    val supported = s.kind != StreamItem.Kind.Unsupported
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(enabled = supported, onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(s.name ?: s.addonName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (last) Text(stringResource(I18nR.string.au22_stremio_last_used), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
        StremioStreamExtraActions(s, title)
        s.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis) }
        Text(
            when (s.kind) {
                StreamItem.Kind.Torrent -> stringResource(I18nR.string.au22_stremio_p2p_warning)
                StreamItem.Kind.External -> stringResource(I18nR.string.au22_stremio_opens_browser)
                StreamItem.Kind.Unsupported -> stringResource(I18nR.string.au22_stremio_unsupported)
                else -> s.addonName
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------------------------------------
