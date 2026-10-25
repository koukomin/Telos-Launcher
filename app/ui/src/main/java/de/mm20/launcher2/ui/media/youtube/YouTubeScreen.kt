package de.mm20.launcher2.ui.media.youtube

import kotlinx.coroutines.launch
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.mm20.launcher2.comms.youtube.YouTubePlaylistSummary
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import de.mm20.launcher2.ui.R

@Composable
fun YouTubeScreen(music: Boolean, vm: YouTubeViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by rememberSaveable(music) { mutableStateOf("") }
    var accountPanel by rememberSaveable { mutableStateOf(false) }
    var browserUnavailable by rememberSaveable { mutableStateOf(false) }
    val allEntries by vm.results.collectAsStateWithLifecycle()
    val favorites by vm.library.favorites.collectAsStateWithLifecycle()
    val playlists by vm.library.playlists.collectAsStateWithLifecycle()
    val userPlaylists by vm.userPlaylists.collectAsStateWithLifecycle(emptyList<de.mm20.launcher2.comms.youtube.YouTubePlaylistSummary>())
    val sessionExpired by vm.sessionExpired.collectAsStateWithLifecycle()

    LaunchedEffect(music) {
        vm.loadUserPlaylists(context)
        vm.loadHome(context, music)
    }

    var shelf by rememberSaveable(music) { mutableStateOf("search") }
    var playlistTarget by remember { mutableStateOf<de.mm20.launcher2.comms.youtube.YouTubeEntry?>(null) }
    var playlistName by rememberSaveable { mutableStateOf("") }
    val entries = when {
        shelf == "search" || shelf.startsWith("online:") -> allEntries[music].orEmpty()
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
                Icon(
                    painterResource(R.drawable.open_in_new_24px),
                    stringResource(R.string.telos_youtube_browser_account)
                )
            }
        },
    ) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive((if (music) 360.dp else 320.dp) * androidx.compose.ui.platform.LocalDensity.current.fontScale.coerceAtLeast(1f)),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (sessionExpired) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Η συνεδρία στο YouTube έληξε",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = "Παρακαλώ συνδεθείτε ξανά για πρόσβαση στις λίστες σας.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                                )
                            }
                            TextButton(onClick = { accountPanel = true }) {
                                Text("Sign In", color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    de.mm20.launcher2.ui.component.TelosSearchTopBar(
                        query,
                        { query = it },
                        stringResource(R.string.telos_youtube_search),
                        modifier = Modifier.padding(vertical = 4.dp),
                        onSearch = {
                            if (query.isNotBlank() && !searching) {
                                shelf = "search"
                                vm.search(context, query, music)
                            }
                        },
                        trailing = {
                            IconButton(
                                onClick = {
                                    shelf = "search"
                                    vm.search(context, query, music)
                                },
                                enabled = query.isNotBlank() && !searching
                            ) {
                                Icon(
                                    painterResource(R.drawable.search_24px),
                                    stringResource(R.string.telos_youtube_search)
                                )
                            }
                        },
                    )

                    // YouTube Online Library Glass Cards
                    if (userPlaylists.isNotEmpty()) {
                        YouTubePlaylistChipsRow(
                            userPlaylists = userPlaylists,
                            selectedUrl = if (shelf.startsWith("online:")) shelf.removePrefix("online:") else null,
                            onSelectPlaylist = { pl ->
                                shelf = "online:${pl.url}"
                                vm.selectPlaylist(context, pl.url, music)
                            }
                        )
                    }

                    // Flow-style Navigation Filter Bar
                    run {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                FilterChip(selected = shelf == "search" && query.isBlank(),
                                    onClick = { shelf = "search"; query = ""; vm.loadHome(context, music, refresh = true) },
                                    label = { Text(stringResource(R.string.au10_music_tab_home)) })
                            }
                            item {
                                FilterChip(
                                    selected = shelf == "search",
                                    onClick = { shelf = "search" },
                                    label = { Text(stringResource(R.string.telos_youtube_search)) }
                                )
                            }
                            item {
                                FilterChip(
                                    selected = shelf == "favorites",
                                    onClick = { shelf = "favorites" },
                                    label = { Text(stringResource(R.string.hc_favorites)) }
                                )
                            }
                            items(userPlaylists, key = { "online:${it.id}" }) { pl ->
                                FilterChip(
                                    selected = shelf == "online:${pl.url}",
                                    onClick = {
                                        shelf = "online:${pl.url}"
                                        vm.selectPlaylist(context, pl.url, music)
                                    },
                                    label = { Text("❤️ ${pl.title}") }
                                )
                            }
                            items(playlists.keys.toList(), key = { it }) { name ->
                                InputChip(
                                    selected = shelf == "playlist:$name",
                                    onClick = { shelf = "playlist:$name" },
                                    label = { Text(name) },
                                    trailingIcon = {
                                        IconButton(onClick = {
                                            vm.library.delete(name)
                                            if (shelf == "playlist:$name") shelf = "search"
                                        }) {
                                            Icon(
                                                painterResource(R.drawable.close_24px),
                                                stringResource(R.string.hc_delete),
                                                Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                )
                            }
                        }
                        if (shelf != "search" && entries.isNotEmpty()) {
                            Button(
                                onClick = { vm.play(context, entries.first(), true, entries) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Icon(painterResource(R.drawable.play_arrow_24px), contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.hc_play))
                            }
                        }
                    }

                    if (music && shelf != "search") {
                        Text(
                            stringResource(R.string.telos_youtube_library_local),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (!music && now?.mediaMetadata?.extras?.getBoolean("audioOnly") == false && controller != null) {
                        AndroidView(
                            factory = { PlayerView(it).apply { player = controller; useController = true } },
                            update = { it.player = controller },
                            onRelease = { it.player = null },
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9)
                                .clip(RoundedCornerShape(24.dp))
                        )
                    }

                    if (searching || resolving || buffering) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(
                            stringResource(if (resolving || buffering) R.string.telos_youtube_preparing else R.string.telos_youtube_searching),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    message?.let { Text(it, color = MaterialTheme.colorScheme.error) }

                    if (entries.isEmpty() && !searching) {
                        Text(
                            stringResource(R.string.telos_youtube_intro),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Flow-style Media Cards
            gridItems(entries.distinctBy { it.url }, key = { it.url }) { entry ->
                FlowMediaCard(
                    entry = entry,
                    music = music,
                    isFavorite = favorites.any { it.url == entry.url },
                    onPlay = { vm.play(context, entry, music, entries) },
                    onFavorite = { vm.library.favorite(entry) },
                    onAddPlaylist = { playlistTarget = entry; playlistName = "" },
                    onRadio = { shelf = "search"; vm.artistRadio(context, entry) },
                    onMoodRadio = { mood -> shelf = "search"; vm.moodRadio(context, entry, mood) },
                    onDeleteFromPlaylist = {
                        if (shelf != "search" && shelf != "favorites") {
                            vm.library.remove(shelf.removePrefix("playlist:"), entry)
                        }
                    }
                )
            }
        }
    }

    playlistTarget?.let { entry ->
        AlertDialog(
            onDismissRequest = { playlistTarget = null },
            title = { Text(stringResource(R.string.telos_youtube_add_playlist)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        playlistName,
                        { playlistName = it },
                        label = { Text(stringResource(R.string.telos_youtube_playlist_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    LazyColumn(Modifier.heightIn(max = 220.dp)) {
                        items(playlists.keys.toList()) { name ->
                            TextButton(
                                onClick = {
                                    vm.library.add(name, entry)
                                    playlistTarget = null
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(name)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = playlistName.isNotBlank(),
                    onClick = {
                        vm.library.add(playlistName, entry)
                        playlistTarget = null
                    }
                ) {
                    Text(stringResource(R.string.hc_add))
                }
            },
            dismissButton = {
                TextButton(onClick = { playlistTarget = null }) {
                    Text(stringResource(R.string.hc_cancel))
                }
            }
        )
    }

    var loginPanel by rememberSaveable { mutableStateOf(false) }

    if (loginPanel) {
        YouTubeLoginDialog(
            onDismiss = { loginPanel = false },
            onSuccess = {
                loginPanel = false
                android.widget.Toast.makeText(context, "Successfully logged in to YouTube!", android.widget.Toast.LENGTH_SHORT).show()
                vm.reconnect(context)
                vm.loadHome(context, music, refresh = true)
            }
        )
    }

    if (accountPanel) {
        var importUrl by remember { mutableStateOf("") }
        var importing by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { accountPanel = false },
            title = { Text(stringResource(R.string.telos_youtube_browser_account)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.telos_youtube_browser_account_info))
                    Text(stringResource(R.string.telos_youtube_browser_native_limit), style = MaterialTheme.typography.bodySmall)

                    Button(
                        onClick = { loginPanel = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Sign In (Web Session)")
                    }

                    if (browserUnavailable) {
                        Text(stringResource(R.string.telos_youtube_browser_unavailable), color = MaterialTheme.colorScheme.error)
                    }
                    listOf(false, true).forEach { browserMusic ->
                        OutlinedButton(
                            onClick = {
                                browserUnavailable = !YouTubeBrowserAccount.open(context, browserMusic)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(if (browserMusic) R.string.telos_youtube_browser_music else R.string.telos_youtube_browser_video))
                        }
                    }

                    TextButton(onClick = {
                        val cookies = android.webkit.CookieManager.getInstance()
                        listOf("https://youtube.com", "https://www.youtube.com", "https://music.youtube.com").forEach { origin ->
                            cookies.getCookie(origin).orEmpty().split(';').map { it.substringBefore('=').trim() }.filter { it.isNotBlank() }.forEach { name ->
                                cookies.setCookie(origin, "$name=; Max-Age=0; Path=/; Secure")
                                cookies.setCookie(origin, "$name=; Max-Age=0; Path=/; Secure; Domain=.youtube.com")
                            }
                        }
                        scope.launch {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                de.mm20.launcher2.comms.youtube.YouTubeAccountManager.clear(context.applicationContext)
                                cookies.flush()
                            }
                            vm.userPlaylists.value = emptyList()
                            vm.selectedPlaylistTracks.value = null
                            vm.sessionExpired.value = false
                            vm.results.value = emptyMap()
                            vm.loadHome(context, music, refresh = true)
                        }
                    }) { Text(stringResource(R.string.hc_sign_out)) }
                    HorizontalDivider()
                    Text("1-Click Import YouTube Playlist", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("Paste any public or unlisted YouTube playlist link to import all its tracks directly into Telos Media:", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = importUrl,
                        onValueChange = { importUrl = it },
                        label = { Text("YouTube Playlist URL") },
                        placeholder = { Text("https://www.youtube.com/playlist?list=...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        enabled = importUrl.contains("list=") && !importing,
                        onClick = {
                            importing = true
                            vm.importPlaylistUrl(importUrl) { ok ->
                                importing = false
                                if (ok) {
                                    android.widget.Toast.makeText(context, "Playlist imported successfully!", android.widget.Toast.LENGTH_SHORT).show()
                                    accountPanel = false
                                } else {
                                    android.widget.Toast.makeText(context, "Could not fetch playlist. Check URL or privacy.", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (importing) "Importing..." else "Import Playlist")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { accountPanel = false }) {
                    Text(stringResource(R.string.telos_youtube_browser_close))
                }
            }
        )
    }
}

@Composable
internal fun FlowMediaCard(
    entry: de.mm20.launcher2.comms.youtube.YouTubeEntry,
    music: Boolean,
    isFavorite: Boolean,
    onPlay: () -> Unit,
    onFavorite: () -> Unit,
    onAddPlaylist: () -> Unit,
    onRadio: () -> Unit,
    onMoodRadio: (String) -> Unit,
    onDeleteFromPlaylist: () -> Unit,
) {
    var menuOpen by remember(entry.url) { mutableStateOf(false) }

    Surface(
        onClick = onPlay,
        shape = RoundedCornerShape(if (music) 12.dp else 8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            if (!music) {
                AsyncImage(model = entry.thumbnail, contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)))
            }
            Row(Modifier.fillMaxWidth().padding(if (music) 10.dp else 12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (music) AsyncImage(model = entry.thumbnail, contentDescription = null,
                    contentScale = ContentScale.Crop, modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)))
                Column(Modifier.weight(1f).padding(horizontal = if (music) 12.dp else 0.dp)) {
                    Text(entry.title, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text(entry.artist, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        painterResource(R.drawable.more_vert_24px),
                        contentDescription = stringResource(R.string.au10_music_more)
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(if (isFavorite) R.string.hf_files_remove_favorite else R.string.hc_add_to_favorites)) },
                        onClick = { onFavorite(); menuOpen = false }
                    )
                    if (music) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.telos_youtube_add_playlist)) },
                            onClick = { onAddPlaylist(); menuOpen = false }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.telos_youtube_artist_radio)) },
                            onClick = { onRadio(); menuOpen = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Chill Mood Radio") },
                            onClick = { onMoodRadio("Chill"); menuOpen = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Workout Mood Radio") },
                            onClick = { onMoodRadio("Workout"); menuOpen = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Party Mood Radio") },
                            onClick = { onMoodRadio("Party"); menuOpen = false }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.hc_delete)) },
                        onClick = { onDeleteFromPlaylist(); menuOpen = false }
                    )
                }
            }
        }
    }
    }
}

@Composable
fun YouTubePlaylistChipsRow(
    userPlaylists: List<YouTubePlaylistSummary>,
    selectedUrl: String?,
    onSelectPlaylist: (YouTubePlaylistSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (userPlaylists.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.telos_youtube_tab_playlists),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            items(userPlaylists, key = { it.id }) { pl ->
                val isSelected = selectedUrl == pl.url
                Surface(
                    onClick = { onSelectPlaylist(pl) },
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.width(160.dp).heightIn(min = 64.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!pl.thumbnail.isNullOrBlank()) {
                            AsyncImage(
                                model = pl.thumbnail,
                                contentDescription = pl.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                        } else {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        painter = painterResource(R.drawable.music_note_24px),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = pl.title,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = pl.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
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

    Surface(
        onClick = { onOpen(item.mediaMetadata.extras?.getBoolean("audioOnly") == true) },
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shadowElevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = item.mediaMetadata.artworkUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    item.mediaMetadata.title?.toString().orEmpty(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    item.mediaMetadata.artist?.toString().orEmpty(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { if (matches) vm.toggle() else saved?.let { vm.play(context, it, music) } }) {
                Icon(
                    painterResource(if (playing && matches) R.drawable.pause_24px else R.drawable.play_arrow_24px),
                    stringResource(if (playing && matches) R.string.au_music_pause else R.string.hc_play)
                )
            }
            if (matches) {
                IconButton(onClick = vm::stop) {
                    Icon(painterResource(R.drawable.close_24px), stringResource(R.string.hc_stop))
                }
            }
        }
    }
}
