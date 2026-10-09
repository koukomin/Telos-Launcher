// === TELOS_PENDING_REVIEW_START: radio_browser_ktor ===
package de.mm20.launcher2.ui.comms.radio

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import de.mm20.launcher2.ui.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.comms.model.RadioStation
import de.mm20.launcher2.comms.radio.StreamResolver
import de.mm20.launcher2.comms.repository.RadioRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.net.URL
import java.util.UUID

/** A message made of a string resource and an optional format argument, resolved by the UI */
data class RadioText(@StringRes val resId: Int, val arg: Any? = null)

class RadioDashboardScreenVM : ViewModel(), KoinComponent {

    private val repository: RadioRepository by inject()

    val favorites = repository.observeFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val history = repository.observeHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _searchResults = MutableStateFlow<List<RadioStation>>(emptyList())
    val searchResults: StateFlow<List<RadioStation>> = _searchResults

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching

    /** One-shot message for the user (shown as a toast, then cleared) */
    private val _message = MutableStateFlow<RadioText?>(null)
    val message: StateFlow<RadioText?> = _message

    private val _searchError = MutableStateFlow<RadioText?>(null)
    val searchError: StateFlow<RadioText?> = _searchError

    private var searchJob: Job? = null

    fun consumeMessage() {
        _message.value = null
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()

        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _searchError.value = null
            _isSearching.value = false
            return
        }

        searchJob = viewModelScope.launch {
            _isSearching.value = true
            delay(500) // Debounce
            try {
                _searchResults.value = repository.searchStations(query)
                _searchError.value = null
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _searchResults.value = emptyList()
                _searchError.value = RadioText(R.string.au_radio_search_failed, e.message ?: e.javaClass.simpleName)
            }
            _isSearching.value = false
        }
    }

    fun toggleFavorite(station: RadioStation) {
        viewModelScope.launch {
            repository.toggleFavorite(station)
        }
    }

    fun renameStation(id: String, name: String) {
        viewModelScope.launch { repository.renameStation(id, name) }
    }

    fun deleteStation(id: String) {
        viewModelScope.launch { repository.deleteStation(id) }
    }

    /** Adds a station from a stream or playlist address typed by the user */
    fun addStation(name: String, address: String) {
        viewModelScope.launch {
            val url = address.trim()
            if (!url.startsWith("http", ignoreCase = true)) {
                _message.value = RadioText(R.string.au_radio_enter_http_address)
                return@launch
            }
            val resolved = StreamResolver.resolve(url)
            val host = runCatching { URL(url).host }.getOrDefault(url)
            val station = RadioStation(
                id = "local-" + UUID.randomUUID(),
                name = name.trim().ifBlank { resolved.playlistName.ifBlank { host } },
                streamUrl = resolved.urls.first(),
                faviconUrl = "",
                streamContent = resolved.mimeType,
                nameManuallySet = name.isNotBlank(),
                alternateStreams = resolved.urls.drop(1),
            )
            _message.value = try {
                repository.saveStation(station)
                RadioText(R.string.au_radio_station_added, station.name)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                RadioText(R.string.au_radio_add_failed)
            }
        }
    }

    fun importPlaylist(context: Context, uri: Uri) {
        viewModelScope.launch {
            val count = runCatching {
                val text = readText(context, uri)
                repository.importPlaylist(text)
            }.getOrNull()
            _message.value = if (count == null) RadioText(R.string.au_radio_import_failed) else RadioText(R.string.au_radio_imported, count)
        }
    }

    fun exportM3u(context: Context, uri: Uri) {
        viewModelScope.launch {
            val ok = runCatching { writeText(context, uri, repository.exportM3u()) }.isSuccess
            _message.value = if (ok) RadioText(R.string.au_radio_playlist_saved) else RadioText(R.string.au_radio_playlist_save_failed)
        }
    }

    fun exportBackup(context: Context, uri: Uri) {
        viewModelScope.launch {
            val ok = runCatching { writeText(context, uri, repository.exportBackup()) }.isSuccess
            _message.value = if (ok) RadioText(R.string.au_radio_backup_saved) else RadioText(R.string.au_radio_backup_save_failed)
        }
    }

    fun restoreBackup(context: Context, uri: Uri) {
        viewModelScope.launch {
            val count = runCatching { repository.restoreBackup(readText(context, uri)) }.getOrNull()
            _message.value = if (count == null) RadioText(R.string.au_radio_restore_failed) else RadioText(R.string.au_radio_restored, count)
        }
    }

    fun clearHistory() {
        viewModelScope.launch { repository.clearHistory() }
    }

    private suspend fun readText(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: error("cannot open file")
    }

    private suspend fun writeText(context: Context, uri: Uri, text: String) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(text) }
            ?: error("cannot open file")
    }
}
// === TELOS_PENDING_REVIEW_END: radio_browser_ktor ===
