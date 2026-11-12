package com.telos.desktop.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.telos.desktop.settings.TelosDesktopSettings

/**
 * Session "panic mute": force Quiet Ops chrome, hide neon tech borders / gauges.
 * Preference-only visual — never touches nftables, kill-switch, or network policy.
 *
 * Session flag lives in memory; optional persist via `theme.panicMute` in vault
 * `desktop.properties` (not the profile overlay).
 */
object PanicMuteController {
    /** In-session mute independent of disk until [syncFromSettings] / [setMuted]. */
    var sessionMuted by mutableStateOf(false)
        private set

    /** Bumped when appearance or mute changes so Compose theme can refresh. */
    var appearanceGeneration by mutableStateOf(0)
        private set

    fun notifyAppearanceChanged() {
        appearanceGeneration++
    }

    fun isMuted(settings: TelosDesktopSettings? = null): Boolean =
        sessionMuted || (settings?.panicMute == true)

    fun syncFromSettings(settings: TelosDesktopSettings) {
        sessionMuted = settings.panicMute
        notifyAppearanceChanged()
    }

    /**
     * Toggle mute. When [persist] is true, writes `theme.panicMute` via [settings]
     * (vault global props only — does not alter nftables / kill-switch).
     */
    fun setMuted(
        muted: Boolean,
        settings: TelosDesktopSettings? = null,
        persist: Boolean = true,
    ) {
        sessionMuted = muted
        if (persist && settings != null) {
            settings.panicMute = muted
            settings.savePanicMuteOnly()
        }
        notifyAppearanceChanged()
    }

    fun toggle(
        settings: TelosDesktopSettings? = null,
        persist: Boolean = true,
    ): Boolean {
        val next = !isMuted(settings)
        setMuted(next, settings, persist)
        return next
    }

    /** Effective intensity: panic mute always forces Quiet Ops. */
    fun effectiveIntensity(settings: TelosDesktopSettings): ThemeIntensity {
        if (isMuted(settings)) return ThemeIntensity.Quiet
        return ThemeIntensity.parse(settings.themeIntensity)
    }

    /** Panic mute hides neon tech borders entirely (screenshare-safe). */
    fun showTechBorders(settings: TelosDesktopSettings? = null): Boolean = !isMuted(settings)

    /** HUD gauges only in Vault Neon and never while muted. */
    fun showGauges(settings: TelosDesktopSettings? = null): Boolean {
        if (isMuted(settings)) return false
        if (settings == null) return false
        return effectiveIntensity(settings) == ThemeIntensity.Neon
    }
}
