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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Per-item state of an in-progress/finished [StoreViewModel.onAppActionClicked] call. */
enum class StoreInstallUiState {
    Idle,
    Downloading,
    /**
     * Either a privileged (Shizuku/root) install is running silently, or the standard
     * [android.content.pm.PackageInstaller] session has been handed off to the system's install
     * confirmation UI. The latter case has no further callback wired up yet - the state simply
     * stays `Installing` until the item list itself refreshes (e.g. after the user returns to
     * Telos and `installedVersionCode` catches up), rather than guessing at an outcome.
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

    val items: StateFlow<List<StoreItem>> = repository.observeItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _installStates = MutableStateFlow<Map<String, StoreInstallUiState>>(emptyMap())
    val installStates: StateFlow<Map<String, StoreInstallUiState>> = _installStates.asStateFlow()

    fun item(id: String): Flow<StoreItem?> = repository.observeItem(id)

    fun installState(id: String): StoreInstallUiState = installStates.value[id] ?: StoreInstallUiState.Idle

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
