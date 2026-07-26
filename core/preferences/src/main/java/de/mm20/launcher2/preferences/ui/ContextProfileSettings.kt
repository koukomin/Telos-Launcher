package de.mm20.launcher2.preferences.ui

import de.mm20.launcher2.preferences.ContextProfile
import de.mm20.launcher2.preferences.LauncherDataStore
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class ContextProfileSettings internal constructor(
    private val dataStore: LauncherDataStore,
) {
    val enabled
        get() = dataStore.data.map { it.contextProfiles.contextProfilesEnabled }.distinctUntilChanged()

    fun setEnabled(enabled: Boolean) {
        dataStore.update { it.copy(contextProfiles = it.contextProfiles.copy(contextProfilesEnabled = enabled)) }
    }

    val profiles
        get() = dataStore.data.map { it.contextProfiles.contextProfiles }.distinctUntilChanged()

    fun setProfile(profile: ContextProfile) {
        dataStore.update {
            val existing = it.contextProfiles.contextProfiles.indexOfFirst { p -> p.id == profile.id }
            val updated = if (existing >= 0) {
                it.contextProfiles.contextProfiles.toMutableList().apply { set(existing, profile) }
            } else {
                it.contextProfiles.contextProfiles + profile
            }
            it.copy(contextProfiles = it.contextProfiles.copy(contextProfiles = updated))
        }
    }

    fun deleteProfile(id: String) {
        dataStore.update {
            it.copy(
                contextProfiles = it.contextProfiles.copy(
                    contextProfiles = it.contextProfiles.contextProfiles.filterNot { p -> p.id == id },
                    contextProfileManualOverrideId = it.contextProfiles.contextProfileManualOverrideId?.takeIf { existing -> existing != id },
                ),
            )
        }
    }

    val manualOverrideId
        get() = dataStore.data.map { it.contextProfiles.contextProfileManualOverrideId }.distinctUntilChanged()

    /** Force-activates [id] regardless of trigger evaluation, or clears the override if null. */
    fun setManualOverride(id: String?) {
        dataStore.update { it.copy(contextProfiles = it.contextProfiles.copy(contextProfileManualOverrideId = id)) }
    }
}
