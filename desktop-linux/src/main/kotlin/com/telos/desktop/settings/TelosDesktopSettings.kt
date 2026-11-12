package com.telos.desktop.settings

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
        outputPrefs.forEach { (name, spec) -> p["output.$name"] = spec }
        file.outputStream().use { p.store(it, "Telos Linux Desktop") }
    }

    fun rememberOutput(name: String, resolution: String, refresh: Double, scale: Double) {
        outputPrefs[name] = "$resolution@$refresh@$scale"
        preferredOutput = name
        preferredResolution = resolution
        preferredRefresh = refresh
        preferredScale = scale
    }
}
