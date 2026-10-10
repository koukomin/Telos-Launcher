package de.mm20.launcher2.ui.launcher.search.radio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
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
import de.mm20.launcher2.ui.comms.RadioPlayButton
import de.mm20.launcher2.ui.comms.RadioViewModel
import de.mm20.launcher2.ui.comms.radioStationSubtitle
import de.mm20.launcher2.ui.launcher.search.common.list.ListItemSurface

/**
 * Favorite stations of Telos Radio that match the search. A tap starts the station right away
 * (through the same player as the radio screen); tapping the playing station pauses it.
 */
fun LazyListScope.RadioResults(stations: List<RadioStation>, reverse: Boolean) {
    stations.forEachIndexed { index, station ->
        item(key = "radio-${station.id}") {
            val context = LocalContext.current
            val player: RadioViewModel = viewModel()
            LaunchedEffect(Unit) { player.initialize(context) }
            val playingId by player.stationId.collectAsStateWithLifecycle()
            val visible by player.isVisible.collectAsStateWithLifecycle()
            val playWhenReady by player.playWhenReady.collectAsStateWithLifecycle()
            // matched by station id: stations can share a name
            val active = visible && playingId == station.id && playWhenReady
            val subtitle = radioStationSubtitle(station)
            ListItemSurface(isFirst = index == 0, isLast = index == stations.lastIndex, reverse = reverse) {
                Row(
                    Modifier.fillMaxWidth().clickable { player.toggleStation(station) }.padding(horizontal = 16.dp, vertical = 8.dp),
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
                    Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                        Text(
                            station.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (subtitle.isNotEmpty()) {
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    RadioPlayButton(station, player)
                }
            }
        }
    }
}
