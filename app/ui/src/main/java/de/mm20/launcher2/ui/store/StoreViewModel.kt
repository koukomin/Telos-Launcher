package de.mm20.launcher2.ui.store

import de.mm20.launcher2.comms.search.TelosSearch
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.preferences.comms.CommsSettings
import de.mm20.launcher2.store.action.StoreAction
import de.mm20.launcher2.store.action.StoreActionHandler
import de.mm20.launcher2.store.model.AppSource
import de.mm20.launcher2.store.model.StoreItem
import de.mm20.launcher2.store.options.GlobalOptions
import de.mm20.launcher2.store.options.ItemOptions
import de.mm20.launcher2.store.options.StoreOptions
import de.mm20.launcher2.store.parser.StoreUrlParser
import de.mm20.launcher2.store.repository.StoreRepository
import de.mm20.launcher2.store.updater.StoreTools
import de.mm20.launcher2.store.updater.StoreUpdater
import de.mm20.launcher2.ui.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Per-item state of an in-progress/finished [StoreViewModel.onAppActionClicked] call. */
enum class StoreInstallUiState {
    Idle,
    Downloading,
    /**
     * Either a privileged (Shizuku/root) install is running silently, or the standard
     * [android.content.pm.PackageInstaller] session has been handed off to the system's install
     * confirmation UI. The real completion signal is the package manager showing the new version,
     * which [StoreViewModel.installStates] picks up (the Store reads what is installed live).
     */
    Installing,
    Installed,
    Failed,
}

enum class StoreFilter { All, Updates, Installed, NotInstalled, TrackOnly }

/** An app of the list together with its settings, ready to show. */
data class StoreRow(
    val item: StoreItem,
    val options: ItemOptions,
    /** A new version exists and the app is not pinned and the version was not skipped */
    val updateAvailable: Boolean,
)

/** An installed app that could be added to the Store. */
data class InstalledCandidate(val packageName: String, val label: String)

/**
 * The Store: tracked apps (the Obtainium side) and the Telos apps. Follows the `KoinComponent` +
 * `by inject()` pattern since this VM has a single, app-wide dependency shape.
 */
class StoreViewModel : ViewModel(), KoinComponent {

    private fun sourceText(s: AppSource): String = when (s) {
        is AppSource.GitHub -> "${s.owner} ${s.repo}"
        is AppSource.FDroid -> s.repoUrl
        is AppSource.DirectApk -> s.downloadUrl
        is AppSource.AffiliatePlayStore -> s.packageName
        is AppSource.AffiliateDirect -> s.downloadUrl
        is AppSource.GitLab -> "${s.host} ${s.path}"
        is AppSource.Gitea -> "${s.host} ${s.owner} ${s.repo}"
        is AppSource.SourceForge -> s.project
        is AppSource.Html -> s.pageUrl
    }

    private val context: Context by inject()
    private fun str(id: Int, vararg args: Any): String = context.getString(id, *args)
    private val repository: StoreRepository by inject()
    private val actionHandler: StoreActionHandler by inject()
    private val tools: StoreTools by inject()
    private val updater: StoreUpdater by inject()
    val options: StoreOptions by inject()
    val settings: CommsSettings by inject()

    val items: StateFlow<List<StoreItem>> = repository.observeItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val global: StateFlow<GlobalOptions> = options.global

    // ---- list state ----

    val query = MutableStateFlow("")
    val filter = MutableStateFlow(StoreFilter.All)
    val category = MutableStateFlow<String?>(null)

    val rows: StateFlow<List<StoreRow>> = combine(items, options.items) { list, opts ->
        list.map { item ->
            val o = opts[item.id] ?: ItemOptions()
            val update = item.hasUpdate && !o.pinned && !(o.skippedVersion != null && o.skippedVersion == item.latestRelease?.version)
            StoreRow(item, o, update)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val visibleRows: StateFlow<List<StoreRow>> = combine(rows, query, filter, category) { all, q, f, c ->
        all.asSequence()
            .filter { row ->
                when (f) {
                    StoreFilter.All -> true
                    StoreFilter.Updates -> row.updateAvailable
                    StoreFilter.Installed -> row.item.installedVersionCode != null
                    StoreFilter.NotInstalled -> row.item.installedVersionCode == null
                    StoreFilter.TrackOnly -> row.options.trackOnly
                }
            }
            .filter { c == null || it.options.category == c }
            .filter { q.isBlank() || TelosSearch.matches(q, it.item.displayName, it.item.packageName, sourceText(it.item.source), it.options.note, it.options.category) }
            .sortedWith(compareByDescending<StoreRow> { it.updateAvailable }.thenBy { it.item.displayName.lowercase() })
            .toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<String>> = rows.map { r ->
        r.map { it.options.category }.filter { it.isNotBlank() }.distinct().sorted()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ---- install state ----

    private val _installStates = MutableStateFlow<Map<String, StoreInstallUiState>>(emptyMap())

    /** [_installStates] without the transient entries of apps that now show as installed and current */
    val installStates: StateFlow<Map<String, StoreInstallUiState>> =
        combine(items, _installStates) { items, states ->
            val itemsById = items.associateBy { it.id }
            states.filterKeys { id ->
                val item = itemsById[id] ?: return@filterKeys true
                val state = states.getValue(id)
                val isTransient = state == StoreInstallUiState.Downloading || state == StoreInstallUiState.Installing
                val resolved = item.installedVersionCode != null && !item.hasUpdate
                !(isTransient && resolved)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message
    fun consumeMessage() { _message.value = null }

    fun item(id: String): Flow<StoreItem?> = repository.observeItem(id)

    // ---- adding ----

    fun addFromUrl(url: String, packageName: String = "", onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val result = runCatching { tools.addFromUrl(url.trim(), packageName) }.getOrElse { Result.failure(it) }
            result.onSuccess { onResult(null) }.onFailure { onResult(it.message ?: str(R.string.au_store_msg_add_failed)) }
        }
    }

    /** Handles an address or an obtainium:// link that opened the Store; returns an address to prefill, if any */
    fun handleLink(link: String, onPrefill: (String) -> Unit) {
        if (link.isBlank()) return
        viewModelScope.launch {
            when {
                link.startsWith("obtainium://app/", true) -> {
                    val json = Uri.decode(link.substringAfter("obtainium://app/"))
                    val wrapped = runCatching { JSONObject().put("apps", org.json.JSONArray().put(JSONObject(json))).toString() }.getOrNull()
                    val summary = wrapped?.let { runCatching { tools.importObtainium(it) }.getOrNull() }
                    _message.value = when {
                        summary == null -> str(R.string.au_store_msg_link_unreadable)
                        summary.added > 0 -> str(R.string.au_store_msg_app_added)
                        summary.skipped > 0 -> str(R.string.au_store_msg_already_listed)
                        else -> str(R.string.au_store_msg_unsupported)
                    }
                }
                link.startsWith("obtainium://add/", true) -> onPrefill(link.substringAfter("obtainium://add/"))
                else -> onPrefill(link)
            }
        }
    }

    // ---- checking and installing ----

    fun checkAll() {
        if (_checking.value) return
        viewModelScope.launch {
            _checking.value = true
            val summary = try {
                runCatching { updater.checkAll(background = false) }.getOrNull()
            } finally {
                _checking.value = false
            }
            _message.value = when {
                summary == null -> str(R.string.au_store_msg_check_failed)
                summary.updates.isNotEmpty() -> str(R.string.au_store_msg_updates_available, summary.updates.size)
                summary.failed > 0 -> str(R.string.au_store_msg_up_to_date_unreachable, summary.failed)
                else -> str(R.string.au_store_msg_all_up_to_date)
            }
        }
    }

    fun checkOne(item: StoreItem) {
        if (_checking.value) return
        viewModelScope.launch {
            _checking.value = true
            val ok = try {
                runCatching { updater.checkOne(item) }.getOrDefault(false)
            } finally {
                _checking.value = false
            }
            if (!ok) _message.value = str(R.string.au_store_msg_source_unreachable, item.displayName)
        }
    }

    fun updateAll() {
        viewModelScope.launch {
            rows.value.filter { it.updateAvailable && !it.options.trackOnly }.forEach { row ->
                installNow(row.item)
            }
        }
    }

    /**
     * Installs/updates [item], or - if it's already installed and up to date - launches it. For apps
     * that are only tracked, the source page opens instead (they are never installed from here).
     */
    fun onAppActionClicked(item: StoreItem) {
        val o = options.item(item.id)
        if (item.installedVersionCode != null && !item.hasUpdate) {
            openApp(item.packageName)
            return
        }
        if (o.trackOnly) {
            openUrl(StoreUrlParser.toUrl(item.source))
            return
        }
        viewModelScope.launch { installNow(item) }
    }

    private suspend fun installNow(item: StoreItem) {
        setState(item.id, StoreInstallUiState.Downloading)
        val result = try {
            actionHandler.install(item)
        } catch (e: kotlinx.coroutines.CancellationException) {
            setState(item.id, StoreInstallUiState.Idle)
            throw e
        } catch (e: Exception) {
            StoreAction.Failed(e.message ?: str(R.string.au_store_msg_install_failed), e)
        }
        when (val action = result) {
            is StoreAction.Installed -> setState(item.id, StoreInstallUiState.Installed)
            is StoreAction.Installing -> setState(item.id, StoreInstallUiState.Installing)
            is StoreAction.LaunchedPlayStore -> setState(item.id, StoreInstallUiState.Idle)
            is StoreAction.Failed -> {
                setState(item.id, StoreInstallUiState.Failed)
                _message.value = "${item.displayName}: ${action.reason}"
            }
        }
    }

    fun uninstall(item: StoreItem) = actionHandler.uninstall(item.packageName)

    fun remove(item: StoreItem) {
        viewModelScope.launch {
            repository.deleteItem(item.id)
            options.remove(item.id)
        }
    }

    // ---- per-app settings ----

    fun setOptions(id: String, change: (ItemOptions) -> ItemOptions) = options.update(id, change)

    /** Changes the filter and the pre-release switch of an app's source */
    fun setSourceFilter(item: StoreItem, regex: String?, includePrereleases: Boolean) {
        val r = regex?.takeIf { it.isNotBlank() }
        val source = when (val s = item.source) {
            is AppSource.GitHub -> s.copy(assetNameRegex = r, includePrereleases = includePrereleases)
            is AppSource.GitLab -> s.copy(assetNameRegex = r, includePrereleases = includePrereleases)
            is AppSource.Gitea -> s.copy(assetNameRegex = r, includePrereleases = includePrereleases)
            is AppSource.SourceForge -> s.copy(assetNameRegex = r)
            is AppSource.Html -> s.copy(linkRegex = r)
            else -> return
        }
        viewModelScope.launch {
            repository.insertItem(item.copy(source = source))
            updater.checkOne(item.copy(source = source))
        }
    }

    fun rename(item: StoreItem, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.insertItem(item.copy(displayName = name.trim())) }
    }

    fun setGlobal(change: (GlobalOptions) -> GlobalOptions) = options.updateGlobal(change)

    // ---- import and export ----

    fun export(uri: Uri) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val json = tools.exportObtainium()
                    context.contentResolver.openOutputStream(uri)!!.use { it.write(json.toByteArray()) }
                }.isSuccess
            }
            _message.value = if (ok) str(R.string.au_store_msg_exported) else str(R.string.au_store_msg_export_failed)
        }
    }

    fun import(uri: Uri) {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() } }.getOrNull()
            }
            if (text == null) {
                _message.value = str(R.string.au_store_msg_read_failed)
                return@launch
            }
            runCatching { tools.importObtainium(text) }
                .onSuccess {
                    _message.value = listOfNotNull(
                        str(R.string.au_store_msg_imported_added, it.added),
                        if (it.skipped > 0) str(R.string.au_store_msg_imported_skipped, it.skipped) else null,
                        if (it.unsupported > 0) str(R.string.au_store_msg_imported_unsupported, it.unsupported) else null,
                    ).joinToString(", ")
                    if (it.added > 0) checkAll()
                }
                .onFailure { _message.value = str(R.string.au_store_msg_not_obtainium) }
        }
    }

    // ---- apps that are already installed ----

    suspend fun installedCandidates(): List<InstalledCandidate> = withContext(Dispatchers.IO) {
        val tracked = repository.getAll().map { it.packageName }.toSet()
        val pm = context.packageManager
        pm.getInstalledApplications(0)
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 && it.packageName != context.packageName && it.packageName !in tracked }
            .map { InstalledCandidate(it.packageName, pm.getApplicationLabel(it).toString()) }
            .sortedBy { it.label.lowercase() }
    }

    /** Looks each app up in F-Droid and IzzyOnDroid and adds those that are found. */
    fun importInstalled(selected: List<InstalledCandidate>, onDone: (found: Int) -> Unit) {
        viewModelScope.launch {
            var found = 0
            for (app in selected) {
                val source = runCatching { tools.findSourceForInstalled(app.packageName) }.getOrNull() ?: continue
                val item = StoreItem(
                    id = java.util.UUID.randomUUID().toString(),
                    packageName = app.packageName,
                    displayName = app.label,
                    source = source,
                )
                repository.insertItem(item)
                found++
            }
            if (found > 0) runCatching { updater.checkAll(background = false) }
            onDone(found)
        }
    }

    // ---- Telos apps ----

    val disabledTelosApps: StateFlow<Set<String>> = settings.disabledVirtualApps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    fun setTelosAppEnabled(key: String, enabled: Boolean) {
        if (enabled) de.mm20.launcher2.base.VirtualAppGuard.reset(context, key)
        settings.setVirtualAppEnabled(key, enabled)
    }

    // ---- helpers ----

    fun installState(id: String): StoreInstallUiState = installStates.value[id] ?: StoreInstallUiState.Idle

    private fun openApp(packageName: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    fun openUrl(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun setState(id: String, state: StoreInstallUiState) {
        _installStates.value = _installStates.value + (id to state)
    }
}
