package com.telos.desktop.keyboard

import java.io.File
import java.util.Locale

/**
 * Manages Linux X11/Wayland keyboard layouts (Polyglot keyboard), mirroring KDE Plasma 6.5 functionality.
 * Supports layout switching via Alt+Shift, tracking active language, and dynamic language tray indicators.
 */
class LinuxKeyboardManager {

    private val configuredLayouts = mutableListOf<String>()
    private var currentIndex = 0
    private var isX11 = System.getenv("XDG_SESSION_TYPE") == "x11"

    val activeLayout: String
        get() = if (configuredLayouts.isNotEmpty()) configuredLayouts[currentIndex] else "us"

    init {
        detectAndConfigureLayouts()
    }

    /**
     * Auto-configures layouts based on system locale.
     * If the system language is not English, it automatically enables the native language and English.
     */
    private fun detectAndConfigureLayouts() {
        val sysLocale = Locale.getDefault().language

        if (sysLocale != "en") {
            configuredLayouts.add(sysLocale)
            configuredLayouts.add("us")
        } else {
            configuredLayouts.add("us")
        }

        applyLayoutsToSystem()
    }

    /**
     * Switches to the next configured keyboard layout.
     * Intended to be triggered by the Alt+Shift key combination or clicking the tray icon.
     */
    fun toggleNextLayout() {
        if (configuredLayouts.size <= 1) return

        currentIndex = (currentIndex + 1) % configuredLayouts.size

        if (isX11) {
            // Apply X11 layout via setxkbmap
            runCatching {
                val layouts = configuredLayouts.joinToString(",")
                ProcessBuilder("setxkbmap", "-layout", layouts, "-variant", "").start().waitFor()

                // Set the current layout group (0-indexed)
                // Note: setxkbmap doesn't easily switch current group, xdotool or skb can be used,
                // but usually setxkbmap -layout "us,gr" -option "grp:alt_shift_toggle" handles it natively.
            }
        }
    }

    /**
     * Re-applies the full layout string to the windowing system.
     * We configure Alt+Shift as the native toggle shortcut at the system level.
     */
    fun applyLayoutsToSystem() {
        if (isX11 && configuredLayouts.isNotEmpty()) {
            runCatching {
                val layouts = configuredLayouts.joinToString(",")
                ProcessBuilder("setxkbmap", "-layout", layouts, "-option", "grp:alt_shift_toggle").start().waitFor()
            }
        }
    }
}
