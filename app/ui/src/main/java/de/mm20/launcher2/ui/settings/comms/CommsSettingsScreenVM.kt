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
}
// === TELOS_PENDING_REVIEW_END: comms_settings_engine ===
