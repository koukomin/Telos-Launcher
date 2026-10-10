package de.mm20.launcher2.ui.media.music

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.runtime.getValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.comms.media.MusicTrack
import de.mm20.launcher2.ui.R

/** Genres worth a chip: the most common tags, without legacy numeric ones like "(17)" */
internal fun genresOf(tracks: List<MusicTrack>): List<String> =
    tracks.map { it.genre.trim() }
        .filter { it.isNotEmpty() && !it.matches(Regex("\\(?\\d+\\)?")) }
        .groupingBy { it }.eachCount()
        .entries.sortedByDescending { it.value }.take(20).map { it.key }

internal fun albumGroupOf(list: List<MusicTrack>, unknownAlbum: String) =
    TrackGroup(list.first().album.ifBlank { unknownAlbum }, list.first().artist, list.sortedBy { it.trackNumber })

/** Albums for the quick picks: recently played first, then most played, then newest in the library */
internal fun quickPickAlbums(tracks: List<MusicTrack>, history: Map<Long, MusicHistory.Entry>, max: Int): List<List<MusicTrack>> {
    val byAlbum = tracks.groupBy { it.albumId }
    val recent = byAlbum.values.filter { l -> l.any { history.containsKey(it.id) } }
        .sortedByDescending { l -> l.maxOf { history[it.id]?.lastPlayedMs ?: 0L } }
    val most = byAlbum.values.filter { l -> l.any { history.containsKey(it.id) } }
        .sortedByDescending { l -> l.sumOf { history[it.id]?.count ?: 0 } }
    val newest = byAlbum.values.sortedByDescending { l -> l.maxOf { it.dateAddedSeconds } }
    val out = LinkedHashMap<Long, List<MusicTrack>>()
    val sources = listOf(recent.take(4), most.take(3), newest)
    // interleave so that the grid is a mix of the three
    val iters = sources.map { it.iterator() }
    while (out.size < max && iters.any { it.hasNext() }) {
        for (it in iters) {
            if (out.size >= max) break
            while (it.hasNext()) {
                val l = it.next()
                if (out.putIfAbsent(l.first().albumId, l) == null) break
            }
        }
    }
    return out.values.toList()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun MusicHome(
    allTracks: List<MusicTrack>,
    tracks: List<MusicTrack>,
    history: Map<Long, MusicHistory.Entry>,
    genre: String?,
    onGenre: (String?) -> Unit,
    onOpenGroup: (TrackGroup) -> Unit,
    onShareGroup: (TrackGroup) -> Unit,
    onPlay: (List<MusicTrack>, Int) -> Unit,
    unknownAlbum: String,
    unknownArtist: String,
    songsFormat: String,
    reduceAnimations: Boolean = false,
) {
    val genres = remember(allTracks) { genresOf(allTracks) }
    val picks = remember(tracks, history) { quickPickAlbums(tracks, history, 9) }
    val recent = remember(tracks, history) {
        tracks.filter { history.containsKey(it.id) }.sortedByDescending { history[it.id]?.lastPlayedMs ?: 0L }.take(20)
    }
    val albums = remember(tracks, unknownAlbum) {
        tracks.groupBy { it.albumId }.values.map { albumGroupOf(it, unknownAlbum) }.sortedBy { it.title.lowercase() }.take(30)
    }
    val artists = remember(tracks, unknownArtist, songsFormat) {
        tracks.groupBy { it.artist.ifBlank { unknownArtist } }
            .map { (name, l) -> TrackGroup(name, songsFormat.format(l.size), l.sortedBy { it.title.lowercase() }) }
            .sortedByDescending { it.tracks.size }.take(30)
    }
    val decadeFormat = stringResource(R.string.au10_music_decade_format)
    val decades = remember(tracks, decadeFormat) {
        tracks.filter { it.year > 1900 }.groupBy { it.year / 10 * 10 }.toSortedMap(compareByDescending<Int> { it })
            .map { (d, l) -> TrackGroup(decadeFormat.format(d), songsFormat.format(l.size), l.sortedBy { it.year }) }
    }
    val haptic = LocalHapticFeedback.current

    LazyColumn(
        contentPadding = PaddingValues(bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (genres.isNotEmpty()) item(key = "genres") {
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item(key = "all") {
                    GenrePill(stringResource(R.string.au10_music_genre_all), genre == null, reduceAnimations) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onGenre(null)
                    }
                }
                items(genres, key = { it }) { g ->
                    GenrePill(g, genre == g, reduceAnimations) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onGenre(if (genre == g) null else g)
                    }
                }
            }
        }
        if (picks.isNotEmpty()) item(key = "picks") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ShelfTitle(stringResource(R.string.au10_music_quick_picks))
                Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    picks.chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { l ->
                                val g = albumGroupOf(l, unknownAlbum)
                                Box(
                                    Modifier.weight(1f).aspectRatio(1f)
                                        .pressScale(reduceAnimations, onClick = { onOpenGroup(g) }, onLongClick = { onShareGroup(g) })
                                        .clip(RoundedCornerShape(20.dp)),
                                ) {
                                    ArtOrNote(l.first().albumArtUri.toString(), Modifier.fillMaxSize())
                                    Box(
                                        Modifier.align(Alignment.BottomStart).fillMaxWidth()
                                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))))
                                            .padding(start = 10.dp, end = 10.dp, top = 24.dp, bottom = 8.dp),
                                    ) {
                                        Text(g.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                            repeat(3 - row.size) { Box(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
        if (recent.isNotEmpty()) item(key = "recent") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ShelfTitle(stringResource(R.string.au10_music_recent))
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(recent.size, key = { recent[it].id }) { i ->
                        val t = recent[i]
                        ArtCard(t.albumArtUri.toString(), t.title, t.artist, reduceAnimations, onClick = { onPlay(recent, i) })
                    }
                }
            }
        }
        if (albums.isNotEmpty()) item(key = "albums") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ShelfTitle(stringResource(R.string.hc_albums))
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(albums, key = { it.tracks.first().albumId }) { a ->
                        ArtCard(a.tracks.first().albumArtUri.toString(), a.title, a.subtitle, reduceAnimations, onClick = { onOpenGroup(a) }, onLongClick = { onShareGroup(a) })
                    }
                }
            }
        }
        if (artists.isNotEmpty()) item(key = "artists") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ShelfTitle(stringResource(R.string.au_music_tab_artists))
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(artists, key = { it.title }) { a ->
                        Column(
                            Modifier.width(112.dp).pressScale(reduceAnimations, onClick = { onOpenGroup(a) }, onLongClick = { onShareGroup(a) }),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            ArtOrNote(a.tracks.first().albumArtUri.toString(), Modifier.size(112.dp).clip(CircleShape))
                            Text(a.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
            }
        }
        if (decades.isNotEmpty()) item(key = "decades") {
            val scheme = MaterialTheme.colorScheme
            val pairs = remember(scheme) {
                listOf(
                    scheme.primaryContainer to scheme.tertiaryContainer,
                    scheme.secondaryContainer to scheme.primaryContainer,
                    scheme.tertiaryContainer to scheme.secondaryContainer,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ShelfTitle(stringResource(R.string.au10_music_decades))
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(decades.size, key = { decades[it].title }) { i ->
                        val d = decades[i]
                        val (c1, c2) = pairs[i % pairs.size]
                        Box(
                            Modifier.size(width = 156.dp, height = 96.dp)
                                .pressScale(reduceAnimations, onClick = { onOpenGroup(d) }, onLongClick = { onShareGroup(d) })
                                .clip(RoundedCornerShape(20.dp))
                                .background(Brush.linearGradient(listOf(c1, c2)))
                                .padding(14.dp),
                            contentAlignment = Alignment.BottomStart,
                        ) {
                            Column {
                                Text(d.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = scheme.onPrimaryContainer)
                                Text(d.subtitle, style = MaterialTheme.typography.labelMedium, color = scheme.onPrimaryContainer.copy(alpha = 0.75f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtCard(url: String?, title: String, subtitle: String, reduce: Boolean, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    Column(Modifier.width(148.dp).pressScale(reduce, onClick, onLongClick)) {
        ArtOrNote(url, Modifier.size(148.dp).clip(RoundedCornerShape(20.dp)))
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
        Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun GenrePill(label: String, selected: Boolean, reduce: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val spec = if (reduce) snap<Color>() else tween(200)
    val bg by animateColorAsState(if (selected) scheme.primary else scheme.surfaceContainerHigh, spec, label = "pillBg")
    val fg by animateColorAsState(if (selected) scheme.onPrimary else scheme.onSurface, spec, label = "pillFg")
    Box(
        Modifier.height(40.dp).clip(CircleShape).background(bg).pressScale(reduce, onClick).padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium, color = fg, maxLines = 1)
    }
}

@Composable
private fun ShelfTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 20.dp))
}
