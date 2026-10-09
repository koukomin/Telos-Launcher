// === TELOS_PENDING_REVIEW_START: radio_browser_ktor ===
package de.mm20.launcher2.ui.comms.radio

import androidx.compose.ui.res.stringResource
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import coil.compose.AsyncImage
import de.mm20.launcher2.comms.model.RadioStation
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.comms.RadioViewModel
import de.mm20.launcher2.ui.component.LauncherCard
import kotlinx.serialization.Serializable
import java.text.DateFormat
import java.util.Date

@Serializable
data object RadioDashboardRoute : NavKey

@Composable
fun RadioDashboardScreen() {
    val viewModel: RadioDashboardScreenVM = viewModel()
    val playerViewModel: RadioViewModel = viewModel()
    val context = LocalContext.current
    // the player connects to the radio service here too, not only in the mini player
    LaunchedEffect(Unit) { playerViewModel.initialize(context) }

    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
    val searchError by viewModel.searchError.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val sleepEndsAt by playerViewModel.sleepEndsAt.collectAsStateWithLifecycle()
    val recordingState by playerViewModel.recordingState.collectAsStateWithLifecycle()
    val isRecording = recordingState is de.mm20.launcher2.comms.radio.RadioRecorder.State.Recording

    var selectedTabIndex by rememberSaveable { mutableStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.au_radio_tab_collection),
        stringResource(R.string.hc_search),
        stringResource(R.string.au_radio_tab_history),
    )

    var menuOpen by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<RadioStation?>(null) }

    LaunchedEffect(message) {
        message?.let {
            val text = if (it.arg != null) context.getString(it.resId, it.arg) else context.getString(it.resId)
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
            viewModel.consumeMessage()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importPlaylist(context, uri)
    }
    val exportM3uLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("audio/x-mpegurl")
    ) { uri -> if (uri != null) viewModel.exportM3u(context, uri) }
    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> if (uri != null) viewModel.exportBackup(context, uri) }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.restoreBackup(context, uri)
    }

    de.mm20.launcher2.ui.media.MediaFrame(stringResource(R.string.hc_radio), askNotifications = true, guardKey = "telos_radio_app://radio", actions = {
        IconButton(onClick = { showAdd = true }) {
            Icon(painterResource(R.drawable.add_24px), contentDescription = stringResource(R.string.hc_add_station))
        }
        IconButton(onClick = {
            playerViewModel.toggleRecording(context)?.let {
                Toast.makeText(context, context.getString(it), Toast.LENGTH_SHORT).show()
            }
        }) {
            Icon(
                painterResource(if (isRecording) R.drawable.radio_button_checked_24px else R.drawable.radio_button_unchecked_24px),
                contentDescription = stringResource(if (isRecording) R.string.au2_radio2_stop_recording else R.string.au2_radio2_record),
                tint = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = { showSleep = true }) {
            Icon(
                painterResource(R.drawable.timer_24px),
                contentDescription = stringResource(R.string.hc_sleep_timer),
                tint = if (sleepEndsAt > 0) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(painterResource(R.drawable.more_vert_24px), contentDescription = stringResource(R.string.hc_more))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.hc_import_playlist_m3u_pls)) },
                    onClick = {
                        menuOpen = false
                        importLauncher.launch(arrayOf("*/*"))
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.hc_export_playlist_m3u)) },
                    onClick = {
                        menuOpen = false
                        exportM3uLauncher.launch("telos-radio.m3u")
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.hc_back_up_collection)) },
                    onClick = {
                        menuOpen = false
                        backupLauncher.launch("telos-radio-backup.json")
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.hc_restore_backup)) },
                    onClick = {
                        menuOpen = false
                        restoreLauncher.launch(arrayOf("*/*"))
                    },
                )
            }
        }
    }) {
    Column(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
        TabRow(selectedTabIndex = selectedTabIndex) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = { Text(title) }
                )
            }
        }

        when (selectedTabIndex) {
            0 -> {
                var collectionQuery by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
                val shownFavorites = androidx.compose.runtime.remember(favorites, collectionQuery) {
                    de.mm20.launcher2.comms.search.TelosSearch.filter(favorites, collectionQuery) { listOf(it.name) }
                }
                Column(modifier = Modifier.fillMaxSize()) {
                if (favorites.isNotEmpty()) {
                    de.mm20.launcher2.ui.component.TelosSearchBar(
                        collectionQuery, { collectionQuery = it }, stringResource(R.string.tsm_search_my_stations)
                    )
                }
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item { RadioRecordingsEntry() }
                    if (shownFavorites.isEmpty() && favorites.isNotEmpty() && collectionQuery.isNotBlank()) {
                        item { de.mm20.launcher2.ui.component.SearchEmptyState(collectionQuery) }
                    }
                    if (favorites.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.hc_no_stations_yet_search_for_a_station_add),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                    items(shownFavorites, key = { it.id }) { station ->
                        StationRow(
                            station = station,
                            isFavorite = true,
                            onFavoriteClick = { viewModel.toggleFavorite(station) },
                            onClick = { playerViewModel.playStation(station) },
                            onLongClick = { editTarget = station },
                        )
                    }
                }
                }
            }

            1 -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    de.mm20.launcher2.ui.component.TelosSearchBar(
                        searchQuery, { viewModel.updateSearchQuery(it) }, stringResource(R.string.tsm_search_radio_browser)
                    )
                    if (isSearching) {
                        androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                    }
                    searchError?.let {
                        Text(
                            text = if (it.arg != null) stringResource(it.resId, it.arg) else stringResource(it.resId),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    if (!isSearching && searchError == null && searchQuery.isNotBlank() && searchResults.isEmpty()) {
                        Text(
                            text = stringResource(R.string.hc_no_stations_found),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }

                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(searchResults.distinctBy { it.id }, key = { it.id }) { station ->
                            val isFavorite = favorites.any { it.id == station.id }
                            StationRow(
                                station = station,
                                isFavorite = isFavorite,
                                onFavoriteClick = { viewModel.toggleFavorite(station) },
                                onClick = { playerViewModel.playStation(station) }
                            )
                        }
                    }
                }
            }

            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (history.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.hc_tracks_announced_by_the_stations_you_lis),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    } else {
                        item {
                            TextButton(onClick = { viewModel.clearHistory() }) { Text(stringResource(R.string.hc_clear_history)) }
                        }
                    }
                    items(history, key = { it.id }) { entry ->
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(
                                text = entry.title,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = entry.stationName + " · " +
                                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                        .format(Date(entry.playedAt)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
    de.mm20.launcher2.ui.comms.RadioMiniPlayer()
    }
    }

    RadioRecordingResultEffect()

    if (showAdd) {
        var name by remember { mutableStateOf("") }
        var address by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text(stringResource(R.string.hc_add_station)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text(stringResource(R.string.hc_stream_or_playlist_address)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.hc_name_optional)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.addStation(name, address)
                    showAdd = false
                }) { Text(stringResource(R.string.hc_add)) }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text(stringResource(R.string.hc_cancel)) } },
        )
    }

    if (showSleep) {
        AlertDialog(
            onDismissRequest = { showSleep = false },
            title = { Text(stringResource(R.string.hc_sleep_timer)) },
            text = {
                Column {
                    listOf(15, 30, 45, 60, 90).forEach { minutes ->
                        TextButton(onClick = {
                            playerViewModel.setSleepTimer(minutes)
                            showSleep = false
                        }) { Text(stringResource(R.string.hc_stop_playback_in_minutes, minutes)) }
                    }
                    if (sleepEndsAt > 0) {
                        TextButton(onClick = {
                            playerViewModel.cancelSleepTimer()
                            showSleep = false
                        }) { Text(stringResource(R.string.hc_turn_timer_off)) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showSleep = false }) { Text(stringResource(R.string.hc_close)) } },
        )
    }

    editTarget?.let { station ->
        var renaming by remember(station.id) { mutableStateOf(false) }
        var newName by remember(station.id) { mutableStateOf(station.name) }
        if (!renaming) {
            AlertDialog(
                onDismissRequest = { editTarget = null },
                title = { Text(station.name) },
                text = { Text(station.streamUrl) },
                confirmButton = {
                    TextButton(onClick = { renaming = true }) { Text(stringResource(R.string.hc_rename)) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        viewModel.deleteStation(station.id)
                        editTarget = null
                    }) { Text(stringResource(R.string.hc_remove)) }
                },
            )
        } else {
            AlertDialog(
                onDismissRequest = { editTarget = null },
                title = { Text(stringResource(R.string.hc_rename_station)) },
                text = {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.renameStation(station.id, newName)
                        editTarget = null
                    }) { Text(stringResource(R.string.hc_save)) }
                },
                dismissButton = { TextButton(onClick = { editTarget = null }) { Text(stringResource(R.string.hc_cancel)) } },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StationRow(
    station: RadioStation,
    isFavorite: Boolean,
    onFavoriteClick: () -> Unit,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    LauncherCard(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Favicon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (station.faviconUrl.isNotBlank()) {
                    AsyncImage(
                        model = station.faviconUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.music_note_24px),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = station.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = station.streamUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Favorite Button
            IconButton(onClick = onFavoriteClick) {
                Icon(
                    painter = painterResource(R.drawable.star_24px),
                    contentDescription = stringResource(R.string.hc_favorite),
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
// === TELOS_PENDING_REVIEW_END: radio_browser_ktor ===
