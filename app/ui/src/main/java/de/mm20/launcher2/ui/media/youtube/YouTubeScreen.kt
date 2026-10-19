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
    var accountPanel by rememberSaveable { mutableStateOf(false) }
    var browserUnavailable by rememberSaveable { mutableStateOf(false) }
    val allEntries by vm.results.collectAsStateWithLifecycle()
    val favorites by vm.library.favorites.collectAsStateWithLifecycle()
    val playlists by vm.library.playlists.collectAsStateWithLifecycle()
    var shelf by rememberSaveable(music) { mutableStateOf("search") }
    var playlistTarget by remember { mutableStateOf<de.mm20.launcher2.comms.youtube.YouTubeEntry?>(null) }
    var playlistName by rememberSaveable { mutableStateOf("") }
    val entries = when {
        !music || shelf == "search" -> allEntries[music].orEmpty()
        shelf == "favorites" -> favorites
        else -> playlists[shelf.removePrefix("playlist:")].orEmpty()
    }
    val searching by vm.searching.collectAsStateWithLifecycle()
    val resolving by vm.resolving.collectAsStateWithLifecycle()
    val buffering by vm.buffering.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val controller by vm.controller.collectAsStateWithLifecycle()
    val now by vm.nowPlaying.collectAsStateWithLifecycle()
    de.mm20.launcher2.ui.media.MediaFrame(
        stringResource(if (music) R.string.telos_youtube_music_label else R.string.telos_youtube_label),
        actions = {
            IconButton(onClick = { accountPanel = true }) {
                Icon(painterResource(R.drawable.open_in_new_24px), stringResource(R.string.telos_youtube_browser_account))
            }
        },
    ) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        de.mm20.launcher2.ui.component.TelosSearchTopBar(
            query, { query = it }, stringResource(R.string.telos_youtube_search),
            modifier = Modifier.padding(vertical = 8.dp),
            onSearch = { if (query.isNotBlank() && !searching) run { shelf = "search"; vm.search(context, query, music) } },
            trailing = {
                IconButton(onClick = { run { shelf = "search"; vm.search(context, query, music) } }, enabled = query.isNotBlank() && !searching) {
                    Icon(painterResource(R.drawable.search_24px), stringResource(R.string.telos_youtube_search))
                }
            },
        )
        if (music) {
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(shelf == "search", { shelf = "search" }, label = { Text(stringResource(R.string.telos_youtube_search)) }) }
                item { FilterChip(shelf == "favorites", { shelf = "favorites" }, label = { Text(stringResource(R.string.hc_favorites)) }) }
                items(playlists.keys.toList(), key = { it }) { name ->
                    InputChip(selected = shelf == "playlist:$name", onClick = { shelf = "playlist:$name" }, label = { Text(name) },
                        trailingIcon = { IconButton(onClick = { vm.library.delete(name); if (shelf == "playlist:$name") shelf = "search" }) {
                            Icon(painterResource(R.drawable.close_24px), stringResource(R.string.hc_delete), Modifier.size(18.dp))
                        } })
                }
            }
            if (shelf != "search" && entries.isNotEmpty()) {
                TextButton(onClick = { vm.play(context, entries.first(), true, entries) }) { Text(stringResource(R.string.hc_play)) }
            }
        }
        if (music && shelf != "search") Text(stringResource(R.string.telos_youtube_library_local), style = MaterialTheme.typography.labelSmall)
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
        }
        }
            items(entries, key = { it.url }) { entry ->
                Surface(onClick = { vm.play(context, entry, music, entries) }, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(entry.thumbnail, null, contentScale = ContentScale.Crop,
                            modifier = Modifier.width(if (music || androidx.compose.ui.platform.LocalConfiguration.current.fontScale > 1.3f) 64.dp else 112.dp).height(64.dp).clip(RoundedCornerShape(12.dp)))
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(entry.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(entry.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (music) {
                            var menu by remember(entry.url) { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { menu = true }) { Icon(painterResource(R.drawable.more_vert_24px), stringResource(R.string.au10_music_more)) }
                                DropdownMenu(menu, { menu = false }) {
                                    DropdownMenuItem(text = { Text(stringResource(if (favorites.any { it.url == entry.url }) R.string.hf_files_remove_favorite else R.string.hc_add_to_favorites)) },
                                        onClick = { vm.library.favorite(entry); menu = false })
                                    DropdownMenuItem(text = { Text(stringResource(R.string.telos_youtube_add_playlist)) },
                                        onClick = { playlistTarget = entry; playlistName = ""; menu = false })
                                    DropdownMenuItem(text = { Text(stringResource(R.string.telos_youtube_artist_radio)) },
                                        onClick = { shelf = "search"; vm.artistRadio(context, entry); menu = false })
                                    if (shelf != "search" && shelf != "favorites") DropdownMenuItem(text = { Text(stringResource(R.string.hc_delete)) },
                                        onClick = { vm.library.remove(shelf.removePrefix("playlist:"), entry); menu = false })
                                }
                            }
                        } else Icon(painterResource(R.drawable.play_arrow_24px), stringResource(R.string.hc_play))
                    }
                }
            }
    }
    }
    playlistTarget?.let { entry ->
        AlertDialog(onDismissRequest = { playlistTarget = null }, title = { Text(stringResource(R.string.telos_youtube_add_playlist)) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(playlistName, { playlistName = it }, label = { Text(stringResource(R.string.telos_youtube_playlist_name)) }, singleLine = true)
                androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 240.dp)) {
                    items(playlists.keys.toList()) { name -> TextButton(onClick = { vm.library.add(name, entry); playlistTarget = null }) { Text(name) } }
                }
            } },
            confirmButton = { TextButton(enabled = playlistName.isNotBlank(), onClick = { vm.library.add(playlistName, entry); playlistTarget = null }) { Text(stringResource(R.string.hc_add)) } },
            dismissButton = { TextButton(onClick = { playlistTarget = null }) { Text(stringResource(R.string.hc_cancel)) } })
    }
    if (accountPanel) {
        AlertDialog(onDismissRequest = { accountPanel = false },
            title = { Text(stringResource(R.string.telos_youtube_browser_account)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.telos_youtube_browser_account_info))
                    Text(stringResource(R.string.telos_youtube_browser_native_limit), style = MaterialTheme.typography.bodySmall)
                    if (browserUnavailable) Text(stringResource(R.string.telos_youtube_browser_unavailable), color = MaterialTheme.colorScheme.error)
                    listOf(false, true).forEach { browserMusic ->
                        OutlinedButton(onClick = {
                            browserUnavailable = !YouTubeBrowserAccount.open(context, browserMusic)
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(if (browserMusic) R.string.telos_youtube_browser_music else R.string.telos_youtube_browser_video))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { accountPanel = false }) { Text(stringResource(R.string.telos_youtube_browser_close)) } })
    }
}

@Composable
fun YouTubeMiniPlayer(vm: YouTubeViewModel, music: Boolean, onOpen: (Boolean) -> Unit) {
    val now by vm.nowPlaying.collectAsStateWithLifecycle()
    val playing by vm.playing.collectAsStateWithLifecycle()
    val savedMusic by vm.library.lastMusic.collectAsStateWithLifecycle()
    val savedVideo by vm.library.lastVideo.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val matches = now != null && (now?.mediaMetadata?.extras?.getBoolean("audioOnly") == true) == music
    val saved = if (music) savedMusic else savedVideo
    val item = if (matches) now!! else saved?.let {
        androidx.media3.common.MediaItem.Builder().setMediaId(it.url).setMediaMetadata(
            androidx.media3.common.MediaMetadata.Builder().setTitle(it.title).setArtist(it.artist)
                .setArtworkUri(it.thumbnail?.let(android.net.Uri::parse)).setExtras(android.os.Bundle().apply { putBoolean("audioOnly", music) }).build()).build()
    } ?: return
    Surface(onClick = { onOpen(item.mediaMetadata.extras?.getBoolean("audioOnly") == true) },
        shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(item.mediaMetadata.artworkUri, null, contentScale = ContentScale.Crop, modifier = Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(item.mediaMetadata.title?.toString().orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                Text(item.mediaMetadata.artist?.toString().orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = { if (matches) vm.toggle() else saved?.let { vm.play(context, it, music) } }) { Icon(painterResource(if (playing && matches) R.drawable.pause_24px else R.drawable.play_arrow_24px), stringResource(if (playing && matches) R.string.au_music_pause else R.string.hc_play)) }
            if (matches) IconButton(onClick = vm::stop) { Icon(painterResource(R.drawable.close_24px), stringResource(R.string.hc_stop)) }
        }
    }
}
