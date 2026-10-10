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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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

    LazyColumn(contentPadding = PaddingValues(bottom = 96.dp), modifier = Modifier.fillMaxSize()) {
        if (genres.isNotEmpty()) item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(selected = genre == null, onClick = { onGenre(null) }, label = { Text(stringResource(R.string.au10_music_genre_all)) })
                }
                items(genres) { g ->
                    FilterChip(selected = genre == g, onClick = { onGenre(if (genre == g) null else g) }, label = { Text(g) })
                }
            }
        }
        if (picks.isNotEmpty()) {
            item { ShelfTitle(stringResource(R.string.au10_music_quick_picks)) }
            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    picks.chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { l ->
                                val g = albumGroupOf(l, unknownAlbum)
                                Box(
                                    Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(12.dp))
                                        .combinedClickable(onLongClick = { onShareGroup(g) }, onClick = { onOpenGroup(g) }),
                                ) {
                                    ArtOrNote(l.first().albumArtUri.toString(), Modifier.fillMaxSize())
                                    Box(
                                        Modifier.align(Alignment.BottomStart).fillMaxWidth()
                                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                    ) {
                                        Text(g.title, style = MaterialTheme.typography.labelMedium, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                            repeat(3 - row.size) { Box(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
        if (recent.isNotEmpty()) {
            item { ShelfTitle(stringResource(R.string.au10_music_recent)) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(recent.size) { i ->
                        val t = recent[i]
                        Column(Modifier.width(128.dp).combinedClickable(onClick = { onPlay(recent, i) })) {
                            ArtOrNote(t.albumArtUri.toString(), Modifier.size(128.dp).clip(RoundedCornerShape(12.dp)))
                            Text(t.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                            Text(t.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        if (albums.isNotEmpty()) {
            item { ShelfTitle(stringResource(R.string.hc_albums)) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(albums) { a ->
                        Column(Modifier.width(128.dp).combinedClickable(onLongClick = { onShareGroup(a) }, onClick = { onOpenGroup(a) })) {
                            ArtOrNote(a.tracks.first().albumArtUri.toString(), Modifier.size(128.dp).clip(RoundedCornerShape(12.dp)))
                            Text(a.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                            Text(a.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        if (artists.isNotEmpty()) {
            item { ShelfTitle(stringResource(R.string.au_music_tab_artists)) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(artists) { a ->
                        Column(
                            Modifier.width(104.dp).combinedClickable(onLongClick = { onShareGroup(a) }, onClick = { onOpenGroup(a) }),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            ArtOrNote(a.tracks.first().albumArtUri.toString(), Modifier.size(104.dp).clip(CircleShape))
                            Text(a.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
            }
        }
        if (decades.isNotEmpty()) {
            item { ShelfTitle(stringResource(R.string.au10_music_decades)) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(decades) { d ->
                        Box(
                            Modifier.size(width = 128.dp, height = 72.dp).clip(RoundedCornerShape(12.dp))
                                .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.tertiaryContainer)))
                                .combinedClickable(onLongClick = { onShareGroup(d) }, onClick = { onOpenGroup(d) })
                                .padding(12.dp),
                            contentAlignment = Alignment.BottomStart,
                        ) {
                            Text(d.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShelfTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp))
}
