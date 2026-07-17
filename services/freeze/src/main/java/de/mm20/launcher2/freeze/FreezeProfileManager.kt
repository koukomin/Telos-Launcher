package de.mm20.launcher2.freeze

import de.mm20.launcher2.contextprofiles.ContextProfileManager
import de.mm20.launcher2.preferences.FreezeExclusionStrictness
import de.mm20.launcher2.preferences.FreezeProfile
import de.mm20.launcher2.preferences.freeze.FreezeSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * The set of tunables a [FreezeProfile] controls. For the four fixed profiles these come from
 * [FreezeProfilePresets]; for [FreezeProfile.Custom] they come straight from the user's own
 * [FreezeSettings] values.
 */
data class ResolvedFreezeSettings(
    val idleTimeoutMinutes: Int,
    val freezeOnIdle: Boolean,
    val freezeOnScreenOff: Boolean,
    val freezeOnBatterySaver: Boolean,
    val exclusionStrictness: FreezeExclusionStrictness,
)

/**
 * Fixed presets for every profile except [FreezeProfile.Custom]. [FreezeExclusionStrictness.Relaxed]
 * is only ever used for [FreezeProfile.UltraAggressive], and even then it only skips the active
 * media-session exclusion check - foreground app, active notification, foreground service, and
 * Android Auto are always enforced regardless of profile or strictness (see
 * [FreezeExclusionChecker]).
 */
internal val FreezeProfilePresets: Map<FreezeProfile, ResolvedFreezeSettings> = mapOf(
    FreezeProfile.BatterySaver to ResolvedFreezeSettings(
        idleTimeoutMinutes = 30,
        freezeOnIdle = true,
        freezeOnScreenOff = false,
        freezeOnBatterySaver = true,
        exclusionStrictness = FreezeExclusionStrictness.Strict,
    ),
    FreezeProfile.Balanced to ResolvedFreezeSettings(
        idleTimeoutMinutes = 15,
        freezeOnIdle = true,
        freezeOnScreenOff = false,
        freezeOnBatterySaver = true,
        exclusionStrictness = FreezeExclusionStrictness.Strict,
    ),
    FreezeProfile.Aggressive to ResolvedFreezeSettings(
        idleTimeoutMinutes = 5,
        freezeOnIdle = true,
        freezeOnScreenOff = true,
        freezeOnBatterySaver = true,
        exclusionStrictness = FreezeExclusionStrictness.Strict,
    ),
    FreezeProfile.UltraAggressive to ResolvedFreezeSettings(
        idleTimeoutMinutes = 1,
        freezeOnIdle = true,
        freezeOnScreenOff = true,
        freezeOnBatterySaver = true,
        exclusionStrictness = FreezeExclusionStrictness.Relaxed,
    ),
)

private data class CustomRaw(
    val idleTimeoutMinutes: Int,
    val freezeOnIdle: Boolean,
    val freezeOnScreenOff: Boolean,
    val freezeOnBatterySaver: Boolean,
    val exclusionStrictness: FreezeExclusionStrictness,
)

class FreezeProfileManager internal constructor(
    private val settings: FreezeSettings,
    private val contextProfileManager: ContextProfileManager,
) {
    private val customRaw: Flow<CustomRaw> = combine(
        settings.idleTimeoutMinutes,
        settings.freezeOnIdle,
        settings.freezeOnScreenOff,
        settings.freezeOnBatterySaver,
        settings.exclusionStrictness,
    ) { idleTimeoutMinutes, freezeOnIdle, freezeOnScreenOff, freezeOnBatterySaver, exclusionStrictness ->
        CustomRaw(idleTimeoutMinutes, freezeOnIdle, freezeOnScreenOff, freezeOnBatterySaver, exclusionStrictness)
    }

    /**
     * The base profile the user has set directly, or - if a context profile is currently active
     * and overrides the freeze profile - the context profile's override instead. This is
     * intentionally non-destructive: the user's own [FreezeSettings.profile] is never
     * overwritten by a context profile, only shadowed while that context profile is active.
     */
    private val effectiveProfile: Flow<FreezeProfile> = combine(
        settings.profile,
        contextProfileManager.activeProfile,
    ) { baseProfile, activeContextProfile ->
        activeContextProfile?.freezeProfileOverride ?: baseProfile
    }

    val resolvedSettings: Flow<ResolvedFreezeSettings> = combine(
        effectiveProfile,
        customRaw,
    ) { profile, custom ->
        FreezeProfilePresets[profile] ?: ResolvedFreezeSettings(
            idleTimeoutMinutes = custom.idleTimeoutMinutes,
            freezeOnIdle = custom.freezeOnIdle,
            freezeOnScreenOff = custom.freezeOnScreenOff,
            freezeOnBatterySaver = custom.freezeOnBatterySaver,
            exclusionStrictness = custom.exclusionStrictness,
        )
    }
}
