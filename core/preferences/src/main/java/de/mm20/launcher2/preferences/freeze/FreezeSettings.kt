package de.mm20.launcher2.preferences.freeze

import de.mm20.launcher2.preferences.FreezeAppStats
import de.mm20.launcher2.preferences.FreezeBackendPreference
import de.mm20.launcher2.preferences.FreezeExclusionStrictness
import de.mm20.launcher2.preferences.FreezeProfile
import de.mm20.launcher2.preferences.LauncherDataStore
import kotlinx.coroutines.flow.map

class FreezeSettings internal constructor(
    private val dataStore: LauncherDataStore,
) {
    val backend
        get() = dataStore.data.map { it.freezeBackend }

    fun setBackend(backend: FreezeBackendPreference) {
        dataStore.update { it.copy(freezeBackend = backend) }
    }

    val autoFreezeEnabled
        get() = dataStore.data.map { it.freezeAutoFreezeEnabled }

    fun setAutoFreezeEnabled(enabled: Boolean) {
        dataStore.update { it.copy(freezeAutoFreezeEnabled = enabled) }
    }

    val freezeOnScreenOff
        get() = dataStore.data.map { it.freezeOnScreenOff }

    fun setFreezeOnScreenOff(enabled: Boolean) {
        dataStore.update { it.copy(freezeOnScreenOff = enabled) }
    }

    val freezeOnIdle
        get() = dataStore.data.map { it.freezeOnIdle }

    fun setFreezeOnIdle(enabled: Boolean) {
        dataStore.update { it.copy(freezeOnIdle = enabled) }
    }

    val idleTimeoutMinutes
        get() = dataStore.data.map { it.freezeIdleTimeoutMinutes }

    fun setIdleTimeoutMinutes(minutes: Int) {
        dataStore.update { it.copy(freezeIdleTimeoutMinutes = minutes) }
    }

    val freezeOnBatterySaver
        get() = dataStore.data.map { it.freezeOnBatterySaver }

    fun setFreezeOnBatterySaver(enabled: Boolean) {
        dataStore.update { it.copy(freezeOnBatterySaver = enabled) }
    }

    val candidates
        get() = dataStore.data.map { it.freezeCandidates }

    fun setCandidateEnabled(packageName: String, enabled: Boolean) {
        dataStore.update {
            if (enabled) {
                it.copy(freezeCandidates = it.freezeCandidates + packageName)
            } else {
                it.copy(freezeCandidates = it.freezeCandidates - packageName)
            }
        }
    }

    val profile
        get() = dataStore.data.map { it.freezeProfile }

    fun setProfile(profile: FreezeProfile) {
        dataStore.update { it.copy(freezeProfile = profile) }
    }

    val exclusionStrictness
        get() = dataStore.data.map { it.freezeExclusionStrictness }

    fun setExclusionStrictness(strictness: FreezeExclusionStrictness) {
        dataStore.update { it.copy(freezeExclusionStrictness = strictness) }
    }

    val neverFreezeApps
        get() = dataStore.data.map { it.freezeNeverFreezeApps }

    fun setNeverFreeze(packageName: String, never: Boolean) {
        dataStore.update {
            if (never) {
                it.copy(freezeNeverFreezeApps = it.freezeNeverFreezeApps + packageName)
            } else {
                it.copy(freezeNeverFreezeApps = it.freezeNeverFreezeApps - packageName)
            }
        }
    }

    val stats
        get() = dataStore.data.map { it.freezeStats }

    fun recordFrozen(packageName: String, timestamp: Long) {
        dataStore.update {
            val current = it.freezeStats[packageName] ?: FreezeAppStats()
            it.copy(
                freezeStats = it.freezeStats + (packageName to current.copy(
                    freezeCount = current.freezeCount + 1,
                    lastFrozenAt = timestamp,
                ))
            )
        }
    }

    fun recordUnfrozen(packageName: String, timestamp: Long) {
        dataStore.update {
            val current = it.freezeStats[packageName] ?: FreezeAppStats()
            it.copy(
                freezeStats = it.freezeStats + (packageName to current.copy(
                    unfreezeCount = current.unfreezeCount + 1,
                    lastUnfrozenAt = timestamp,
                ))
            )
        }
    }
}
