package de.mm20.launcher2.ui.media.youtube

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import de.mm20.launcher2.ui.R

@Composable
fun YouTubeScreen(music: Boolean, vm: YouTubeViewModel) {
    val context = LocalContext.current
    var query by rememberSaveable(music) { mutableStateOf("") }
    val allEntries by vm.results.collectAsStateWithLifecycle()
    val entries = allEntries[music].orEmpty()
    val searching by vm.searching.collectAsStateWithLifecycle()
    val resolving by vm.resolving.collectAsStateWithLifecycle()
    val buffering by vm.buffering.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val controller by vm.controller.collectAsStateWithLifecycle()
    val now by vm.nowPlaying.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(query, { query = it }, modifier = Modifier.weight(1f), singleLine = true,
                label = { Text(stringResource(R.string.telos_youtube_search)) }, shape = RoundedCornerShape(24.dp))
            IconButton(onClick = { vm.search(context, query, music) }, enabled = query.isNotBlank() && !searching) {
                Icon(painterResource(R.drawable.search_24px), stringResource(R.string.telos_youtube_search))
            }
        }
        if (!music && now?.mediaMetadata?.extras?.getBoolean("audioOnly") == false && controller != null) {
            AndroidView(factory = { PlayerView(it).apply { player = controller; useController = true } },
                update = { it.player = controller }, onRelease = { it.player = null },
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9).clip(RoundedCornerShape(20.dp)))
        }
        if (searching || resolving || buffering) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(stringResource(if (resolving || buffering) R.string.telos_youtube_preparing else R.string.telos_youtube_searching), style = MaterialTheme.typography.labelMedium)
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (entries.isEmpty() && !searching) {
            Text(stringResource(R.string.telos_youtube_intro), style = MaterialTheme.typography.bodyLarge)
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(entries, key = { it.url }) { entry ->
                Surface(onClick = { vm.play(context, entry, music) }, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(entry.thumbnail, null, contentScale = ContentScale.Crop,
                            modifier = Modifier.width(if (music) 76.dp else 128.dp).height(76.dp).clip(RoundedCornerShape(12.dp)))
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(entry.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(entry.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Icon(painterResource(R.drawable.play_arrow_24px), stringResource(R.string.hc_play))
                    }
                }
            }
        }
    }
}

@Composable
fun YouTubeMiniPlayer(vm: YouTubeViewModel, onOpen: (Boolean) -> Unit) {
    val now by vm.nowPlaying.collectAsStateWithLifecycle()
    val playing by vm.playing.collectAsStateWithLifecycle()
    val item = now ?: return
    Surface(onClick = { onOpen(item.mediaMetadata.extras?.getBoolean("audioOnly") == true) },
        shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(item.mediaMetadata.artworkUri, null, contentScale = ContentScale.Crop, modifier = Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(item.mediaMetadata.title?.toString().orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                Text(item.mediaMetadata.artist?.toString().orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = vm::toggle) { Icon(painterResource(if (playing) R.drawable.pause_24px else R.drawable.play_arrow_24px), stringResource(if (playing) R.string.au_music_pause else R.string.hc_play)) }
            IconButton(onClick = vm::stop) { Icon(painterResource(R.drawable.close_24px), stringResource(R.string.hc_stop)) }
        }
    }
}
