package de.mm20.launcher2.ui.comms

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.comms.model.CallLogEntry
import de.mm20.launcher2.comms.model.DialerContact
import de.mm20.launcher2.comms.repository.CallLogRepository
import de.mm20.launcher2.comms.repository.ContactDirectoryRepository
import de.mm20.launcher2.comms.t9.T9SearchEngine
import de.mm20.launcher2.comms.AuthManager
import de.mm20.launcher2.comms.privacy.HiddenContacts
import de.mm20.launcher2.comms.privacy.PrivacySession
import de.mm20.launcher2.ui.R
import kotlinx.coroutines.Dispatchers
import de.mm20.launcher2.preferences.comms.CommsSettings
import android.telephony.PhoneNumberUtils
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Backs [DialpadScreen]: the typed number plus its live T9 contact matches. */
class DialpadViewModel : ViewModel(), KoinComponent {

    private val contactDirectory: ContactDirectoryRepository by inject()
    private val callLogRepository: CallLogRepository by inject()
    private val t9SearchEngine: T9SearchEngine by inject()
    private val commsSettings: CommsSettings by inject()
    private val authManager = AuthManager()

    val speedDials = commsSettings.speedDials
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyMap())
    val t9Alphabet = commsSettings.t9Alphabet
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), "latin")
    val dialpadSounds = commsSettings.dialpadSounds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), true)
    val dialpadVibration = commsSettings.dialpadVibration
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), true)
    val hideDialpadLetters = commsSettings.hideDialpadLetters
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)
    val clirEnabled = commsSettings.clirEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)
    val clirPrefix = commsSettings.clirPrefix
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), "")
    val recents: StateFlow<List<CallLogEntry>> = combine(
        callLogRepository.observeRecents(),
        commsSettings.hiddenNumbers,
        commsSettings.hideFromRecents,
        PrivacySession.hiderUnlocked,
    ) { list, hidden, hide, unlocked ->
        if (!hide || unlocked) list
        else list.filter { !HiddenContacts.matches(it.phoneNumber, hidden) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input.map {
        runCatching { PhoneNumberUtils.formatNumber(it, Locale.getDefault().country) }.getOrNull() ?: it
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    private val _isVaultUnlocked = MutableStateFlow(false)
    val isVaultUnlocked: StateFlow<Boolean> = _isVaultUnlocked

    private val _vaultAuthRequested = MutableStateFlow(false)
    val vaultAuthRequested: StateFlow<Boolean> = _vaultAuthRequested

    // hidden contacts must not show up in the T9 suggestions while the hider is locked
    private val contacts = combine(
        contactDirectory.observeContacts(),
        commsSettings.hiddenNumbers,
        commsSettings.hideFromContacts,
        PrivacySession.hiderUnlocked,
    ) { list, hidden, hide, unlocked ->
        if (!hide || unlocked) list
        else list.filter { !HiddenContacts.contactHidden(it, hidden) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val t9Results: StateFlow<List<DialerContact>> = combine(_input, contacts) { query, contacts ->
        t9SearchEngine.search(query, contacts)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            if (commsSettings.rememberDialpad.first() && _input.value.isEmpty()) {
                val saved = commsSettings.lastDialpadDigits.first()
                if (saved.isNotEmpty()) _input.value = saved
            }
        }
    }

    fun seedInput(number: String) {
        if (number.isEmpty()) return
        _input.value = number.filter { it.isDigit() || it == '+' || it == '*' || it == '#' || it == ';' || it == ',' }
        persistInput()
    }

    fun onKeyPressed(key: Char) {
        val newInput = _input.value + key
        _input.value = newInput
        persistInput()
        checkVaultTrigger(newInput)
    }

    private fun checkVaultTrigger(currentInput: String) {
        val vaultPattern = Regex("^#\\d{4,6}#$")
        if (vaultPattern.matches(currentInput)) {
            _input.value = ""
            // To be secure, the actual PIN validation happens via AuthManager
            val pin = currentInput.removeSurrounding("#")
            // the PIN hash is deliberately slow, so it must not run on the main thread
            viewModelScope.launch(Dispatchers.Default) {
                if (authManager.hasCustomPin()) {
                    if (authManager.authenticateCustom(pin)) {
                        PrivacySession.unlockHider()
                        _isVaultUnlocked.value = true
                    }
                } else {
                    _vaultAuthRequested.value = true
                }
            }
        }
    }

    fun onVaultAuthSuccess() {
        _vaultAuthRequested.value = false
        PrivacySession.unlockHider()
        _isVaultUnlocked.value = true
    }

    /** The biometric prompt is finished (successful or not): the next vault code may ask again */
    fun onVaultAuthHandled() {
        _vaultAuthRequested.value = false
    }

    /** The screen has navigated to the hidden contacts; the event must not fire a second time */
    fun onVaultNavigated() {
        _isVaultUnlocked.value = false
    }

    fun onBackspace() {
        _input.value = _input.value.dropLast(1)
        persistInput()
    }

    fun onClear() {
        _input.value = ""
        persistInput()
    }

    private fun persistInput() {
        viewModelScope.launch {
            // "#1234": a vault PIN being typed must not be written to disk
            val typed = _input.value.takeUnless { it.startsWith("#") }.orEmpty()
            if (commsSettings.rememberDialpad.first()) {
                commsSettings.setLastDialpadDigits(typed)
            }
        }
    }

    fun dialVoicemail(context: Context) {
        val tm = context.getSystemService(android.telephony.TelephonyManager::class.java)
        val number = runCatching { tm?.voiceMailNumber }.getOrNull().orEmpty()
        if (number.isNotEmpty()) dial(context, number)
    }

    /**
     * Hands the typed number to the system dialer via [Intent.ACTION_DIAL] (pre-filled, requires
     * no permission) rather than [Intent.ACTION_CALL] (would place the call directly and needs
     * runtime `CALL_PHONE` permission handling) - the user still confirms the call themselves.
     */
    fun dial(context: Context, phoneNumber: String? = null) {
        val number = phoneNumber ?: _input.value
        if (number.isEmpty()) return
        viewModelScope.launch {
            de.mm20.launcher2.comms.privacy.CallGuard.place(context, number)
        }
    }

    fun dialSim(context: Context, phoneNumber: String, handle: android.telecom.PhoneAccountHandle) {
        if (phoneNumber.isEmpty()) return
        viewModelScope.launch {
            de.mm20.launcher2.comms.privacy.CallGuard.place(context, phoneNumber, handle)
        }
    }

    fun dialSip(context: Context, phoneNumber: String? = null) {
        val number = phoneNumber ?: _input.value
        if (number.isEmpty()) return
        viewModelScope.launch {
            val ok = de.mm20.launcher2.comms.privacy.CallGuard.placeSip(context, number)
            if (!ok) {
                android.widget.Toast.makeText(
                    context,
                    de.mm20.launcher2.comms.sip.SipDialer.lastFailure
                        .ifBlank { context.getString(R.string.au_phonea_sip_not_connected) },
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    fun deleteRecent(call: CallLogEntry) {
        viewModelScope.launch { callLogRepository.deleteById(call.id) }
    }
}
