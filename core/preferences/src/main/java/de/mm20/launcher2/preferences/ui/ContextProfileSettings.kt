package de.mm20.launcher2.preferences.ui

import de.mm20.launcher2.preferences.ContextProfile
import de.mm20.launcher2.preferences.LauncherDataStore
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class ContextProfileSettings internal constructor(
    private val dataStore: LauncherDataStore,
) {
    val enabled
        get() = dataStore.data.map { it.contextProfilesEnabled }.distinctUntilChanged()

    fun setEnabled(enabled: Boolean) {
        dataStore.update { it.copy(contextProfilesEnabled = enabled) }
    }

    val profiles
        get() = dataStore.data.map { it.contextProfiles }.distinctUntilChanged()

    fun setProfile(profile: ContextProfile) {
        dataStore.update {
            val existing = it.contextProfiles.indexOfFirst { p -> p.id == profile.id }
            val updated = if (existing >= 0) {
                it.contextProfiles.toMutableList().apply { set(existing, profile) }
            } else {
                it.contextProfiles + profile
            }
            it.copy(contextProfiles = updated)
        }
    }

    fun deleteProfile(id: String) {
        dataStore.update {
            it.copy(
                contextProfiles = it.contextProfiles.filterNot { p -> p.id == id },
                contextProfileManualOverrideId = it.contextProfileManualOverrideId?.takeIf { existing -> existing != id },
            )
        }
    }

    val manualOverrideId
        get() = dataStore.data.map { it.contextProfileManualOverrideId }.distinctUntilChanged()

    /** Force-activates [id] regardless of trigger evaluation, or clears the override if null. */
    fun setManualOverride(id: String?) {
        dataStore.update { it.copy(contextProfileManualOverrideId = id) }
    }
}
