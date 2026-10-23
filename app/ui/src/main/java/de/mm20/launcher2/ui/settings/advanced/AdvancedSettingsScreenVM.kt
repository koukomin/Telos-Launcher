package de.mm20.launcher2.ui.settings.advanced

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.freeze.ShizukuBootManager
import de.mm20.launcher2.freeze.ShizukuSystemStatus
import de.mm20.launcher2.preferences.ui.UiSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class AdvancedSettingsScreenVM(application: Application) : AndroidViewModel(application), KoinComponent {

    private val uiSettings: UiSettings by inject()

    val shizukuAutoStart = uiSettings.shizukuAutoStart

    private val _systemStatus = MutableStateFlow<ShizukuSystemStatus?>(null)
    val systemStatus: StateFlow<ShizukuSystemStatus?> = _systemStatus.asStateFlow()

    private val _verificationError = MutableStateFlow<String?>(null)
    val verificationError: StateFlow<String?> = _verificationError.asStateFlow()

    fun refreshStatus(context: Context) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                _systemStatus.value = ShizukuBootManager.checkSystemStatus(context)
            }
        }
    }

    fun setShizukuAutoStart(context: Context, enabled: Boolean, onVerified: () -> Unit, onFailed: (String) -> Unit) {
        viewModelScope.launch {
            if (enabled) {
                _verificationError.value = null
                val result = withContext(Dispatchers.IO) {
                    ShizukuBootManager.verifyAndStartShizuku(context, isBootTrigger = false)
                }
                if (result.isSuccess) {
                    uiSettings.setShizukuAutoStart(true)
                    refreshStatus(context)
                    onVerified()
                } else {
                    val err = result.exceptionOrNull()?.message ?: "Verification failed"
                    _verificationError.value = err
                    uiSettings.setShizukuAutoStart(false)
                    refreshStatus(context)
                    onFailed(err)
                }
            } else {
                uiSettings.setShizukuAutoStart(false)
                refreshStatus(context)
            }
        }
    }

    fun trySelfGrant(context: Context, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                ShizukuBootManager.trySelfGrantWriteSecureSettings(context)
            }
            refreshStatus(context)
            onDone(ok)
        }
    }

    fun clearError() {
        _verificationError.value = null
    }
}
