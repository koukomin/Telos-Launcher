package de.mm20.launcher2.ui.comms.radio

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import de.mm20.launcher2.comms.model.RadioStation
import de.mm20.launcher2.preferences.ui.PerformanceSettings
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.comms.RadioPlayButton
import de.mm20.launcher2.ui.comms.RadioViewModel
import de.mm20.launcher2.ui.comms.radioStationSubtitle
import org.koin.compose.koinInject

enum class RadioChip { All, Recent, Mine, Browser }

/** Average colour of a station logo, used to tint the card of the playing station. Null when unavailable. */
@Composable
private fun rememberLogoColor(url: String, enabled: Boolean): Color? {
    val context = LocalContext.current
    var color by remember(url) { mutableStateOf<Color?>(null) }
    LaunchedEffect(url, enabled) {
        if (!enabled || url.isBlank()) return@LaunchedEffect
        color = runCatching {
            val request = ImageRequest.Builder(context).data(url).size(32).allowHardware(false).build()
            val result = context.imageLoader.execute(request) as? SuccessResult ?: return@runCatching null
            val bmp = (result.drawable as? BitmapDrawable)?.bitmap ?: return@runCatching null
            averageColor(bmp)
        }.getOrNull()
    }
    return color
}

private fun averageColor(bmp: Bitmap): Color? {
    var r = 0L; var g = 0L; var b = 0L; var n = 0L
    val w = bmp.width; val h = bmp.height
    if (w == 0 || h == 0) return null
    val step = maxOf(1, minOf(w, h) / 16)
    var y = 0
    while (y < h) {
        var x = 0
        while (x < w) {
            val p = bmp.getPixel(x, y)
            if ((p ushr 24) > 128) {
                r += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; b += p and 0xFF; n++
            }
            x += step
        }
        y += step
    }
    if (n == 0L) return null
    return Color((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
}

/** Three bars that move while the station plays. Static when animations are reduced. */
@Composable
fun RadioEqualizer(animate: Boolean, color: Color, modifier: Modifier = Modifier) {
    val phases = if (animate) {
        val t = rememberInfiniteTransition(label = "eq")
        listOf(420, 560, 490).mapIndexed { i, ms ->
            t.animateFloat(
                initialValue = 0.3f, targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(ms, easing = LinearEasing), RepeatMode.Reverse),
                label = "bar$i",
            ).value
        }
    } else listOf(0.6f, 1f, 0.45f)
    Row(modifier.height(16.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        phases.forEach { f ->
            Box(Modifier.width(3.dp).height((16 * f).dp).clip(RoundedCornerShape(1.dp)).background(color))
        }
    }
}

@Composable
private fun Logo(station: RadioStation, modifier: Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (station.faviconUrl.isNotBlank()) {
            AsyncImage(
                model = station.faviconUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f).padding(12.dp),
            )
        } else {
            Icon(painterResource(R.drawable.music_note_24px), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RadioStationCard(
    station: RadioStation,
    player: RadioViewModel,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduce by koinInject<PerformanceSettings>().reduceAnimations.collectAsState(false)
    val playingId by player.stationId.collectAsStateWithLifecycle()
    val playWhenReady by player.playWhenReady.collectAsStateWithLifecycle()
    val playing = playingId == station.id && playWhenReady
    val logoColor = rememberLogoColor(station.faviconUrl, playing)
    val base = MaterialTheme.colorScheme.surfaceVariant
    val bg = if (playing && logoColor != null) logoColor.copy(alpha = 0.28f).compositeOver(base) else base.copy(alpha = 0.5f)
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .combinedClickable(onClick = { player.toggleStation(station) }, onLongClick = onLongClick)
            .padding(10.dp),
    ) {
        Box {
            Logo(station, Modifier.fillMaxWidth().aspectRatio(1f))
            RadioPlayButton(station, player, Modifier.align(Alignment.BottomEnd).padding(6.dp))
        }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (playing) RadioEqualizer(!reduce, MaterialTheme.colorScheme.primary)
            Text(station.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            radioStationSubtitle(station),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Home of the collection: filter chips, a shelf of recently played stations and a two column grid */
@Composable
fun RadioHome(
    favorites: List<RadioStation>,
    player: RadioViewModel,
    chip: RadioChip,
    onChip: (RadioChip) -> Unit,
    onLongClick: (RadioStation) -> Unit,
    header: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val recent = remember(favorites) {
        favorites.filter { it.lastPlayedAt > 0 }.sortedByDescending { it.lastPlayedAt }.take(10)
    }
    val shown = remember(favorites, chip) {
        when (chip) {
            RadioChip.All -> favorites
            RadioChip.Recent -> favorites.filter { it.lastPlayedAt > 0 }.sortedByDescending { it.lastPlayedAt }
            RadioChip.Mine -> favorites.filter { it.id.startsWith("local-") }
            RadioChip.Browser -> favorites.filter { !it.id.startsWith("local-") }
        }
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier,
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { header() }
        item(span = { GridItemSpan(maxLineSpan) }) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(RadioChip.values().toList()) { c ->
                    FilterChip(
                        selected = chip == c,
                        onClick = { onChip(c) },
                        label = {
                            Text(
                                stringResource(
                                    when (c) {
                                        RadioChip.All -> R.string.au10_radio_chip_all
                                        RadioChip.Recent -> R.string.au10_radio_chip_recent
                                        RadioChip.Mine -> R.string.au10_radio_chip_mine
                                        RadioChip.Browser -> R.string.au10_radio_chip_browser
                                    }
                                )
                            )
                        },
                    )
                }
            }
        }
        if (chip == RadioChip.All && recent.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Text(stringResource(R.string.au10_radio_recent_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(recent, key = { it.id }) { s ->
                            RadioStationCard(s, player, { onLongClick(s) }, Modifier.width(140.dp))
                        }
                    }
                }
            }
        }
        items(shown, key = { it.id }) { s ->
            RadioStationCard(s, player, { onLongClick(s) })
        }
    }
}
