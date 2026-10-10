package de.mm20.launcher2.ui.media.tv

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.comms.telephony.HomeCountry
import de.mm20.launcher2.comms.tv.TvBackup
import de.mm20.launcher2.comms.tv.TvCatalog
import de.mm20.launcher2.comms.tv.TvChannel
import de.mm20.launcher2.comms.tv.TvCategory
import de.mm20.launcher2.comms.tv.TvCountry
import de.mm20.launcher2.comms.tv.TvCustomChannel
import de.mm20.launcher2.comms.tv.TvEpg
import de.mm20.launcher2.comms.tv.TvExtraSources
import de.mm20.launcher2.comms.tv.TvProgrammes
import de.mm20.launcher2.comms.tv.TvImportResult
import de.mm20.launcher2.comms.tv.TvIndex
import de.mm20.launcher2.comms.tv.TvLanguage
import de.mm20.launcher2.comms.tv.TvLibrary
import de.mm20.launcher2.comms.tv.TvM3u
import de.mm20.launcher2.comms.tv.TvPlayerController
import de.mm20.launcher2.comms.tv.TvRepository
import de.mm20.launcher2.comms.tv.TvSaveResult
import de.mm20.launcher2.comms.tv.TvSettings
import de.mm20.launcher2.preferences.comms.CommsSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.InputStreamReader
import java.util.Locale

enum class TvLoad { Idle, Loading, Failed, Ready }

/** What the home screen shows, computed off the main thread */
data class TvHome(
    /** the channels of the selected countries / languages / category */
    val channels: List<TvChannel> = emptyList(),
    /** set while a search text is typed */
    val results: List<TvChannel>? = null,
    /** the selected countries are shown (otherwise it is a suggestion for the locale) */
    val countrySelected: Boolean = false,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class TvViewModel : ViewModel(), KoinComponent {
    private val catalog: TvCatalog by inject()
    private val settings: TvSettings by inject()
    private val library: TvLibrary by inject()
    private val repository: TvRepository by inject()
    private val backup: TvBackup by inject()
    private val commsSettings: CommsSettings by inject()
    private val appContext: Context by inject()
    private val epg: TvEpg by inject()
    private val extraSources: TvExtraSources by inject()
    val controller: TvPlayerController by inject()

    // ---- optional extra Greek sources and programme guide ----

    val extraPlaylistsEnabled: StateFlow<Boolean> get() = settings.extraPlaylistsEnabled
    val epgEnabled: StateFlow<Boolean> get() = settings.epgEnabled
    /** Keep playing with the screen off / when the app is left (foreground service with notification) */
    val keepInBackground: StateFlow<Boolean> get() = settings.keepPlayingInBackground

    fun setKeepInBackground(enabled: Boolean) = settings.setKeepPlayingInBackground(enabled)
    val epgVersion: StateFlow<Int> get() = epg.epgVersion
    private val homeHint = MutableStateFlow("")

    /** Greece is selected, or nothing is selected yet and home country / locale / language is Greek (extras apply) */
    val greeceSelected: StateFlow<Boolean> = combine(settings.selectedCountries, homeHint) { c, h ->
        TvSettings.greeceActive(c, h, Locale.getDefault())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, settings.greeceSelected())

    private val homeIsGreece = MutableStateFlow(false)

    /** Greece is selected, or is the home country that will be selected after accepting (first run) */
    val disclaimerGreece: StateFlow<Boolean> = combine(greeceSelected, homeIsGreece) { a, b -> a || b }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        viewModelScope.launch {
            val home = withContext(Dispatchers.Default) {
                runCatching { HomeCountry.resolve(appContext, commsSettings.homeCountry.first()) }.getOrDefault("")
            }
            settings.homeCountryHint = home
            homeHint.value = home
            homeIsGreece.value = TvSettings.defaultCountry(home, Locale.getDefault()) == "GR"
        }
    }

    fun setExtraPlaylists(enabled: Boolean) {
        settings.setExtraPlaylistsEnabled(enabled)
        viewModelScope.launch { runCatching { extraSources.load() } }
    }

    fun setEpg(enabled: Boolean) {
        settings.setEpgEnabled(enabled)
        if (enabled) refreshEpg()
    }

    /** Silent; loads the cached guide and updates it when it is older than 6 hours */
    fun refreshEpg() {
        if (!settings.disclaimerShown.value) return
        viewModelScope.launch { runCatching { epg.refreshEpgIfStale() } }
    }

    /** What is on now / next on [channelId]; null when there is no guide data */
    fun nowNext(channelId: String): TvProgrammes? = epg.nowNext(channelId)

    /** URLs of streams that failed recently */
    fun badStreamUrls(): Set<String> = settings.badStreams().keys

    val disclaimerShown: StateFlow<Boolean> get() = settings.disclaimerShown
    val selectedCountries: StateFlow<List<String>> get() = settings.selectedCountries
    val selectedLanguages: StateFlow<List<String>> get() = settings.selectedLanguages
    val refreshStatus get() = catalog.refreshStatus

    private val _load = MutableStateFlow(TvLoad.Idle)
    val load: StateFlow<TvLoad> = _load

    /** The full player is open (the hub hides its mini bar then) */
    private val _playerOpen = MutableStateFlow(false)
    val playerOpen: StateFlow<Boolean> = _playerOpen

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query
    private val _category = MutableStateFlow<String?>(null)
    val category: StateFlow<String?> = _category

    private val index: StateFlow<TvIndex?> get() = catalog.index

    /** Opens the catalog. Only after the disclaimer was accepted; the first time this may download the list. */
    fun load() {
        if (!settings.disclaimerShown.value) return
        if (_load.value == TvLoad.Loading || _load.value == TvLoad.Ready) return
        _load.value = TvLoad.Loading
        viewModelScope.launch {
            val idx = runCatching { catalog.open() }.getOrNull()
            if (idx == null) {
                _load.value = TvLoad.Failed
            } else {
                applyDefaults(idx)
                _load.value = TvLoad.Ready
            }
        }
    }

    fun accept() {
        settings.setDisclaimerShown(true)
        load()
    }

    fun refresh() {
        if (!settings.disclaimerShown.value) return
        viewModelScope.launch {
            runCatching { catalog.refresh() }
            if (_load.value != TvLoad.Ready && catalog.index.value != null) _load.value = TvLoad.Ready
        }
    }

    fun retryLoad() {
        if (_load.value == TvLoad.Failed) _load.value = TvLoad.Idle
        load()
    }

    /** First use: the home country becomes the selection (when the catalog has channels for it) */
    private suspend fun applyDefaults(idx: TvIndex) {
        if (settings.selectedCountries.value.isEmpty()) {
            val resolved = withContext(Dispatchers.Default) {
                runCatching { HomeCountry.resolve(appContext, commsSettings.homeCountry.first()) }.getOrDefault("")
            }
            settings.homeCountryHint = resolved
            homeHint.value = resolved
            // home country, else locale country, else Greece for a Greek app language
            val country = TvSettings.defaultCountry(resolved, Locale.getDefault())
            // Greece is always selectable: the extra sources add Greek channels the catalog may lack
            if (country.isNotBlank() && (country == "GR" || idx.byCountry(listOf(country)).isNotEmpty())) {
                settings.setSelectedCountries(listOf(country))
            }
        }
        // the extras were requested when TV was opened, possibly before the country was known: load again,
        // which republishes the index so the lists show the Greek channels without leaving TV
        runCatching { extraSources.load() }
    }

    fun setQuery(q: String) { _query.value = q.take(100) }
    fun setCategory(id: String?) { _category.value = id }
    fun setCountries(codes: List<String>) {
        settings.setSelectedCountries(codes)
        if (settings.greeceSelected()) {
            viewModelScope.launch {
                runCatching { extraSources.load() }
                runCatching { epg.refreshEpgIfStale() }
            }
        } else {
            viewModelScope.launch { runCatching { extraSources.load() } }
        }
    }
    fun setLanguages(codes: List<String>) = settings.setSelectedLanguages(codes)

    val countries: StateFlow<List<TvCountry>> = index.map { it?.countries(Locale.getDefault()).orEmpty() }
        .flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val languages: StateFlow<List<TvLanguage>> = index.map { it?.languages(Locale.getDefault()).orEmpty() }
        .flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val categories: StateFlow<List<TvCategory>> = index.map { it?.categories().orEmpty() }
        .flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val filters = combine(
        settings.selectedCountries, settings.selectedLanguages, _category,
        _query.debounce { if (it.isEmpty()) 0L else 250L },
    ) { c, l, k, q -> Filters(c, l, k, q.trim()) }

    private data class Filters(val countries: List<String>, val languages: List<String>, val category: String?, val query: String)

    val home: StateFlow<TvHome> = combine(index, filters) { idx, f ->
        if (idx == null) return@combine TvHome()
        val langs3 = f.languages.mapNotNull { TvIndex.language3(it) }.toSet()
        var base: List<TvChannel> = when {
            f.countries.isNotEmpty() -> idx.byCountry(f.countries)
            else -> {
                val appLang = Locale.getDefault().language
                val home = TvSettings.defaultCountry(HomeCountry.resolve(appContext, ""), Locale.getDefault())
                idx.suggestedForLocale(home, appLang, 400).ifEmpty { idx.all }
            }
        }
        if (langs3.isNotEmpty()) {
            base = if (f.countries.isEmpty()) idx.byLanguage(langs3)
            else base.filter { c -> c.languages.isEmpty() || c.languages.any { it in langs3 } }
        }
        f.category?.let { k -> base = base.filter { k in it.categories } }
        TvHome(
            channels = base,
            results = if (f.query.isEmpty()) null else idx.search(f.query, 200),
            countrySelected = f.countries.isNotEmpty(),
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TvHome())

    val favorites: StateFlow<List<TvChannel>> = library.observeFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val favoriteIds: StateFlow<Set<String>> = favorites.map { l -> l.mapTo(HashSet()) { it.id } as Set<String> }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
    val recents: StateFlow<List<TvChannel>> = library.observeRecents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val customChannels: StateFlow<List<TvChannel>> = library.observeCustomChannels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ---- playback ----

    fun play(channel: TvChannel, list: List<TvChannel>) {
        controller.play(channel, list)
        _playerOpen.value = true
    }

    fun openPlayer() { if (controller.currentChannel.value != null) _playerOpen.value = true }
    fun minimizePlayer() { _playerOpen.value = false }
    fun stop() {
        _playerOpen.value = false
        controller.stop()
    }

    // ---- library ----

    fun setFavorite(channelId: String, favorite: Boolean) {
        viewModelScope.launch { runCatching { repository.setFavorite(channelId, favorite) } }
    }

    fun moveFavorite(channelId: String, toIndex: Int) {
        viewModelScope.launch { runCatching { repository.moveFavorite(channelId, toIndex) } }
    }

    suspend fun saveCustom(id: String, name: String, url: String, logo: String, group: String): TvSaveResult =
        runCatching {
            repository.saveCustomChannel(
                TvCustomChannel(id = id, name = name.trim(), streamUrl = url.trim(), logoUrl = logo.trim(), group = group.trim(),
                    addedAt = System.currentTimeMillis())
            )
        }.getOrDefault(TvSaveResult.INVALID_NAME)

    fun deleteCustom(id: String) {
        viewModelScope.launch {
            if (controller.currentChannel.value?.id == id) stop()
            runCatching { repository.deleteCustomChannel(id) }
        }
    }

    /** Reads at most 2 MB of text (one char more than the limit is enough for the repository to refuse it) */
    suspend fun importM3u(uri: Uri): TvImportResult? = withContext(Dispatchers.IO) {
        runCatching {
            val text = appContext.contentResolver.openInputStream(uri)?.use { input ->
                val sb = StringBuilder()
                val reader = InputStreamReader(input, Charsets.UTF_8)
                val buf = CharArray(16 * 1024)
                while (sb.length <= TvM3u.MAX_CHARS) {
                    val n = reader.read(buf)
                    if (n < 0) break
                    sb.append(buf, 0, n)
                }
                sb.toString()
            } ?: return@runCatching null
            repository.importM3u(text)
        }.getOrNull()
    }

    suspend fun exportBackup(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val json = backup.export()
            appContext.contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray(Charsets.UTF_8)) } != null
        }.getOrDefault(false)
    }

    /** Number of restored items, or -1 when the file could not be read */
    suspend fun restoreBackup(uri: Uri): Int = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = appContext.contentResolver.openInputStream(uri)?.use { readLimited(it, MAX_BACKUP_BYTES) }
                ?: return@runCatching -1
            val n = backup.restore(String(bytes, Charsets.UTF_8))
            if (n == 0 && bytes.isEmpty()) -1 else n
        }.getOrDefault(-1)
    }

    private fun readLimited(input: java.io.InputStream, limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (out.size() < limit) {
            val n = input.read(buf, 0, minOf(buf.size, limit - out.size()))
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    companion object {
        private const val MAX_BACKUP_BYTES = 8 * 1024 * 1024
    }
}
