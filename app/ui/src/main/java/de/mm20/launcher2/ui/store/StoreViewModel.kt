package de.mm20.launcher2.ui.store

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.store.action.StoreAction
import de.mm20.launcher2.store.action.StoreActionHandler
import de.mm20.launcher2.store.model.StoreItem
import de.mm20.launcher2.store.repository.StoreRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import de.mm20.launcher2.store.parser.StoreUrlParser
import de.mm20.launcher2.store.fetcher.StoreFetcherRegistry
import de.mm20.launcher2.store.model.AppSource
import java.util.UUID

/** Per-item state of an in-progress/finished [StoreViewModel.onAppActionClicked] call. */
enum class StoreInstallUiState {
    Idle,
    Downloading,
    /**
     * Either a privileged (Shizuku/root) install is running silently, or the standard
     * [android.content.pm.PackageInstaller] session has been handed off to the system's install
     * confirmation UI. In both cases the real completion signal is Room's `installedVersionCode`
     * catching up (written synchronously for Shizuku/root, or asynchronously by
     * `InstallResultReceiver` for the session API) - [StoreViewModel.installStates] drops this
     * state for an item as soon as Room shows it installed and current, rather than this enum
     * value itself ever being cleared by a direct callback.
     */
    Installing,
    Installed,
    Failed,
}

/**
 * Observes the Store's tracked apps and drives installs/updates through [StoreActionHandler].
 * Follows the `KoinComponent` + `by inject()` pattern (see `DockSettingsScreenVM`) since this VM
 * has a single, app-wide dependency shape rather than needing a `viewModelFactory`.
 */
class StoreViewModel : ViewModel(), KoinComponent {

    private val context: Context by inject()
    private val repository: StoreRepository by inject()
    private val actionHandler: StoreActionHandler by inject()
    // === TELOS_PENDING_REVIEW_START: telos_store_ui ===
    private val fetcherRegistry: StoreFetcherRegistry by inject()
    // === TELOS_PENDING_REVIEW_END: telos_store_ui ===

    val items: StateFlow<List<StoreItem>> = repository.observeItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _installStates = MutableStateFlow<Map<String, StoreInstallUiState>>(emptyMap())

    /**
     * [_installStates] filtered against [items]: once Room shows an item installed and with no
     * update pending, any lingering `Installing`/`Downloading` entry for it is dropped rather than
     * leaving the button stuck - this is what "unsticks" the UI after
     * [de.mm20.launcher2.data.store.installer.InstallResultReceiver] (or a synchronous
     * Shizuku/root install) writes the result to Room.
     */
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

    fun item(id: String): Flow<StoreItem?> = repository.observeItem(id)

    fun installState(id: String): StoreInstallUiState = installStates.value[id] ?: StoreInstallUiState.Idle

    // === TELOS_PENDING_REVIEW_START: telos_store_ui ===
    fun addAppFromUrl(url: String) {
        viewModelScope.launch {
            val source = StoreUrlParser.parseUrl(url) ?: return@launch
            
            // Try to fetch latest release to grab metadata and verify it's valid
            val release = fetcherRegistry.fetchLatestRelease(source) ?: return@launch
            
            val packageName = when (source) {
                is AppSource.FDroid -> source.packageName
                is AppSource.AffiliatePlayStore -> source.packageName
                else -> "unknown.package"
            }
            
            val displayName = when (source) {
                is AppSource.GitHub -> "${source.owner}/${source.repo}"
                is AppSource.FDroid -> source.packageName
                else -> "New App"
            }
            
            val item = StoreItem(
                id = UUID.randomUUID().toString(),
                packageName = packageName,
                displayName = displayName,
                source = source,
                latestRelease = release,
                lastCheckedAt = System.currentTimeMillis()
            )
            
            repository.insertItem(item)
        }
    }
    // === TELOS_PENDING_REVIEW_END: telos_store_ui ===

    /**
     * Installs/updates [item], or - if it's already installed and up to date - launches it
     * instead. The actual download/install work is entirely [actionHandler]'s; this function
     * only tracks per-item UI state around that call.
     */
    fun onAppActionClicked(item: StoreItem) {
        if (item.installedVersionCode != null && !item.hasUpdate) {
            openApp(item.packageName)
            return
        }

        viewModelScope.launch {
            setState(item.id, StoreInstallUiState.Downloading)
            when (val action = actionHandler.install(item)) {
                is StoreAction.Installed -> setState(item.id, StoreInstallUiState.Installed)
                is StoreAction.Installing -> setState(item.id, StoreInstallUiState.Installing)
                is StoreAction.LaunchedPlayStore -> setState(item.id, StoreInstallUiState.Idle)
                is StoreAction.Failed -> setState(item.id, StoreInstallUiState.Failed)
            }
        }
    }

    private fun openApp(packageName: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return
        context.startActivity(intent)
    }

    private fun setState(id: String, state: StoreInstallUiState) {
        _installStates.value = _installStates.value + (id to state)
    }
}
