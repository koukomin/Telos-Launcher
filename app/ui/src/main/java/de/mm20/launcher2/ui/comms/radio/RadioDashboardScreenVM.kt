// === TELOS_PENDING_REVIEW_START: radio_browser_ktor ===
package de.mm20.launcher2.ui.comms.radio
import de.mm20.launcher2.i18n.R as I18nR

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import de.mm20.launcher2.ui.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.comms.model.RadioStation
import de.mm20.launcher2.comms.radio.StreamResolver
import de.mm20.launcher2.comms.radio.RadioArchives
import de.mm20.launcher2.comms.radio.RadioSettings
import de.mm20.launcher2.comms.radio.RadioArtwork
import kotlinx.coroutines.flow.first
import java.io.File
import java.security.MessageDigest
import de.mm20.launcher2.comms.repository.RadioRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
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
        .catch { emit(emptyList()) } // a database that cannot be opened must not crash the screen
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val history = repository.observeHistory()
        .catch { emit(emptyList()) }
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
                _searchError.value = RadioText(I18nR.string.au_radio_search_failed, e.message ?: e.javaClass.simpleName)
            }
            _isSearching.value = false
        }
    }

    fun toggleFavorite(station: RadioStation) {
        viewModelScope.launch { runCatching { repository.toggleFavorite(station) } }
    }

    fun renameStation(id: String, name: String) {
        viewModelScope.launch { runCatching { repository.renameStation(id, name) } }
    }

    fun updateStation(station: RadioStation) {
        if (station.name.isBlank() || !RadioArchives.validStream(station.streamUrl) || station.alternateStreams.any { !RadioArchives.validStream(it) }) return
        viewModelScope.launch { runCatching { repository.saveStation(station) } }
    }

    fun refreshStationImages(context: Context) {
        viewModelScope.launch {
            runCatching {
                val stations = repository.observeFavorites().first()
                for (station in stations) {
                    val match = repository.searchStations(station.name).firstOrNull {
                        it.streamUrl == station.streamUrl || it.name.equals(station.name, true)
                    } ?: continue
                    if (match.faviconUrl.isBlank()) continue
                    val bytes = withContext(Dispatchers.IO) { loadImage(context, match.faviconUrl) } ?: continue
                    val image = withContext(Dispatchers.IO) { saveImage(context, bytes) }
                    repository.saveStation(station.copy(faviconUrl = image))
                }
            }.onSuccess { _message.value = RadioText(I18nR.string.hc_done) }
                .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it else _message.value = RadioText(I18nR.string.au_radio_import_failed) }
        }
    }

    fun deleteStation(id: String) {
        viewModelScope.launch { runCatching { repository.deleteStation(id) } }
    }

    /** Adds a station from a stream or playlist address typed by the user */
    fun addStation(name: String, address: String) {
        viewModelScope.launch {
            val url = address.trim()
            if (!url.startsWith("http", ignoreCase = true)) {
                _message.value = RadioText(I18nR.string.au_radio_enter_http_address)
                return@launch
            }
            val resolved = StreamResolver.resolve(url)
            val host = runCatching { URL(url).host }.getOrDefault(url)
            val station = RadioStation(
                id = "local-" + UUID.randomUUID(),
                name = name.trim().ifBlank { resolved.playlistName.ifBlank { host } },
                streamUrl = resolved.urls.firstOrNull() ?: url,
                faviconUrl = "",
                streamContent = resolved.mimeType,
                nameManuallySet = name.isNotBlank(),
                alternateStreams = resolved.urls.drop(1),
            )
            _message.value = try {
                repository.saveStation(station)
                RadioText(I18nR.string.au_radio_station_added, station.name)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                RadioText(I18nR.string.au_radio_add_failed)
            }
        }
    }

    fun importPlaylist(context: Context, uri: Uri) {
        viewModelScope.launch {
            val count = runCatching { importArchive(context, uri, replace = false) }.getOrElse {
                if (it is kotlinx.coroutines.CancellationException) throw it
                null
            }
            _message.value = if (count == null) RadioText(I18nR.string.au_radio_import_failed) else RadioText(I18nR.string.au_radio_imported, count)
        }
    }

    fun exportM3u(context: Context, uri: Uri) {
        viewModelScope.launch {
            val ok = runCatching { writeText(context, uri, repository.exportM3u()) }.isSuccess
            _message.value = if (ok) RadioText(I18nR.string.au_radio_playlist_saved) else RadioText(I18nR.string.au_radio_playlist_save_failed)
        }
    }

    fun exportBackup(context: Context, uri: Uri) {
        viewModelScope.launch {
            val ok = runCatching {
                val stations = repository.observeFavorites().first()
                withContext(Dispatchers.IO) {
                    val output = context.contentResolver.openOutputStream(uri) ?: error("Cannot open backup")
                    RadioArchives.write(stations, output) { station -> loadImage(context, station.faviconUrl) }
                }
            }.getOrElse { if (it is kotlinx.coroutines.CancellationException) throw it; null } != null
            _message.value = if (ok) RadioText(I18nR.string.au_radio_backup_saved) else RadioText(I18nR.string.au_radio_backup_save_failed)
        }
    }

    /** The UI asks before destructive replacement. Normal playlist import always merges. */
    fun restoreBackup(context: Context, uri: Uri, replace: Boolean = false) {
        viewModelScope.launch {
            val count = runCatching { importArchive(context, uri, replace) }.getOrElse {
                if (it is kotlinx.coroutines.CancellationException) throw it
                null
            }
            _message.value = if (count == null) RadioText(I18nR.string.au_radio_restore_failed) else RadioText(I18nR.string.au_radio_restored, count)
        }
    }

    private suspend fun importArchive(context: Context, uri: Uri, replace: Boolean): Int {
        val imported = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { RadioArchives.read(it) } ?: error("Cannot open collection")
        }
        require(imported.isNotEmpty()) { "No valid stations" }
        val stations = withContext(Dispatchers.IO) {
            imported.map { entry ->
                var bytes = entry.image
                if (bytes == null && RadioSettings(context).values.value.updateImages) bytes = loadImage(context, entry.station.faviconUrl)
                entry.station.copy(faviconUrl = bytes?.let { saveImage(context, it) } ?: entry.station.faviconUrl)
            }
        }
        return repository.importStations(stations, replace)
    }

    private fun saveImage(context: Context, bytes: ByteArray): String {
        val directory = File(context.applicationContext.filesDir, "radio/images").apply { mkdirs() }
        val name = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val file = File(directory, "$name.img")
        if (!file.exists()) {
            val temporary = File.createTempFile("image-", ".tmp", directory)
            temporary.writeBytes(bytes)
            if (!temporary.renameTo(file)) { temporary.delete(); error("Cannot save station image") }
        }
        return file.toURI().toString()
    }

    private fun loadImage(context: Context, address: String): ByteArray? = runCatching {
        val uri = Uri.parse(address)
        val input = when (uri.scheme) {
            "file" -> {
                val file = File(uri.path ?: return null)
                if (!file.canonicalPath.startsWith(context.applicationContext.filesDir.canonicalPath + File.separator)) return null
                file.inputStream()
            }
            "content", "android.resource" -> context.contentResolver.openInputStream(uri)
            "http", "https" -> {
                return@runCatching RadioArtwork.download(address)
            }
            else -> return null
        }
        input?.use { readImage(it) }
    }.getOrNull()

    private fun readImage(input: java.io.InputStream): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val size = input.read(buffer)
            if (size < 0) break
            require(output.size() + size <= RadioArchives.MAX_IMAGE_BYTES)
            output.write(buffer, 0, size)
        }
        return output.toByteArray()
    }

    fun clearHistory() {
        viewModelScope.launch { runCatching { repository.clearHistory() } }
    }

    private suspend fun writeText(context: Context, uri: Uri, text: String) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(text) }
            ?: error("cannot open file")
    }
}
// === TELOS_PENDING_REVIEW_END: radio_browser_ktor ===
