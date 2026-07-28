package de.mm20.launcher2.ui.settings.freeze

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.freeze.FreezeManager
import de.mm20.launcher2.preferences.FreezeBackendPreference
import de.mm20.launcher2.preferences.freeze.FreezeSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class DeviceOwnerSetupScreenVM : ViewModel(), KoinComponent {
    private val freezeManager: FreezeManager by inject()
    private val freezeSettings: FreezeSettings by inject()

    val adminComponent: String = freezeManager.deviceOwnerAdminComponent

    private val _isDeviceOwner = MutableStateFlow<Boolean?>(null)
    val isDeviceOwner: StateFlow<Boolean?> = _isDeviceOwner.asStateFlow()

    init {
        refresh()
    }

    /** Re-checks device owner status - call after the user switches back from a terminal/adb
     * session where they may have just run the setup or removal command. */
    fun refresh() {
        viewModelScope.launch {
            _isDeviceOwner.value = freezeManager.isDeviceOwner()
        }
    }

    /** Switches the freeze backend to Device Owner once it's confirmed active, so the user
     * doesn't also have to separately find and change the backend picker in Freeze settings. */
    fun useAsBackend() {
        freezeSettings.setBackend(FreezeBackendPreference.DeviceOwnerOnly)
        viewModelScope.launch { freezeManager.refreshBackendState() }
    }
}
