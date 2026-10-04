// === TELOS_PENDING_REVIEW_START: comms_settings_engine ===
package de.mm20.launcher2.ui.settings.comms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.preferences.comms.CommsSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class CommsSettingsScreenVM : ViewModel(), KoinComponent {

    private val commsSettings: CommsSettings by inject()

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
}
// === TELOS_PENDING_REVIEW_END: comms_settings_engine ===
