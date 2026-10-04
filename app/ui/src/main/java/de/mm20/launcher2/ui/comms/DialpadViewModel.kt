package de.mm20.launcher2.ui.comms

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.comms.model.DialerContact
import de.mm20.launcher2.comms.repository.ContactDirectoryRepository
import de.mm20.launcher2.comms.t9.T9SearchEngine
import de.mm20.launcher2.comms.AuthManager
import de.mm20.launcher2.preferences.comms.CommsSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Backs [DialpadScreen]: the typed number plus its live T9 contact matches. */
class DialpadViewModel : ViewModel(), KoinComponent {

    private val contactDirectory: ContactDirectoryRepository by inject()
    private val t9SearchEngine: T9SearchEngine by inject()
    private val commsSettings: CommsSettings by inject()
    private val authManager = AuthManager()

    val speedDials = commsSettings.speedDials
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyMap())

    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input

    private val _isVaultUnlocked = MutableStateFlow(false)
    val isVaultUnlocked: StateFlow<Boolean> = _isVaultUnlocked

    private val _vaultAuthRequested = MutableStateFlow(false)
    val vaultAuthRequested: StateFlow<Boolean> = _vaultAuthRequested

    private val contacts = contactDirectory.observeContacts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val t9Results: StateFlow<List<DialerContact>> = combine(_input, contacts) { query, contacts ->
        t9SearchEngine.search(query, contacts)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onKeyPressed(key: Char) {
        val newInput = _input.value + key
        _input.value = newInput
        checkVaultTrigger(newInput)
    }

    private fun checkVaultTrigger(currentInput: String) {
        val vaultPattern = Regex("^#\\d{4,6}#$")
        if (vaultPattern.matches(currentInput)) {
            _input.value = ""
            // To be secure, the actual PIN validation happens via AuthManager
            val pin = currentInput.removeSurrounding("#")
            if (authManager.hasCustomPin() && authManager.authenticateCustom(pin)) {
                _isVaultUnlocked.value = true
            } else if (!authManager.hasCustomPin()) {
                // Initial setup or native auth request
                _vaultAuthRequested.value = true
            }
        }
    }

    fun onVaultAuthSuccess() {
        _isVaultUnlocked.value = true
        _vaultAuthRequested.value = false
    }

    fun onBackspace() {
        _input.value = _input.value.dropLast(1)
    }

    fun onClear() {
        _input.value = ""
    }

    /**
     * Hands the typed number to the system dialer via [Intent.ACTION_DIAL] (pre-filled, requires
     * no permission) rather than [Intent.ACTION_CALL] (would place the call directly and needs
     * runtime `CALL_PHONE` permission handling) - the user still confirms the call themselves.
     */
    fun dial(context: Context, phoneNumber: String = _input.value) {
        if (phoneNumber.isEmpty()) return
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(phoneNumber)}"))
        context.startActivity(intent)
    }
}
