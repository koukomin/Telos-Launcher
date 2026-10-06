// === TELOS_PENDING_REVIEW_START: comms_settings_engine ===
package de.mm20.launcher2.ui.settings.comms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.comms.backup.CommsBackupManager
import de.mm20.launcher2.comms.repository.ContactDirectoryRepository
import de.mm20.launcher2.preferences.comms.CommsSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class CommsSettingsScreenVM : ViewModel(), KoinComponent {

    private val commsSettings: CommsSettings by inject()
    private val contacts: ContactDirectoryRepository by inject()
    private val backup: CommsBackupManager by inject()

    val speedDials = commsSettings.speedDials
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyMap())

    fun setSpeedDial(digit: Int, number: String?) {
        commsSettings.setSpeedDial(digit, number)
    }

    val t9Alphabet = commsSettings.t9Alphabet
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), "latin")

    fun setT9Alphabet(alphabet: String) {
        commsSettings.setT9Alphabet(alphabet)
    }

    val defaultSim = commsSettings.defaultSim
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), "ask")

    fun setDefaultSim(sim: String) {
        commsSettings.setDefaultSim(sim)
    }

    val dialpadSounds = commsSettings.dialpadSounds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)
    fun setDialpadSounds(enabled: Boolean) {
        commsSettings.setDialpadSounds(enabled)
    }

    val dialpadVibration = commsSettings.dialpadVibration
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), true)
    fun setDialpadVibration(enabled: Boolean) {
        commsSettings.setDialpadVibration(enabled)
    }

    val vibrateOnAnswer = commsSettings.vibrateOnAnswer
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)
    fun setVibrateOnAnswer(enabled: Boolean) {
        commsSettings.setVibrateOnAnswer(enabled)
    }

    val vibrateOnHangup = commsSettings.vibrateOnHangup
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)
    fun setVibrateOnHangup(enabled: Boolean) {
        commsSettings.setVibrateOnHangup(enabled)
    }

    val clirPrefix = commsSettings.clirPrefix
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), "")
    fun setClirPrefix(prefix: String) {
        commsSettings.setClirPrefix(prefix)
    }

    val enableSpamBlocking = commsSettings.enableSpamBlocking
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)
    fun setEnableSpamBlocking(enabled: Boolean) {
        commsSettings.setEnableSpamBlocking(enabled)
    }

    val hideDialpadLetters = commsSettings.hideDialpadLetters
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)
    fun setHideDialpadLetters(enabled: Boolean) {
        commsSettings.setHideDialpadLetters(enabled)
    }

    val blockHiddenNumbers = commsSettings.blockHiddenNumbers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)
    fun setBlockHiddenNumbers(enabled: Boolean) {
        commsSettings.setBlockHiddenNumbers(enabled)
    }

    val blockUnknownNumbers = commsSettings.blockUnknownNumbers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)
    fun setBlockUnknownNumbers(enabled: Boolean) {
        commsSettings.setBlockUnknownNumbers(enabled)
    }

    val blockInternational = commsSettings.blockInternational
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)
    fun setBlockInternational(enabled: Boolean) {
        commsSettings.setBlockInternational(enabled)
    }

    val clirEnabled = commsSettings.clirEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)
    fun setClirEnabled(enabled: Boolean) {
        commsSettings.setClirEnabled(enabled)
    }

    val autoRecordCalls = commsSettings.autoRecordCalls
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)
    fun setAutoRecordCalls(enabled: Boolean) {
        commsSettings.setAutoRecordCalls(enabled)
    }

    val recordingQuality = commsSettings.recordingQuality
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), "BALANCED")
    fun setRecordingQuality(quality: String) {
        commsSettings.setRecordingQuality(quality)
    }

    val recordingBackend = commsSettings.recordingBackend
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), "auto")
    fun setRecordingBackend(backend: String) {
        commsSettings.setRecordingBackend(backend)
    }

    val rememberDialpad = commsSettings.rememberDialpad
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), true)
    fun setRememberDialpad(enabled: Boolean) = commsSettings.setRememberDialpad(enabled)

    val confirmBeforeCall = commsSettings.confirmBeforeCall
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)
    fun setConfirmBeforeCall(enabled: Boolean) = commsSettings.setConfirmBeforeCall(enabled)

    val tapToCall = commsSettings.tapToCall
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), true)
    fun setTapToCall(enabled: Boolean) = commsSettings.setTapToCall(enabled)

    val autoRedial = commsSettings.autoRedial
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)
    fun setAutoRedial(enabled: Boolean) = commsSettings.setAutoRedial(enabled)

    val autoOpenDialpad = commsSettings.autoOpenDialpad
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)
    fun setAutoOpenDialpad(enabled: Boolean) = commsSettings.setAutoOpenDialpad(enabled)

    val rejectSmsTemplate = commsSettings.rejectSmsTemplate
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), "I'll call you back")
    fun setRejectSmsTemplate(text: String) = commsSettings.setRejectSmsTemplate(text)

    val secureCallScreen = commsSettings.secureCallScreen.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)
    fun setSecureCallScreen(enabled: Boolean) = commsSettings.setSecureCallScreen(enabled)
    val answerStyle = commsSettings.answerStyle.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), "buttons")
    fun setAnswerStyle(style: String) = commsSettings.setAnswerStyle(style)
    val recordingAutoDeleteDays = commsSettings.recordingAutoDeleteDays.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), 0)
    fun setRecordingAutoDeleteDays(days: Int) = commsSettings.setRecordingAutoDeleteDays(days)
    val pocketMode = commsSettings.pocketMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)
    fun setPocketMode(enabled: Boolean) = commsSettings.setPocketMode(enabled)
    val proximitySpeaker = commsSettings.proximitySpeaker.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)
    fun setProximitySpeaker(enabled: Boolean) = commsSettings.setProximitySpeaker(enabled)
    val showNumbersInRecents = commsSettings.showNumbersInRecents.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), true)
    fun setShowNumbersInRecents(enabled: Boolean) = commsSettings.setShowNumbersInRecents(enabled)
    val missedCallPopup = commsSettings.missedCallPopup.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), true)
    fun setMissedCallPopup(enabled: Boolean) = commsSettings.setMissedCallPopup(enabled)
    val postCallPopup = commsSettings.postCallPopup.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)
    fun setPostCallPopup(enabled: Boolean) = commsSettings.setPostCallPopup(enabled)
    val inCallNotes = commsSettings.inCallNotes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), true)
    fun setInCallNotes(enabled: Boolean) = commsSettings.setInCallNotes(enabled)

    val hideFromContacts = commsSettings.hideFromContacts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), true)
    fun setHideFromContacts(enabled: Boolean) = commsSettings.setHideFromContacts(enabled)
    val hideFromRecents = commsSettings.hideFromRecents.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), true)
    fun setHideFromRecents(enabled: Boolean) = commsSettings.setHideFromRecents(enabled)
    val maskHiddenIncoming = commsSettings.maskHiddenIncoming.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), true)
    fun setMaskHiddenIncoming(enabled: Boolean) = commsSettings.setMaskHiddenIncoming(enabled)
    val stealthHiderMenu = commsSettings.stealthHiderMenu.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)
    fun setStealthHiderMenu(enabled: Boolean) = commsSettings.setStealthHiderMenu(enabled)
    val phoneAppLock = commsSettings.phoneAppLock.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)
    fun setPhoneAppLock(enabled: Boolean) = commsSettings.setPhoneAppLock(enabled)
    val callProtectMode = commsSettings.callProtectMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), "none")
    fun setCallProtectMode(mode: String) = commsSettings.setCallProtectMode(mode)

    fun setPin(pin: String) {
        de.mm20.launcher2.comms.AuthManager().setCustomPin(pin)
    }

    val raiseToAnswer = commsSettings.raiseToAnswer.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)
    fun setRaiseToAnswer(enabled: Boolean) = commsSettings.setRaiseToAnswer(enabled)
    val flipToDecline = commsSettings.flipToDecline.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)
    fun setFlipToDecline(enabled: Boolean) = commsSettings.setFlipToDecline(enabled)
    val rainMode = commsSettings.rainMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)
    fun setRainMode(enabled: Boolean) = commsSettings.setRainMode(enabled)
    val volumeDnd = commsSettings.volumeDnd.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)
    fun setVolumeDnd(enabled: Boolean) = commsSettings.setVolumeDnd(enabled)
    val volumeDndLockOnly = commsSettings.volumeDndLockOnly.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), true)
    fun setVolumeDndLockOnly(enabled: Boolean) = commsSettings.setVolumeDndLockOnly(enabled)
    val preferredNetworkMode = commsSettings.preferredNetworkMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), "auto")
    fun setPreferredNetworkMode(mode: String) = commsSettings.setPreferredNetworkMode(mode)
    val networkBackend = commsSettings.networkBackend.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), "auto")
    fun setNetworkBackend(backend: String) = commsSettings.setNetworkBackend(backend)
    val screenOffLte = commsSettings.screenOffLte.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)
    fun setScreenOffLte(enabled: Boolean) = commsSettings.setScreenOffLte(enabled)
    val batterySaverLte = commsSettings.batterySaverLte.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)
    fun setBatterySaverLte(enabled: Boolean) = commsSettings.setBatterySaverLte(enabled)

    fun exportBackup(password: String, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            onDone(runCatching { backup.exportEncrypted(password) }.getOrNull())
        }
    }

    fun importBackup(payload: String, password: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            onDone(backup.importEncrypted(payload, password))
        }
    }

    fun importVcf(content: String, onDone: (Int) -> Unit) {
        viewModelScope.launch {
            onDone(contacts.importVcf(content))
        }
    }
}
// === TELOS_PENDING_REVIEW_END: comms_settings_engine ===
