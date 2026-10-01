package de.mm20.launcher2.data.store.installer

import java.io.File

/**
 * One way to get an APK onto the device, ordered by how intrusive it is to the user. Mirrors
 * `PrivilegedAccessProvider` in `:services:freeze` - same idea (Shizuku/root availability and
 * permission checks), applied to installs instead of suspend/enable toggles.
 */
internal interface InstallBackend {
    /** Whether the underlying privilege source (Shizuku service, root) is reachable at all. */
    suspend fun isAvailable(): Boolean

    /** Whether we currently hold permission to use it, without prompting the user. */
    suspend fun hasPermission(): Boolean

    /** Prompts the user if necessary. Suspends until the user responds. Returns the granted state. */
    suspend fun requestPermission(): Boolean

    /** Installs [apk] silently (no system install confirmation UI). */
    suspend fun install(apk: File, packageName: String): BackendInstallResult
}

internal sealed interface BackendInstallResult {
    data object Success : BackendInstallResult
    data class Failed(val reason: String, val cause: Throwable? = null) : BackendInstallResult
}
