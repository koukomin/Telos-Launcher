package de.mm20.launcher2.store.installer

import java.io.File

sealed interface InstallResult {
    data object Success : InstallResult
    /** The install was handed off to the system installer UI; the outcome isn't known yet. */
    data object Pending : InstallResult
    data class Failed(val reason: String, val cause: Throwable? = null) : InstallResult
}

/**
 * Installs an already-downloaded APK file, picking the least intrusive backend that's actually
 * usable on this device: a Shizuku-authorized silent install, then a rooted silent install, then
 * falling back to the standard [android.content.pm.PackageInstaller] session API, which prompts
 * the user. The concrete orchestration (`TelosPackageInstaller`) lives in `:data:store`, since it
 * depends on Android's `PackageInstaller`/`PackageManager` APIs.
 */
interface AppInstaller {
    /**
     * True if at least one backend (Shizuku, root, or the standard session API, which is always
     * available) can install right now. In practice this is almost always true; it exists mainly
     * so callers can short-circuit before downloading an APK that couldn't be installed anyway.
     */
    suspend fun isAvailable(): Boolean

    /**
     * True when updates can be installed without anyone tapping through the system installer:
     * Shizuku or root is available and allowed. Background installs only happen then.
     */
    suspend fun canInstallSilently(): Boolean

    /** Installs [apk]. Suspends until the install finishes or is handed off to system UI. */
    suspend fun install(apk: File, packageName: String): InstallResult
}
