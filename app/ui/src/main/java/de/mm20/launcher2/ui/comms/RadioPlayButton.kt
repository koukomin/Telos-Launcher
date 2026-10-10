package de.mm20.launcher2.ui.comms

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.mm20.launcher2.comms.model.RadioStation
import de.mm20.launcher2.ui.R

/**
 * Filled tonal play/pause button of a station. The state is matched by station id, never by name:
 * play when the station is not playing, pause while it plays, and a small progress ring around
 * the pause icon while the stream is loading or buffering. Live streams are paused, not stopped.
 */
@Composable
fun RadioPlayButton(station: RadioStation, player: RadioViewModel, modifier: Modifier = Modifier) {
    val playingId by player.stationId.collectAsStateWithLifecycle()
    val visible by player.isVisible.collectAsStateWithLifecycle()
    val playWhenReady by player.playWhenReady.collectAsStateWithLifecycle()
    val loadingNow by player.isLoading.collectAsStateWithLifecycle()
    val current = playingId == station.id && (visible || loadingNow)
    val active = current && playWhenReady
    val loading = active && loadingNow
    FilledTonalIconButton(onClick = { player.toggleStation(station) }, modifier = modifier) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(if (active) R.drawable.pause_24px else R.drawable.play_arrow_24px),
                contentDescription = stringResource(
                    when {
                        loading -> R.string.au6_radio2_loading
                        active -> R.string.au6_radio2_pause
                        else -> R.string.au5_radiosearch_play
                    },
                    station.name,
                ),
            )
            if (loading) CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
        }
    }
}

/** Host of the stream plus its content type, to tell stations with the same name apart */
fun radioStationSubtitle(station: RadioStation): String {
    val host = runCatching { android.net.Uri.parse(station.streamUrl).host }.getOrNull().orEmpty()
    return listOf(host, station.streamContent).filter { it.isNotBlank() }.joinToString(" · ")
}
