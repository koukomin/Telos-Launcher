package com.telos.desktop.settings

import com.telos.desktop.theme.AppearanceKeys
import com.telos.desktop.theme.DesktopThemeChrome
import com.telos.desktop.theme.PanicMuteController
import com.telos.desktop.theme.PanelIconContrast
import com.telos.desktop.theme.PanelSurfaceMode
import com.telos.desktop.theme.ProfileAppearanceOverlay
import com.telos.desktop.theme.ThemeAccent
import com.telos.desktop.theme.ThemeDensity
import com.telos.desktop.theme.ThemeIntensity
import com.telos.desktop.theme.WorkspaceTintStrips
import com.telos.desktop.vault.DesktopProfiles
import java.io.File
import java.util.Properties

class TelosDesktopSettings(private val vaultConfigDir: File) {
    val configDir: File get() = vaultConfigDir
    var showDesktopIcons: Boolean = true
    var showHiddenFiles: Boolean = false
    var showLnkFiles: Boolean = false
    var showDesktopFiles: Boolean = true
    var showStandardFolders: Boolean = true
    var iconTheme: String = "hicolor"
    var windowButtonStyle: String = "KLASSY_ACCENT_FILLED"
    var buttonsOnLeft: Boolean = false
    var cursorPreset: String = "SWEET_DARK_NEON"
    /** Freedesktop / Plasma XCursor theme name; blank = Compose preset or follow host. */
    var cursorTheme: String = ""
    /** KDE-style cursor size: 16 / 24 / 32 / 48. */
    var cursorSize: Int = 24
    var preferredOutput: String? = null
    var preferredResolution: String? = null
    var preferredRefresh: Double? = null
    var preferredScale: Double? = 1.0
    var initializeVaultIfMissing: Boolean = true
    // === TELOS_PENDING_REVIEW_START: telos_de_final_polish ===
    var startFullscreen: Boolean = false
    var followSystemTheme: Boolean = true
    // === TELOS_PENDING_REVIEW_END: telos_de_final_polish ===
    // === TELOS_PENDING_REVIEW_START: telos_desktop_kwin_parity_upgrade ===
    var panelArgb: Long = 0xF2241E36
    var panelAlpha: Float = 0.94f
    var panelBlur: Float = 12f
    /**
     * Helix panel root appearance: transparent | blurred | solid.
     * Stored as `panel.surfaceMode` (default blurred to match prior alpha+blur prefs).
     */
    var panelSurfaceMode: String = PanelSurfaceMode.Blurred.propertyValue
    var panelIconDp: Float = 22f
    var panelHeightDp: Float = 36f
    // === TELOS_PENDING_REVIEW_END: telos_desktop_kwin_parity_upgrade ===
    val outputPrefs = LinkedHashMap<String, String>()
    var idleSeconds: Int = 300
    var secureClipboard: Boolean = true
    var clipboardClearSeconds: Int = 45
    /** Opt-in vault-local file name index (disabled by default; privacy-first). */
    var fileNameIndexEnabled: Boolean = false
    /**
     * Helix windowing preference: `stacking` (labwc) or `tiling` (niri).
     * Stored as `desktop.windowMode`; applied on next telos-session when
     * `TELOS_COMPOSITOR` is unset.
     */
    var windowMode: String = "stacking"
    /** Warm display temperature (Night Light); applied via host gammastep/redshift/wlsunset when present. */
    var nightLightEnabled: Boolean = false
    var nightLightTemperatureK: Int = 4500
    /**
     * Subtle neon accent strips under Helix panel workspace digits 1–4.
     * Stored as `theme.workspaceTints` (default on).
     */
    var workspaceTints: Boolean = true
    /**
     * Panel / tray icon contrast: `auto` | `light` | `dark` ([PanelIconContrast.PREF_KEY]).
     * Auto samples the wallpaper strip behind the top panel (local only).
     */
    var panelIconContrast: String = PanelIconContrast.Mode.Auto.propertyValue
    /**
     * Persisted panic-mute preference (`theme.panicMute`). Session mute may also
     * be toggled without writing disk via [PanicMuteController].
     */
    var panicMute: Boolean = false
    /**
     * Derived Quiet Ops flag for workspace tint / Theme Studio consumers.
     */
    var themeQuietOps: Boolean = false
    /** Alias of [panicMute] for Theme Studio / panel consumers. */
    var themePanicMute: Boolean
        get() = panicMute
        set(value) {
            panicMute = value
        }
    /** Theme Studio: Quiet Ops (default) / Vault Neon / Paper Ops. Stored as `theme.intensity`. */
    var themeIntensity: String = ThemeIntensity.QuietOps.propertyValue
    /**
     * Theme Studio accent hex or `wallpaper` (local tonal seed).
     * [wallpaperTonal] is an alternate boolean switch for the same wallpaper path.
     */
    var themeAccent: String = ThemeAccent.NeonMagenta.propertyValue
    /** Theme Studio density: compact / comfort / touch. Stored as `theme.density`. */
    var themeDensity: String = ThemeDensity.Comfort.propertyValue
    /** Overview / snap pill scale (also set by density presets). */
    var themeSnapPillScale: Float = ThemeDensity.Comfort.snapPillScale
    /** When true, chrome uses a locally sampled wallpaper tonal seed (no network). */
    var wallpaperTonal: Boolean = false
    /**
     * Optional vault/portable wallpaper path for Theme Studio tonal seed, panel
     * contrast sampling, and unlock theatre reveal. Written as both
     * `theme.wallpaperPath` and `wallpaper.path`. Blank = search Pictures /
     * `.config/telos` defaults. Never shown on the locked pre-boot screen
     * (locked-boot always paints Quiet Ops).
     */
    var wallpaperPath: String = ""

    private val file: File get() = File(vaultConfigDir, "desktop.properties")

    fun load() {
        val hasGlobal = file.isFile
        val hasProfiles = DesktopProfiles.listProfileFolders().isNotEmpty()
        if (!hasGlobal && !hasProfiles) return
        val p = ProfileAppearanceOverlay.loadMerged(vaultConfigDir)
        applyLoadedProperties(p)
        PanicMuteController.syncFromSettings(this)
        syncThemeChrome()
    }

    /**
     * Reload appearance from the active profile overlay, then global vault props.
     * Call after a (lock-gated) profile switch. Does not touch nftables.
     */
    fun reloadAppearanceFromProfile() {
        val merged = ProfileAppearanceOverlay.loadMerged(vaultConfigDir)
        ProfileAppearanceOverlay.applyAppearanceFields(
            props = merged,
            setIntensity = { themeIntensity = it },
            setAccent = { themeAccent = it },
            setIconTheme = { iconTheme = it },
            setCursorTheme = { cursorTheme = it },
        )
        themeQuietOps = ThemeIntensity.parse(themeIntensity) == ThemeIntensity.QuietOps ||
            WorkspaceTintStrips.parseQuietOps(merged)
        // panicMute stays global (not profile-scoped).
        panicMute = merged.getProperty(AppearanceKeys.PANIC_MUTE, "false").toBoolean() ||
            WorkspaceTintStrips.parsePanicMute(merged)
        wallpaperTonal = merged.getProperty("theme.wallpaperTonal", "false").toBoolean() ||
            themeAccent.equals("wallpaper", ignoreCase = true)
        wallpaperPath = merged.getProperty("theme.wallpaperPath") ?: wallpaperPath
        themeDensity = ThemeDensity.parse(merged.getProperty("theme.density") ?: themeDensity).propertyValue
        themeSnapPillScale = merged.getProperty("theme.snapPillScale")?.toFloatOrNull()
            ?.coerceIn(0.7f, 1.4f)
            ?: ThemeDensity.parse(themeDensity).snapPillScale
        panelSurfaceMode = PanelSurfaceMode.parse(merged).propertyValue
        panelIconContrast = PanelIconContrast.parseMode(merged).propertyValue
        PanicMuteController.syncFromSettings(this)
        syncThemeChrome()
    }

    private fun applyLoadedProperties(p: Properties) {
        showDesktopIcons = p.getProperty("showDesktopIcons", "true").toBoolean()
        showHiddenFiles = (p.getProperty("hiddenItems.showHiddenFiles")
            ?: p.getProperty("showHiddenFiles", "false")).toBoolean()
        showLnkFiles = p.getProperty("showLnkFiles", "false").toBoolean()
        showDesktopFiles = p.getProperty("showDesktopFiles", "true").toBoolean()
        showStandardFolders = p.getProperty("showStandardFolders", "true").toBoolean()
        iconTheme = p.getProperty("icons.theme") ?: p.getProperty("iconTheme", iconTheme)
        windowButtonStyle = p.getProperty("windowButtonStyle", windowButtonStyle)
        buttonsOnLeft = p.getProperty("buttonsOnLeft", "false").toBoolean()
        cursorPreset = p.getProperty("cursorPreset", cursorPreset)
        cursorTheme = p.getProperty("cursor.theme")
            ?: p.getProperty("cursorTheme", cursorTheme)
        cursorSize = (p.getProperty("cursor.size") ?: p.getProperty("cursorSize"))
            ?.toIntOrNull()
            ?.let { com.telos.desktop.cursor.KdeCursorConfig.nearestKdeSize(it) }
            ?: cursorSize
        preferredOutput = p.getProperty("preferredOutput")
        preferredResolution = p.getProperty("preferredResolution")
        preferredRefresh = p.getProperty("preferredRefresh")?.toDoubleOrNull()
        preferredScale = p.getProperty("preferredScale")?.toDoubleOrNull() ?: 1.0
        initializeVaultIfMissing = p.getProperty("initializeVaultIfMissing", "true").toBoolean()
        // === TELOS_PENDING_REVIEW_START: telos_de_final_polish ===
        startFullscreen = p.getProperty("startFullscreen", "false").toBoolean()
        followSystemTheme = p.getProperty("followSystemTheme", "true").toBoolean()
        // === TELOS_PENDING_REVIEW_END: telos_de_final_polish ===
        // === TELOS_PENDING_REVIEW_START: telos_desktop_kwin_parity_upgrade ===
        panelArgb = p.getProperty("panelArgb")?.toLongOrNull() ?: panelArgb
        panelAlpha = p.getProperty("panelAlpha")?.toFloatOrNull() ?: panelAlpha
        panelBlur = p.getProperty("panelBlur")?.toFloatOrNull() ?: panelBlur
        panelSurfaceMode = PanelSurfaceMode.parse(p).propertyValue
        panelIconDp = p.getProperty("panelIconDp")?.toFloatOrNull() ?: panelIconDp
        panelHeightDp = p.getProperty("panelHeightDp")?.toFloatOrNull() ?: panelHeightDp
        // === TELOS_PENDING_REVIEW_END: telos_desktop_kwin_parity_upgrade ===
        idleSeconds = p.getProperty("idleSeconds")?.toIntOrNull()?.coerceIn(30, 3600) ?: 300
        secureClipboard = p.getProperty("opsec.secureClipboard", "true").toBoolean()
        clipboardClearSeconds = p.getProperty("opsec.clipboardClearSeconds")?.toIntOrNull()?.coerceIn(0, 600) ?: 45
        fileNameIndexEnabled = p.getProperty("privacy.fileNameIndex", "false").toBoolean()
        windowMode = com.telos.desktop.wm.WindowMode.parse(
            p.getProperty("desktop.windowMode") ?: p.getProperty("windowMode"),
        ).propertyValue
        nightLightEnabled = p.getProperty("nightLight.enabled", "false").toBoolean()
        nightLightTemperatureK = p.getProperty("nightLight.temperatureK")?.toIntOrNull()
            ?.coerceIn(1000, 6500) ?: 4500
        workspaceTints = WorkspaceTintStrips.parseWorkspaceTints(p, default = true)
        panelIconContrast = PanelIconContrast.parseMode(p).propertyValue
        themeIntensity = ThemeIntensity.parse(p.getProperty(AppearanceKeys.INTENSITY)).propertyValue
        val rawAccent = p.getProperty(AppearanceKeys.ACCENT) ?: themeAccent
        themeAccent = if (rawAccent.equals("wallpaper", ignoreCase = true)) {
            "wallpaper"
        } else {
            ThemeAccent.canonicalizeForStorage(rawAccent)
        }
        themeDensity = ThemeDensity.parse(p.getProperty("theme.density")).propertyValue
        themeSnapPillScale = p.getProperty("theme.snapPillScale")?.toFloatOrNull()
            ?.coerceIn(0.7f, 1.4f)
            ?: ThemeDensity.parse(themeDensity).snapPillScale
        panicMute = p.getProperty(AppearanceKeys.PANIC_MUTE, "false").toBoolean() ||
            WorkspaceTintStrips.parsePanicMute(p)
        wallpaperTonal = p.getProperty("theme.wallpaperTonal", "false").toBoolean() ||
            themeAccent.equals("wallpaper", ignoreCase = true)
        wallpaperPath = p.getProperty("theme.wallpaperPath")
            ?: p.getProperty("wallpaper.path")
            ?: p.getProperty("wallpaperPath", wallpaperPath)
        themeQuietOps = WorkspaceTintStrips.parseQuietOps(p) ||
            ThemeIntensity.parse(themeIntensity) == ThemeIntensity.QuietOps
        outputPrefs.clear()
        p.stringPropertyNames().forEach { key ->
            if (key.startsWith("output.")) {
                outputPrefs[key.removePrefix("output.")] = p.getProperty(key).orEmpty()
            }
        }
    }

    fun save() {
        vaultConfigDir.mkdirs()
        val p = Properties()
        // Preserve unknown / Theme Studio keys (forward-compat).
        if (file.isFile) {
            runCatching { file.inputStream().use { p.load(it) } }
        }
        p["showDesktopIcons"] = showDesktopIcons.toString()
        p["showHiddenFiles"] = showHiddenFiles.toString()
        p["hiddenItems.showHiddenFiles"] = showHiddenFiles.toString()
        p["showLnkFiles"] = showLnkFiles.toString()
        p["showDesktopFiles"] = showDesktopFiles.toString()
        p["showStandardFolders"] = showStandardFolders.toString()
        p["iconTheme"] = iconTheme
        p["icons.theme"] = iconTheme
        p["windowButtonStyle"] = windowButtonStyle
        p["buttonsOnLeft"] = buttonsOnLeft.toString()
        p["cursorPreset"] = cursorPreset
        p["cursor.theme"] = cursorTheme
        p["cursorTheme"] = cursorTheme
        p["cursor.size"] = cursorSize.toString()
        p["cursorSize"] = cursorSize.toString()
        preferredOutput?.let { p["preferredOutput"] = it }
        preferredResolution?.let { p["preferredResolution"] = it }
        preferredRefresh?.let { p["preferredRefresh"] = it.toString() }
        preferredScale?.let { p["preferredScale"] = it.toString() }
        p["initializeVaultIfMissing"] = initializeVaultIfMissing.toString()
        // === TELOS_PENDING_REVIEW_START: telos_de_final_polish ===
        p["startFullscreen"] = startFullscreen.toString()
        p["followSystemTheme"] = followSystemTheme.toString()
        // === TELOS_PENDING_REVIEW_END: telos_de_final_polish ===
        // === TELOS_PENDING_REVIEW_START: telos_desktop_kwin_parity_upgrade ===
        p["panelArgb"] = panelArgb.toString()
        p["panelAlpha"] = panelAlpha.toString()
        p["panelBlur"] = panelBlur.toString()
        p[PanelSurfaceMode.PREF_KEY] = PanelSurfaceMode.parse(panelSurfaceMode).propertyValue
        p["panelIconDp"] = panelIconDp.toString()
        p["panelHeightDp"] = panelHeightDp.toString()
        // === TELOS_PENDING_REVIEW_END: telos_desktop_kwin_parity_upgrade ===
        p["idleSeconds"] = idleSeconds.toString()
        p["opsec.secureClipboard"] = secureClipboard.toString()
        p["opsec.clipboardClearSeconds"] = clipboardClearSeconds.toString()
        p["privacy.fileNameIndex"] = fileNameIndexEnabled.toString()
        val mode = com.telos.desktop.wm.WindowMode.parse(windowMode).propertyValue
        p["desktop.windowMode"] = mode
        p["windowMode"] = mode
        p["nightLight.enabled"] = nightLightEnabled.toString()
        p["nightLight.temperatureK"] = nightLightTemperatureK.toString()
        p[WorkspaceTintStrips.PREF_KEY] = workspaceTints.toString()
        p[PanelIconContrast.PREF_KEY] = PanelIconContrast.parseMode(panelIconContrast).propertyValue
        p[AppearanceKeys.INTENSITY] = ThemeIntensity.parse(themeIntensity).propertyValue
        p[AppearanceKeys.ACCENT] = ThemeAccent.canonicalizeForStorage(themeAccent)
        p[AppearanceKeys.PANIC_MUTE] = panicMute.toString()
        p["theme.density"] = ThemeDensity.parse(themeDensity).propertyValue
        p["theme.snapPillScale"] = themeSnapPillScale.coerceIn(0.7f, 1.4f).toString()
        p["theme.wallpaperTonal"] = wallpaperTonal.toString()
        val intensity = com.telos.desktop.theme.ThemeIntensity.parse(themeIntensity).propertyValue
        p["theme.intensity"] = intensity
        p["themeIntensity"] = intensity
        if (wallpaperPath.isNotBlank()) {
            p["theme.wallpaperPath"] = wallpaperPath
            p["wallpaper.path"] = wallpaperPath
            p["wallpaperPath"] = wallpaperPath
        } else {
            p.remove("theme.wallpaperPath")
            p.remove("wallpaper.path")
            p.remove("wallpaperPath")
        }
        outputPrefs.forEach { (name, spec) -> p["output.$name"] = spec }
        file.outputStream().use { p.store(it, "Telos Linux Desktop") }
        // When a VOS-1 profile is active, also mirror appearance keys into the
        // profile overlay so Work vs Ghost keep distinct looks (portable-safe).
        persistActiveProfileAppearanceOverlay()
        PanicMuteController.notifyAppearanceChanged()
        syncThemeChrome()
        DesktopChromeBump()
    }

    /**
     * Persist only `theme.panicMute` into vault global props (merge).
     * Used by [PanicMuteController] so mute toggles do not rewrite every key.
     */
    fun savePanicMuteOnly() {
        vaultConfigDir.mkdirs()
        val p = Properties()
        if (file.isFile) {
            file.inputStream().use { p.load(it) }
        }
        p.setProperty(AppearanceKeys.PANIC_MUTE, panicMute.toString())
        file.outputStream().use { p.store(it, "Telos Linux Desktop") }
        DesktopChromeBump()
    }

    /**
     * Write appearance overlay keys for the active profile when profile folders exist.
     * Paths use [DesktopProfiles.profilesRoot] (no host HOME in Portable Data).
     */
    fun persistActiveProfileAppearanceOverlay(): Boolean {
        val active = DesktopProfiles.activeProfile(vaultConfigDir) ?: return false
        if (DesktopProfiles.listProfileFolders().isEmpty()) return false
        return ProfileAppearanceOverlay.writeOverlayKeys(
            profileName = active,
            keys = mapOf(
                AppearanceKeys.INTENSITY to ThemeIntensity.parse(themeIntensity).propertyValue,
                AppearanceKeys.ACCENT to ThemeAccent.canonicalizeForStorage(themeAccent),
                AppearanceKeys.ICONS_THEME to iconTheme,
                AppearanceKeys.CURSOR_THEME to cursorTheme,
            ),
        )
    }

    /** Apply a named density preset to panel metrics + snap/overview pill scale. */
    fun applyThemeDensity(density: ThemeDensity) {
        themeDensity = density.propertyValue
        panelHeightDp = density.panelHeightDp
        panelIconDp = density.panelIconDp
        themeSnapPillScale = density.snapPillScale
        DesktopThemeChrome.applyDensity(density)
    }

    fun applyThemeIntensity(intensity: ThemeIntensity) {
        themeIntensity = intensity.propertyValue
        themeQuietOps = intensity == ThemeIntensity.QuietOps
        DesktopThemeChrome.intensity = intensity
    }

    fun applyThemeAccent(accent: ThemeAccent) {
        themeAccent = accent.propertyValue
        wallpaperTonal = accent == ThemeAccent.FromWallpaper
        DesktopThemeChrome.accent = accent
    }

    fun syncThemeChrome() {
        DesktopThemeChrome.applyFromSettings(
            intensityRaw = themeIntensity,
            accentRaw = themeAccent,
            densityRaw = themeDensity,
            snapScale = themeSnapPillScale,
        )
    }

    fun rememberOutput(name: String, resolution: String, refresh: Double, scale: Double) {
        outputPrefs[name] = "$resolution@$refresh@$scale"
        preferredOutput = name
        preferredResolution = resolution
        preferredRefresh = refresh
        preferredScale = scale
    }

    private fun DesktopChromeBump() {
        runCatching { com.telos.desktop.ui.DesktopChrome.bumpAccentEpoch() }
    }
}
