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
        file.outputStream().use { p.store(it, "Telos Linux Desktop") }
    }
}
