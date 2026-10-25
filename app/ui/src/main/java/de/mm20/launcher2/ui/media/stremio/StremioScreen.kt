package de.mm20.launcher2.ui.media.stremio

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import de.mm20.launcher2.comms.stremio.Addon
import de.mm20.launcher2.comms.stremio.Meta
import de.mm20.launcher2.comms.stremio.MetaPreview
import de.mm20.launcher2.comms.stremio.StreamItem
import de.mm20.launcher2.comms.stremio.StremioAddons
import de.mm20.launcher2.comms.stremio.streamKey
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.TelosSearchBar
import de.mm20.launcher2.ui.media.MediaFrame

/**
 * The Stremio space of Telos Media: a client for Stremio add-ons (catalogs, search, detail pages, streams), with a
 * local library and a "continue watching" shelf. It is Telos' own implementation of the open add-on protocol and
 * is not affiliated with Stremio. Telos ships no add-on; what an add-on offers is the add-on's responsibility.
 */
@Composable
fun StremioScreen() {
    val vm: StremioViewModel = viewModel()
    val accepted by vm.noticeAccepted.collectAsStateWithLifecycle()
    val addons by vm.addons.collectAsStateWithLifecycle()
    val detail by vm.detail.collectAsStateWithLifecycle()
    val streams by vm.streams.collectAsStateWithLifecycle()
    var showPlaybackSettings by remember { mutableStateOf(false) }
    if (showPlaybackSettings) StremioPlaybackSheet { showPlaybackSettings = false }
    var showActions by remember { mutableStateOf(false) }
    var showNetworkRules by remember { mutableStateOf(false) }
    var showAddons by remember { mutableStateOf(false) }
    var showAccount by remember { mutableStateOf(false) }
    var showTrakt by remember { mutableStateOf(false) }
    val email by vm.accountEmail.collectAsStateWithLifecycle()
    val context = LocalContext.current
    androidx.compose.runtime.LaunchedEffect(email) {
        if (email == null) {
            showAccount = false
            showAddons = false
        }
    }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    androidx.compose.runtime.DisposableEffect(lifecycle, vm) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) vm.refreshAccounts(context, force = true)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    if (showNetworkRules) androidx.compose.material3.AlertDialog(
        onDismissRequest = { showNetworkRules = false },
        title = { Text(stringResource(R.string.dl_settings_rules)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            de.mm20.launcher2.ui.downloads.TransferNetworkPreferences("torrent")
        } },
        confirmButton = { TextButton(onClick = { showNetworkRules = false }) { Text(stringResource(R.string.hc_close)) } },
    )
    MediaFrame(stringResource(R.string.au22_stremio_label), actions = {
        Box {
            IconButton(onClick = { showActions = true }) {
                Icon(painterResource(R.drawable.more_vert_24px), contentDescription = stringResource(R.string.hc_more))
            }
            androidx.compose.material3.DropdownMenu(expanded = showActions, onDismissRequest = { showActions = false }) {
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(stringResource(R.string.settings)) },
                    onClick = { showActions = false; showPlaybackSettings = true },
                )
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(stringResource(R.string.dl_settings_rules)) },
                    onClick = { showActions = false; showNetworkRules = true },
                )
                if (email != null) androidx.compose.material3.DropdownMenuItem(
                    text = { Text(stringResource(R.string.au22_stremio_account)) },
                    onClick = { showActions = false; showAccount = true },
                )
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(stringResource(R.string.au7_trakt_title)) },
                    onClick = { showActions = false; showTrakt = true },
                )
                if (accepted && email != null) androidx.compose.material3.DropdownMenuItem(
                    text = { Text(stringResource(R.string.au22_stremio_addons)) },
                    onClick = { showActions = false; showAddons = true },
                )
            }
        }
    }) {
        Box(Modifier.fillMaxSize()) {
            when {
                email == null -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(stringResource(R.string.au22_stremio_fork_intro), style = MaterialTheme.typography.bodyLarge)
                    AccountSection(vm)
                }
                !accepted -> NoticeCard(onAccept = { vm.acceptNotice() })
                addons.isEmpty() -> EmptyAddons(vm)
                else -> StremioHome(vm)
            }
            detail?.let { d ->
                DetailPage(vm, d)
            }
        }
    }

    if (showAccount && email != null) AccountSheet(vm) { showAccount = false }
    if (showTrakt) de.mm20.launcher2.ui.media.video.TraktDialog {
        showTrakt = false
        vm.refreshAccounts(context, force = true)
    }
    if (showAddons && email != null) AddonsSheet(vm, onDismiss = { showAddons = false })
    streams?.let { s -> StreamsSheet(vm, s) }
}

@Composable
private fun NoticeCard(onAccept: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.Center) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.au22_stremio_notice_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.au22_stremio_notice_text), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onAccept, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.au22_stremio_notice_accept)) }
            }
        }
    }
}

@Composable
private fun EmptyAddons(vm: StremioViewModel) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.au22_stremio_empty), style = MaterialTheme.typography.bodyLarge)
        AddAddonForm(vm, showSuggestion = true)
    }
}

@Composable
private fun AddAddonForm(vm: StremioViewModel, showSuggestion: Boolean) {
    val context = LocalContext.current
    var text by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun add(input: String) {
        busy = true
        error = null
        vm.addAddon(input) { result ->
            busy = false
            if (result == null) text = "" else error = result
        }
    }
    val shownError = error
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(stringResource(R.string.au22_stremio_addon_address)) },
            placeholder = { Text(stringResource(R.string.au22_stremio_addon_address_hint)) },
            singleLine = true,
            isError = error != null,
            supportingText = if (shownError != null) { { Text(shownError) } } else null,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { add(text) }, enabled = text.isNotBlank() && !busy) { Text(stringResource(R.string.au22_stremio_add_addon)) }
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        }
        if (showSuggestion) {
            OutlinedButton(onClick = { add(StremioAddons.CINEMETA) }, enabled = !busy) {
                Text(stringResource(R.string.au22_stremio_add_cinemeta))
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------

@Composable
private fun StremioHome(vm: StremioViewModel) {
    val rows by vm.rows.collectAsStateWithLifecycle()
    val searchRows by vm.searchRows.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val watching by vm.watching.collectAsStateWithLifecycle()
    val saved by vm.saved.collectAsStateWithLifecycle()
    var tab by de.mm20.launcher2.ui.component.rememberPersistentUiState("stremio.tab", 0)

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text(stringResource(R.string.au22_stremio_home)) }, shape = RoundedCornerShape(8.dp))
            FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text(stringResource(R.string.au22_stremio_library)) }, shape = RoundedCornerShape(8.dp))
        }
        if (tab == 0) {
            TelosSearchBar(query, { vm.setQuery(it) }, stringResource(R.string.au22_stremio_search_hint))
            val shown = if (query.isBlank()) rows else searchRows
            LazyColumn(contentPadding = PaddingValues(bottom = 112.dp), verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize()) {
                if (query.isBlank() && watching.isNotEmpty()) item(key = "continue") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        RowTitle(stringResource(R.string.au22_stremio_continue))
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(watching, key = { it.id }) { p ->
                                Poster(vm, MetaPreview(p.id, p.type, p.name, p.poster), caption = p.videoTitle, onClick = { vm.openProgress(p) })
                            }
                        }
                    }
                }
                items(shown, key = { it.key }) { row ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        RowTitle(row.title)
                        val list = row.items
                        if (list == null) {
                            Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) }
                        } else if (list.isEmpty()) {
                            Text(stringResource(R.string.au22_stremio_no_results), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
                        } else {
                            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(list, key = { it.id }) { m -> Poster(vm, m, onClick = { vm.open(m) }) }
                            }
                        }
                    }
                }
            }
        } else {
            if (saved.isEmpty()) {
                Text(stringResource(R.string.au22_stremio_library_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(156.dp * LocalDensity.current.fontScale),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 112.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    gridItems(saved, key = { it.id }) { e -> Poster(vm, MetaPreview(e.id, e.type, e.name, e.poster), onClick = { vm.openSaved(e) }, fillWidth = true) }
                }
            }
        }
    }
}

@Composable
private fun RowTitle(text: String) {
    Text(text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun Poster(vm: StremioViewModel, item: MetaPreview, onClick: () -> Unit, caption: String? = null, fillWidth: Boolean = false) {
    val ratio = when (item.posterShape) {
        "landscape" -> 1.77f
        "square" -> 1f
        else -> 0.675f
    }
    Column(
        (if (fillWidth) Modifier.fillMaxWidth() else Modifier.width((if (ratio > 1f) 288.dp else 156.dp) * LocalDensity.current.fontScale)).clickable(onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(ratio).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
            AsyncImage(model = item.poster, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        if (item.type == "movie") WatchMark(vm, item.id)
        Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        if (caption != null) Text(caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        else if (item.releaseInfo != null) Text(item.releaseInfo!!, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

// ---------------------------------------------------------------------------------------------

@Composable
private fun DetailPage(vm: StremioViewModel, d: DetailState) {
    BackHandler { vm.closeDetail() }
    val meta = d.meta
    val preview = d.preview
    val saved by vm.saved.collectAsStateWithLifecycle()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().height(240.dp)) {
                AsyncImage(
                    model = meta?.background ?: meta?.poster ?: preview.poster,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.25f), MaterialTheme.colorScheme.surface))))
                IconButton(onClick = { vm.closeDetail() }, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
                    Icon(painterResource(R.drawable.arrow_back_24px), contentDescription = stringResource(R.string.hc_back), tint = Color.White)
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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!meta.isSeries) {
                            Button(onClick = { vm.showStreams(meta, null) }) {
                                Icon(painterResource(R.drawable.play_arrow_24px), contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.hc_play))
                            }
                        }
                        val isSaved = saved.any { it.id == meta.id }
                        FilledTonalButton(onClick = { vm.toggleSaved(meta) }) {
                            Icon(painterResource(if (isSaved) R.drawable.star_24px_filled else R.drawable.star_24px), contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(if (isSaved) R.string.au22_stremio_saved else R.string.au22_stremio_save))
                        }
                    }
                    meta.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    if (meta.cast.isNotEmpty()) Text(meta.cast.take(8).joinToString(", "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (meta.isSeries) Episodes(vm, meta)
                }
                Spacer(Modifier.height(96.dp))
            }
        }
    }
}

@Composable
private fun Episodes(vm: StremioViewModel, meta: Meta) {
    val seasons = remember(meta) { meta.videos.mapNotNull { it.season }.distinct().sortedBy { if (it == 0) Int.MAX_VALUE else it } }
    var season by rememberSaveable(meta.id) { mutableIntStateOf(seasons.firstOrNull { it != 0 } ?: seasons.firstOrNull() ?: 1) }
    if (seasons.size > 1) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(seasons) { s ->
                FilterChip(selected = season == s, onClick = { season = s }, label = { Text(stringResource(R.string.au22_stremio_season, s)) }, shape = CircleShape)
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
                    WatchMark(vm, meta.id, v.id, meta.videos.map { it.id }, v.season, v.episode)
                    v.overview?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StreamsSheet(vm: StremioViewModel, state: StreamsState) {
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
            Text(stringResource(R.string.au22_stremio_streams), style = MaterialTheme.typography.titleLarge)
            Text(state.title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 8.dp))
            if (state.loading) {
                Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (state.items.isEmpty()) {
                Text(stringResource(R.string.au22_stremio_no_streams), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 16.dp))
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().height(420.dp)) {
                    items(visibleStreams, key = { it.streamKey() + "#" + state.items.indexOf(it) }) { s ->
                        StreamRow(s, last = s.streamKey() == state.lastUsed) {
                            vm.play(context, s, state) { opened ->
                                if (opened) vm.closeStreams()
                                else Toast.makeText(context, R.string.au22_stremio_unsupported, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamRow(s: StreamItem, last: Boolean, onClick: () -> Unit) {
    val supported = s.kind != StreamItem.Kind.Unsupported
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(enabled = supported, onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(s.name ?: s.addonName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (last) Text(stringResource(R.string.au22_stremio_last_used), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
        s.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis) }
        Text(
            when (s.kind) {
                StreamItem.Kind.Torrent -> stringResource(R.string.au22_stremio_p2p_warning)
                StreamItem.Kind.External -> stringResource(R.string.au22_stremio_opens_browser)
                StreamItem.Kind.Unsupported -> stringResource(R.string.au22_stremio_unsupported)
                else -> s.addonName
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddonsSheet(vm: StremioViewModel, onDismiss: () -> Unit) {
    val addons by vm.addons.collectAsStateWithLifecycle()
    var showDirectory by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.au22_stremio_addons), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.refreshAddons() }) { Text(stringResource(R.string.au22_stremio_refresh)) }
            }
            addons.forEach { a -> AddonRow(a, onRemove = { vm.removeAddon(a.baseUrl) }) }
            AddAddonForm(vm, showSuggestion = addons.none { it.baseUrl == StremioAddons.CINEMETA })
            OutlinedButton(onClick = { showDirectory = true; vm.loadDirectory() }) { Text(stringResource(R.string.au22_stremio_directory)) }
        }
    }
    if (showDirectory) DirectorySheet(vm, onDismiss = { showDirectory = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountSheet(vm: StremioViewModel, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp).padding(bottom = 24.dp)) {
            AccountSection(vm)
        }
    }
}

/** Sign in to a Stremio account to keep add-ons, library and continue watching in step with it */
@Composable
private fun AccountSection(vm: StremioViewModel) {
    val context = LocalContext.current
    val email by vm.accountEmail.collectAsStateWithLifecycle()
    val busy by vm.accountBusy.collectAsStateWithLifecycle()
    var mail by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val syncedText = stringResource(R.string.au22_stremio_synced)
    val shownError = error
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.au22_stremio_account), style = MaterialTheme.typography.titleMedium)
            if (email != null) {
                Text(stringResource(R.string.au22_stremio_signed_in, email!!), style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        enabled = !busy && !working,
                        onClick = {
                            working = true
                            vm.syncNow { result ->
                                working = false
                                Toast.makeText(context, result ?: syncedText, Toast.LENGTH_SHORT).show()
                            }
                        },
                    ) { Text(stringResource(R.string.au22_stremio_sync_now)) }
                    TextButton(enabled = !busy && !working, onClick = { vm.signOut() }) { Text(stringResource(R.string.au22_stremio_sign_out)) }
                    if (busy || working) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            } else {
                Text(stringResource(R.string.au22_stremio_account_intro), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = mail, onValueChange = { mail = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.au22_stremio_email)) },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Email),
                )
                OutlinedTextField(
                    value = password, onValueChange = { password = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.au22_stremio_password)) },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
                    isError = shownError != null,
                    supportingText = if (shownError != null) { { Text(shownError) } } else null,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        enabled = mail.isNotBlank() && password.isNotEmpty() && !working,
                        onClick = {
                            working = true
                            error = null
                            vm.signIn(mail, password) { result ->
                                working = false
                                if (result == null) password = "" else error = result
                            }
                        },
                    ) { Text(stringResource(R.string.au22_stremio_sign_in)) }
                    if (working) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }
        }
    }
}

/** Add-ons found in the directories of installed add-ons and in Stremio's public list; they are made by others and not checked by Telos */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DirectorySheet(vm: StremioViewModel, onDismiss: () -> Unit) {
    val directory by vm.directory.collectAsStateWithLifecycle()
    val installed by vm.addons.collectAsStateWithLifecycle()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text(stringResource(R.string.au22_stremio_directory), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.au22_stremio_directory_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            val list = directory
            if (list == null) {
                Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (list.isEmpty()) {
                Text(stringResource(R.string.au22_stremio_directory_empty), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 16.dp))
            } else {
                LazyColumn(Modifier.fillMaxWidth().height(460.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(list, key = { it.baseUrl }) { d ->
                        val isInstalled = installed.any { it.baseUrl == d.baseUrl || it.manifest.id == d.manifest.id }
                        Card(Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("${d.manifest.name} ${d.manifest.version}".trim(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    if (d.manifest.description.isNotBlank()) Text(d.manifest.description, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                    if (d.manifest.p2p) Text(stringResource(R.string.au22_stremio_p2p_warning), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                }
                                if (isInstalled) OutlinedButton(onClick = {}, enabled = false) { Text(stringResource(R.string.hf_store_installed)) }
                                else Button(onClick = { vm.installDirectory(d) }) { Text(stringResource(R.string.hc_install)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddonRow(a: Addon, onRemove: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${a.manifest.name} ${a.manifest.version}".trim(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (a.manifest.description.isNotBlank()) Text(a.manifest.description, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (a.manifest.p2p) Text(stringResource(R.string.au22_stremio_p2p_warning), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                Text(a.baseUrl, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TextButton(onClick = onRemove) { Text(stringResource(R.string.hc_remove)) }
        }
    }
}

@Composable
private fun WatchMark(vm: StremioViewModel, id: String, videoId: String? = null, videoIds: List<String> = emptyList(), season: Int? = null, episode: Int? = null) {
    val states by vm.accountWatched.collectAsStateWithLifecycle()
    val version by vm.traktVersion.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val state = states[id]
    val stremio = remember(state, videoId, videoIds) {
        if (videoId == null) state?.movie == true else videoId in state?.episodes(videoIds).orEmpty()
    }
    val trakt = remember(id, season, episode, version) {
        de.mm20.launcher2.comms.media.video.trakt.Trakt.isWatchedId(context, id, season, episode)
    }
    if (stremio || trakt) {
        val sources = listOfNotNull(if (stremio) "Stremio" else null, if (trakt) "Trakt" else null).joinToString(" · ")
        Text(stringResource(R.string.au22_stremio_watched_sources, sources), style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 4.dp))
    }
}
