// === TELOS_PENDING_REVIEW_START: comms_settings_engine ===
package de.mm20.launcher2.preferences.comms

import de.mm20.launcher2.preferences.LauncherDataStore
import kotlinx.coroutines.flow.map

class CommsSettings internal constructor(
    private val dataStore: LauncherDataStore,
) {
    val speedDials
        get() = dataStore.data.map { it.comms.speedDials }

    fun setSpeedDial(digit: Int, number: String?) {
        dataStore.update { data ->
            val currentMap = data.comms.speedDials.toMutableMap()
            if (number.isNullOrEmpty()) {
                currentMap.remove(digit)
            } else {
                currentMap[digit] = number
            }
            data.copy(comms = data.comms.copy(speedDials = currentMap))
        }
    }

    val t9Alphabet
        get() = dataStore.data.map { it.comms.t9Alphabet }

    fun setT9Alphabet(alphabet: String) {
        dataStore.update { it.copy(comms = it.comms.copy(t9Alphabet = alphabet)) }
    }

    val defaultSim
        get() = dataStore.data.map { it.comms.defaultSim }

    fun setDefaultSim(sim: String) {
        dataStore.update { it.copy(comms = it.comms.copy(defaultSim = sim)) }
    }

    val dialpadSounds
        get() = dataStore.data.map { it.comms.dialpadSounds }
    fun setDialpadSounds(enabled: Boolean) {
        dataStore.update { it.copy(comms = it.comms.copy(dialpadSounds = enabled)) }
    }

    val dialpadVibration
        get() = dataStore.data.map { it.comms.dialpadVibration }
    fun setDialpadVibration(enabled: Boolean) {
        dataStore.update { it.copy(comms = it.comms.copy(dialpadVibration = enabled)) }
    }

    val vibrateOnAnswer
        get() = dataStore.data.map { it.comms.vibrateOnAnswer }
    fun setVibrateOnAnswer(enabled: Boolean) {
        dataStore.update { it.copy(comms = it.comms.copy(vibrateOnAnswer = enabled)) }
    }

    val vibrateOnHangup
        get() = dataStore.data.map { it.comms.vibrateOnHangup }
    fun setVibrateOnHangup(enabled: Boolean) {
        dataStore.update { it.copy(comms = it.comms.copy(vibrateOnHangup = enabled)) }
    }
}
// === TELOS_PENDING_REVIEW_END: comms_settings_engine ===
