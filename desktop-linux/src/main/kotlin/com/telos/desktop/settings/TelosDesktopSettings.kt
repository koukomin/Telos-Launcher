package com.telos.desktop.settings

import com.telos.desktop.theme.DesktopThemeChrome
import com.telos.desktop.theme.ThemeAccent
import com.telos.desktop.theme.ThemeDensity
import com.telos.desktop.theme.ThemeIntensity
import com.telos.desktop.theme.WorkspaceTintStrips
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
     * Defensive reads for Theme Studio / OpSec visual mute keys. Not authored here —
     * only consumed so strips stay quiet or hidden when those prefs appear.
     */
    var themeQuietOps: Boolean = false
    var themePanicMute: Boolean = false
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
    /** Optional vault/portable wallpaper path; blank = search Pictures / config defaults. */
    var wallpaperPath: String = ""

    private val file: File get() = File(vaultConfigDir, "desktop.properties")

    fun load() {
        if (!file.isFile) return
        val p = Properties()
        file.inputStream().use { p.load(it) }
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
        themePanicMute = WorkspaceTintStrips.parsePanicMute(p)
        themeIntensity = ThemeIntensity.parse(p.getProperty("theme.intensity")).propertyValue
        themeAccent = ThemeAccent.parse(p.getProperty("theme.accent")).propertyValue
        themeDensity = ThemeDensity.parse(p.getProperty("theme.density")).propertyValue
        themeSnapPillScale = p.getProperty("theme.snapPillScale")?.toFloatOrNull()
            ?.coerceIn(0.7f, 1.4f)
            ?: ThemeDensity.parse(themeDensity).snapPillScale
        wallpaperTonal = p.getProperty("theme.wallpaperTonal", "false").toBoolean() ||
            themeAccent.equals("wallpaper", ignoreCase = true)
        wallpaperPath = p.getProperty("theme.wallpaperPath") ?: wallpaperPath
        themeQuietOps = WorkspaceTintStrips.parseQuietOps(p) ||
            ThemeIntensity.parse(themeIntensity) == ThemeIntensity.QuietOps
        outputPrefs.clear()
        p.stringPropertyNames().forEach { key ->
            if (key.startsWith("output.")) {
                outputPrefs[key.removePrefix("output.")] = p.getProperty(key).orEmpty()
            }
        }
        syncThemeChrome()
    }

    fun save() {
        vaultConfigDir.mkdirs()
        val p = Properties()
        // Preserve unknown / Theme Studio keys (panic mute, quiet ops, intensity, …).
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
        p["theme.intensity"] = ThemeIntensity.parse(themeIntensity).propertyValue
        p["theme.accent"] = ThemeAccent.parse(themeAccent).propertyValue
        p["theme.density"] = ThemeDensity.parse(themeDensity).propertyValue
        p["theme.snapPillScale"] = themeSnapPillScale.coerceIn(0.7f, 1.4f).toString()
        p["theme.wallpaperTonal"] = wallpaperTonal.toString()
        if (wallpaperPath.isNotBlank()) p["theme.wallpaperPath"] = wallpaperPath
        outputPrefs.forEach { (name, spec) -> p["output.$name"] = spec }
        file.outputStream().use { p.store(it, "Telos Linux Desktop") }
        syncThemeChrome()
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
}
