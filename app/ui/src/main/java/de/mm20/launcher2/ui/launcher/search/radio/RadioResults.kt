package de.mm20.launcher2.ui.launcher.search.radio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import de.mm20.launcher2.comms.model.RadioStation
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.comms.RadioViewModel
import de.mm20.launcher2.ui.launcher.search.common.list.ListItemSurface

/**
 * Favorite stations of Telos Radio that match the search. A tap starts the station right away
 * (through the same player as the radio screen); tapping the playing station stops it.
 */
fun LazyListScope.RadioResults(stations: List<RadioStation>, reverse: Boolean) {
    stations.forEachIndexed { index, station ->
        item(key = "radio-${station.id}") {
            val context = LocalContext.current
            val player: RadioViewModel = viewModel()
            LaunchedEffect(Unit) { player.initialize(context) }
            val isPlaying by player.isPlaying.collectAsStateWithLifecycle()
            val playingName by player.stationName.collectAsStateWithLifecycle()
            val visible by player.isVisible.collectAsStateWithLifecycle()
            // the player only knows the title of the media item, so the name identifies the station
            val active = visible && isPlaying && playingName == station.name
            val toggle = { if (active) player.stop() else player.playStation(station) }
            ListItemSurface(isFirst = index == 0, isLast = index == stations.lastIndex, reverse = reverse) {
                Row(
                    Modifier.fillMaxWidth().clickable { toggle() }.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (station.faviconUrl.isNotBlank()) {
                            AsyncImage(model = station.faviconUrl, contentDescription = null, modifier = Modifier.fillMaxSize())
                        } else {
                            Icon(
                                painter = painterResource(R.drawable.music_note_24px),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(
                        station.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                    )
                    IconButton(onClick = { toggle() }) {
                        Icon(
                            painter = painterResource(if (active) R.drawable.close_24px else R.drawable.play_arrow_24px),
                            contentDescription = stringResource(
                                if (active) R.string.au5_radiosearch_stop else R.string.au5_radiosearch_play,
                                station.name,
                            ),
                        )
                    }
                }
            }
        }
    }
}
