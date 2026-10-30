package com.telos.desktop.settings

import java.io.File
import java.util.Properties

class TelosDesktopSettings(private val vaultConfigDir: File) {
    var showDesktopIcons: Boolean = true
    var showHiddenFiles: Boolean = false
    var showLnkFiles: Boolean = false
    var showDesktopFiles: Boolean = true
    var showStandardFolders: Boolean = true
    var iconTheme: String = "hicolor"
    var windowButtonStyle: String = "KLASSY_ACCENT_FILLED"
    var buttonsOnLeft: Boolean = false
    var cursorPreset: String = "SWEET_DARK_NEON"
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

    private val file: File get() = File(vaultConfigDir, "desktop.properties")

    fun load() {
        if (!file.isFile) return
        val p = Properties()
        file.inputStream().use { p.load(it) }
        showDesktopIcons = p.getProperty("showDesktopIcons", "true").toBoolean()
        showHiddenFiles = p.getProperty("showHiddenFiles", "false").toBoolean()
        showLnkFiles = p.getProperty("showLnkFiles", "false").toBoolean()
        showDesktopFiles = p.getProperty("showDesktopFiles", "true").toBoolean()
        showStandardFolders = p.getProperty("showStandardFolders", "true").toBoolean()
        iconTheme = p.getProperty("iconTheme", iconTheme)
        windowButtonStyle = p.getProperty("windowButtonStyle", windowButtonStyle)
        buttonsOnLeft = p.getProperty("buttonsOnLeft", "false").toBoolean()
        cursorPreset = p.getProperty("cursorPreset", cursorPreset)
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
    }

    fun save() {
        vaultConfigDir.mkdirs()
        val p = Properties()
        p["showDesktopIcons"] = showDesktopIcons.toString()
        p["showHiddenFiles"] = showHiddenFiles.toString()
        p["showLnkFiles"] = showLnkFiles.toString()
        p["showDesktopFiles"] = showDesktopFiles.toString()
        p["showStandardFolders"] = showStandardFolders.toString()
        p["iconTheme"] = iconTheme
        p["windowButtonStyle"] = windowButtonStyle
        p["buttonsOnLeft"] = buttonsOnLeft.toString()
        p["cursorPreset"] = cursorPreset
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
        file.outputStream().use { p.store(it, "Telos Linux Desktop") }
    }
}
