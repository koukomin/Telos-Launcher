package de.mm20.launcher2.store.installer

import java.io.File

sealed interface InstallResult {
    data object Success : InstallResult
    /** The install was handed off to the system installer UI; the outcome isn't known yet. */
    data object Pending : InstallResult
    data class Failed(val reason: String, val cause: Throwable? = null) : InstallResult
}

/** An explicitly selectable installation path. External handlers always require user interaction. */
data class InstallerChoice(val id: String, val label: String)

interface AppInstaller {
    fun externalInstallers(): List<InstallerChoice> = emptyList()

    /**
     * True if at least one backend (Shizuku, root, or the standard session API, which is always
     * available) can install right now. In practice this is almost always true; it exists mainly
     * so callers can short-circuit before downloading an APK that couldn't be installed anyway.
     */
    suspend fun isAvailable(): Boolean

    /**
     * True only when the selected installer is currently authorized for silent installation.
     * External handlers never qualify; Android sessions require device-owner privileges.
     */
    suspend fun canInstallSilently(): Boolean

    /** Installs [apk]. Suspends until the install finishes or is handed off to system UI. */
    suspend fun install(apk: File, packageName: String): InstallResult
}
