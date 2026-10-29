package com.telos.desktop.platform

/**
 * Platform capability contract used by Telos settings UIs.
 *
 * Android (`:app:app`) never depends on this module. A parallel type lives in
 * `app/ui` so mobile settings can hide Linux-only options without pulling
 * Compose Desktop into the APK.
 */
enum class TelosRuntimePlatform {
    AndroidMobile,
    LinuxDesktop,
}

data class SettingsVisibilityFilter(
    val platform: TelosRuntimePlatform,
    val desktopModeOnAndroid: Boolean = false,
) {
    val showAndroidMobileSettings: Boolean
        get() = platform == TelosRuntimePlatform.AndroidMobile

    val showLinuxDesktopSettings: Boolean
        get() = platform == TelosRuntimePlatform.LinuxDesktop ||
            (platform == TelosRuntimePlatform.AndroidMobile && desktopModeOnAndroid)

    val showShizukuDhizukuFreeze: Boolean get() = showAndroidMobileSettings
    val showWorkProfileIsland: Boolean get() = showAndroidMobileSettings
    val showT9DialerSmsPermissions: Boolean get() = showAndroidMobileSettings
    val showAndroidWallpaperServices: Boolean get() = showAndroidMobileSettings

    val showDisplayResolution: Boolean get() = platform == TelosRuntimePlatform.LinuxDesktop
    val showXdgUserDirs: Boolean get() = platform == TelosRuntimePlatform.LinuxDesktop
    val showDesktopSurfaceFilters: Boolean get() = platform == TelosRuntimePlatform.LinuxDesktop
    val showLinuxIconThemeSelector: Boolean get() = platform == TelosRuntimePlatform.LinuxDesktop
    val showLuksVault: Boolean get() = platform == TelosRuntimePlatform.LinuxDesktop
    val showKlassyWindowManager: Boolean get() = platform == TelosRuntimePlatform.LinuxDesktop
    val showSweetCursor: Boolean get() = platform == TelosRuntimePlatform.LinuxDesktop

    companion object {
        fun linuxDesktop(): SettingsVisibilityFilter =
            SettingsVisibilityFilter(TelosRuntimePlatform.LinuxDesktop)
    }
}

object PlatformEnvironment {
    val current: TelosRuntimePlatform = TelosRuntimePlatform.LinuxDesktop
    val settings: SettingsVisibilityFilter = SettingsVisibilityFilter.linuxDesktop()
}
