package de.mm20.launcher2.data.store.installer

import android.content.Context
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.store.installer.AppInstaller
import de.mm20.launcher2.store.installer.InstallResult
import java.io.File
import java.io.InputStream

/**
 * [AppInstaller] implementation: tries silent install backends in order (Shizuku, then root),
 * and only falls back to the user-prompted standard [android.content.pm.PackageInstaller] session
 * API ([SessionApiInstallBackend]) when neither privileged backend is authorized. This mirrors
 * `FreezeManager`'s Shizuku-then-root backend selection in `:services:freeze`.
 */
class TelosPackageInstaller(context: Context) : AppInstaller {

    private val shizuku = ShizukuInstallBackend()
    private val root = RootInstallBackend()
    private val sessionApi = SessionApiInstallBackend(context)

    /** Ordered, privileged backends tried before falling back to [sessionApi]. */
    private val privilegedBackends: List<InstallBackend> = listOf(shizuku, root)

    override suspend fun isAvailable(): Boolean {
        // sessionApi.isAvailable() is always true - the standard API works unconditionally.
        return true
    }

    override suspend fun install(apk: File, packageName: String): InstallResult {
        for (backend in privilegedBackends) {
            if (!backend.isAvailable() || !backend.hasPermission()) continue
            return when (val result = backend.install(apk, packageName)) {
                is BackendInstallResult.Success -> InstallResult.Success
                is BackendInstallResult.Failed -> {
                    // A privileged backend being available but still failing (e.g. OEM-specific
                    // `pm install` quirk) isn't worth falling through for - it already bypassed
                    // the user, so silently handing off to a second, *prompting* install would
                    // be a confusing surprise. Report the failure instead.
                    CrashReporter.logException(Exception("${backend::class.simpleName}: ${result.reason}", result.cause))
                    InstallResult.Failed(result.reason, result.cause)
                }
            }
        }

        // No privileged backend authorized - fall back to the standard session API, which always
        // succeeds at *starting* the install (the actual accept/reject happens in system UI).
        return when (val result = sessionApi.install(apk, packageName)) {
            is BackendInstallResult.Success -> InstallResult.Pending
            is BackendInstallResult.Failed -> {
                CrashReporter.logException(Exception(result.reason, result.cause))
                InstallResult.Failed(result.reason, result.cause)
            }
        }
    }

    /**
     * Installs directly from [apkStream] via the standard session API, without first spooling to
     * a local file - useful when a caller already has a download stream in hand. This always goes
     * through [sessionApi] (never a privileged backend), since the shell-based Shizuku/root paths
     * need a file path on disk to pass to `pm install`.
     */
    suspend fun installFromStream(apkStream: InputStream, packageName: String, sizeBytes: Long = -1): InstallResult {
        return when (val result = sessionApi.install(apkStream, packageName, sizeBytes)) {
            is BackendInstallResult.Success -> InstallResult.Pending
            is BackendInstallResult.Failed -> {
                CrashReporter.logException(Exception(result.reason, result.cause))
                InstallResult.Failed(result.reason, result.cause)
            }
        }
    }
}
