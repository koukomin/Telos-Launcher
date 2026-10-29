package de.mm20.launcher2.ui.settings.platform

/**
 * Android-side platform capability contract.
 *
 * Linux Desktop settings live exclusively in `:desktop-linux` and are never
 * compiled into `:app:app`. This filter hides Linux-only entries if they are
 * ever referenced from the Android settings graph, and keeps Android-only
 * options (Shizuku, work profile, T9/SMS, wallpaper services) visible.
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

    /** Linux DE controls stay off Android, including Android Desktop Mode. */
    val showLinuxDesktopSettings: Boolean
        get() = platform == TelosRuntimePlatform.LinuxDesktop

    val showShizukuDhizukuFreeze: Boolean get() = showAndroidMobileSettings
    val showWorkProfileIsland: Boolean get() = showAndroidMobileSettings
    val showT9DialerSmsPermissions: Boolean get() = showAndroidMobileSettings
    val showAndroidWallpaperServices: Boolean get() = showAndroidMobileSettings

    val showDisplayResolution: Boolean get() = showLinuxDesktopSettings
    val showXdgUserDirs: Boolean get() = showLinuxDesktopSettings
    val showDesktopSurfaceFilters: Boolean get() = showLinuxDesktopSettings
    val showLinuxIconThemeSelector: Boolean get() = showLinuxDesktopSettings
    val showLuksVault: Boolean get() = showLinuxDesktopSettings
    val showKlassyWindowManager: Boolean get() = showLinuxDesktopSettings
    val showSweetCursor: Boolean get() = showLinuxDesktopSettings

    companion object {
        fun androidMobile(desktopModeOnAndroid: Boolean = false): SettingsVisibilityFilter =
            SettingsVisibilityFilter(TelosRuntimePlatform.AndroidMobile, desktopModeOnAndroid)
    }
}

object PlatformEnvironment {
    val current: TelosRuntimePlatform = TelosRuntimePlatform.AndroidMobile
    fun settings(desktopModeOnAndroid: Boolean = false): SettingsVisibilityFilter =
        SettingsVisibilityFilter.androidMobile(desktopModeOnAndroid)
}
