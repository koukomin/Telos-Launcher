package de.mm20.launcher2.preferences.freeze

import de.mm20.launcher2.preferences.FreezeAppStats
import de.mm20.launcher2.preferences.FreezeBackendPreference
import de.mm20.launcher2.preferences.FreezeExclusionStrictness
import de.mm20.launcher2.preferences.FreezeMethod
import de.mm20.launcher2.preferences.FreezeProfile
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.preferences.FrozenAppStyle
import kotlinx.coroutines.flow.map

class FreezeSettings internal constructor(
    private val dataStore: LauncherDataStore,
) {
    // === TELOS_PENDING_REVIEW_START: ui_frozen_apps_style ===
    val frozenAppStyle
        get() = dataStore.data.map { it.freeze.frozenAppStyle }
    
    fun setFrozenAppStyle(style: FrozenAppStyle) {
        dataStore.update { it.copy(freeze = it.freeze.copy(frozenAppStyle = style)) }
    }
    // === TELOS_PENDING_REVIEW_END: ui_frozen_apps_style ===

    val backend
        get() = dataStore.data.map { it.freeze.freezeBackend }

    fun setBackend(backend: FreezeBackendPreference) {
        dataStore.update { it.copy(freeze = it.freeze.copy(freezeBackend = backend)) }
    }

    val autoFreezeEnabled
        get() = dataStore.data.map { it.freeze.freezeAutoFreezeEnabled }

    fun setAutoFreezeEnabled(enabled: Boolean) {
        dataStore.update { it.copy(freeze = it.freeze.copy(freezeAutoFreezeEnabled = enabled)) }
    }

    val freezeOnScreenOff
        get() = dataStore.data.map { it.freeze.freezeOnScreenOff }

    fun setFreezeOnScreenOff(enabled: Boolean) {
        dataStore.update { it.copy(freeze = it.freeze.copy(freezeOnScreenOff = enabled)) }
    }

    val freezeOnIdle
        get() = dataStore.data.map { it.freeze.freezeOnIdle }

    fun setFreezeOnIdle(enabled: Boolean) {
        dataStore.update { it.copy(freeze = it.freeze.copy(freezeOnIdle = enabled)) }
    }

    val idleTimeoutMinutes
        get() = dataStore.data.map { it.freeze.freezeIdleTimeoutMinutes }

    fun setIdleTimeoutMinutes(minutes: Int) {
        dataStore.update { it.copy(freeze = it.freeze.copy(freezeIdleTimeoutMinutes = minutes)) }
    }

    val freezeOnBatterySaver
        get() = dataStore.data.map { it.freeze.freezeOnBatterySaver }

    fun setFreezeOnBatterySaver(enabled: Boolean) {
        dataStore.update { it.copy(freeze = it.freeze.copy(freezeOnBatterySaver = enabled)) }
    }

    val candidates
        get() = dataStore.data.map { it.freeze.freezeCandidates }

    fun setCandidateEnabled(packageName: String, enabled: Boolean) {
        dataStore.update {
            if (enabled) {
                it.copy(freeze = it.freeze.copy(freezeCandidates = it.freeze.freezeCandidates + packageName))
            } else {
                it.copy(freeze = it.freeze.copy(freezeCandidates = it.freeze.freezeCandidates - packageName))
            }
        }
    }

    val profile
        get() = dataStore.data.map { it.freeze.freezeProfile }

    fun setProfile(profile: FreezeProfile) {
        dataStore.update { it.copy(freeze = it.freeze.copy(freezeProfile = profile)) }
    }

    val exclusionStrictness
        get() = dataStore.data.map { it.freeze.freezeExclusionStrictness }

    fun setExclusionStrictness(strictness: FreezeExclusionStrictness) {
        dataStore.update { it.copy(freeze = it.freeze.copy(freezeExclusionStrictness = strictness)) }
    }

    val neverFreezeApps
        get() = dataStore.data.map { it.freeze.freezeNeverFreezeApps }

    fun setNeverFreeze(packageName: String, never: Boolean) {
        dataStore.update {
            if (never) {
                it.copy(freeze = it.freeze.copy(freezeNeverFreezeApps = it.freeze.freezeNeverFreezeApps + packageName))
            } else {
                it.copy(freeze = it.freeze.copy(freezeNeverFreezeApps = it.freeze.freezeNeverFreezeApps - packageName))
            }
        }
    }

    val excludeMusic
        get() = dataStore.data.map { it.freeze.freezeExcludeMusic }

    fun setExcludeMusic(exclude: Boolean) {
        dataStore.update { it.copy(freeze = it.freeze.copy(freezeExcludeMusic = exclude)) }
    }

    val excludeNetwork
        get() = dataStore.data.map { it.freeze.freezeExcludeNetwork }

    fun setExcludeNetwork(exclude: Boolean) {
        dataStore.update { it.copy(freeze = it.freeze.copy(freezeExcludeNetwork = exclude)) }
    }

    val networkThresholdKb
        get() = dataStore.data.map { it.freeze.freezeNetworkThresholdKb }

    fun setNetworkThresholdKb(threshold: Int) {
        dataStore.update { it.copy(freeze = it.freeze.copy(freezeNetworkThresholdKb = threshold)) }
    }

    val freezeMethods
        get() = dataStore.data.map { it.freeze.freezeMethods }

    fun setFreezeMethod(packageName: String, method: FreezeMethod?) {
        dataStore.update {
            if (method == null) {
                it.copy(freeze = it.freeze.copy(freezeMethods = it.freeze.freezeMethods - packageName))
            } else {
                it.copy(freeze = it.freeze.copy(freezeMethods = it.freeze.freezeMethods + (packageName to method)))
            }
        }
    }

    val advancedFeaturesEnabled
        get() = dataStore.data.map { it.freeze.freezeAdvancedFeaturesEnabled }

    fun setAdvancedFeaturesEnabled(enabled: Boolean) {
        dataStore.update { it.copy(freeze = it.freeze.copy(freezeAdvancedFeaturesEnabled = enabled)) }
    }

    val hideFromLauncher
        get() = dataStore.data.map { it.freeze.freezeHideFromLauncher }

    fun setHideFromLauncher(hide: Boolean) {
        dataStore.update { it.copy(freeze = it.freeze.copy(freezeHideFromLauncher = hide)) }
    }

    val stats
        get() = dataStore.data.map { it.freeze.freezeStats }

    fun recordFrozen(packageName: String, timestamp: Long) {
        dataStore.update {
            val current = it.freeze.freezeStats[packageName] ?: FreezeAppStats()
            it.copy(
                freeze = it.freeze.copy(
                    freezeStats = it.freeze.freezeStats + (packageName to current.copy(
                        freezeCount = current.freezeCount + 1,
                        lastFrozenAt = timestamp,
                    ))
                )
            )
        }
    }

    fun recordUnfrozen(packageName: String, timestamp: Long) {
        dataStore.update {
            val current = it.freeze.freezeStats[packageName] ?: FreezeAppStats()
            it.copy(
                freeze = it.freeze.copy(
                    freezeStats = it.freeze.freezeStats + (packageName to current.copy(
                        unfreezeCount = current.unfreezeCount + 1,
                        lastUnfrozenAt = timestamp,
                    ))
                )
            )
        }
    }
}
