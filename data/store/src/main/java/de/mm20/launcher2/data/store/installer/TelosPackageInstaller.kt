package de.mm20.launcher2.data.store.installer

import android.content.Context
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.database.AppDatabase
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
class TelosPackageInstaller(
    private val context: Context,
    private val freezeManager: de.mm20.launcher2.freeze.FreezeManager,
    private val options: de.mm20.launcher2.store.options.StoreOptions,
) : AppInstaller {

    private val shizuku = ShizukuInstallBackend()
    private val root = RootInstallBackend()
    private val sessionApi = SessionApiInstallBackend(context)

    /** Ordered, privileged backends tried before falling back to [sessionApi]. */
    private val privilegedBackends: List<InstallBackend> = listOf(shizuku, root)

    override suspend fun isAvailable(): Boolean {
        // sessionApi.isAvailable() is always true - the standard API works unconditionally.
        return true
    }

    override suspend fun canInstallSilently(): Boolean =
        privilegedBackends.any { it.isAvailable() && it.hasPermission() } || isDeviceOwner()

    private fun isDeviceOwner() = context.getSystemService(android.app.admin.DevicePolicyManager::class.java)
        ?.isDeviceOwnerApp(context.packageName) == true

    private fun failed(cause: Throwable? = null, details: String? = cause?.message) = InstallResult.Failed(
        context.getString(de.mm20.launcher2.data.store.R.string.au_store_msg_install_failed) +
            details?.takeIf { it.isNotBlank() }?.take(8192)?.let { "\n$it" }.orEmpty(), cause)

    override suspend fun install(apk: File, packageName: String): InstallResult {
        if (freezeManager.hasPendingFrozenUpdate(packageName)) return InstallResult.Failed(context.getString(de.mm20.launcher2.data.store.R.string.hf_store_installing))
        val archive = context.packageManager.getPackageArchiveInfo(apk.path, 0)
        if (archive?.packageName != packageName) return failed(IllegalArgumentException("APK package does not match the selected app"))
        if (freezeManager.isFrozen(packageName) && !options.global.value.updateFrozenApps)
            return InstallResult.Failed(context.getString(de.mm20.launcher2.data.store.R.string.store_update_frozen))
        for (backend in privilegedBackends) {
            if (!backend.isAvailable() || !backend.hasPermission()) continue
            var result: BackendInstallResult = BackendInstallResult.Failed("Install did not start")
            var restored = true
            var lease: String? = null
            try {
                lease = freezeManager.beginFrozenUpdate(packageName,
                    if (backend === shizuku) "Shizuku" else "Root")
                result = backend.install(apk, packageName)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                result = BackendInstallResult.Failed(error.message ?: "Frozen update failed", error)
            } finally {
                restored = lease?.let { freezeManager.finishFrozenUpdate(packageName, it) } ?: true
            }
            if (!restored) return InstallResult.Failed(
                context.getString(de.mm20.launcher2.data.store.R.string.freeze_restore_all) + ": " +
                    context.getString(de.mm20.launcher2.data.store.R.string.pdft_error_title))
            return when (val installed = result) {
                is BackendInstallResult.Success -> {
                    // Shizuku/root installs finish synchronously (unlike the session API path,
                    // whose result arrives later via InstallResultReceiver) - persist the new
                    // installedVersionCode to Room right here so the UI unsticks immediately.
                    val versionCode = resolveInstalledVersionCode(context, packageName)
                    AppDatabase.getInstance(context).storeItemDao()
                        .updateInstalledVersion(packageName, versionCode)
                    InstallResult.Success
                }
                is BackendInstallResult.Failed -> {
                    // A privileged backend being available but still failing (e.g. OEM-specific
                    // `pm install` quirk) isn't worth falling through for - it already bypassed
                    // the user, so silently handing off to a second, *prompting* install would
                    // be a confusing surprise. Report the failure instead.
                    CrashReporter.logException(Exception("${backend::class.simpleName}: ${installed.reason}", installed.cause))
                    failed(installed.cause, installed.reason)
                }
            }
        }

        // No privileged backend authorized - fall back to the standard session API, which always
        // succeeds at *starting* the install (the actual accept/reject happens in system UI).
        var pending = false
        var lease: String? = null
        try {
            if (freezeManager.isFrozen(packageName)) {
                if (!isDeviceOwner()) return InstallResult.Failed(context.getString(de.mm20.launcher2.data.store.R.string.freeze_no_backend_available))
            }
            lease = freezeManager.beginFrozenUpdate(packageName, "DeviceOwner")
            return when (val result = sessionApi.install(apk, packageName, lease)) {
                is BackendInstallResult.Success -> { pending = true; InstallResult.Pending }
                is BackendInstallResult.Failed -> failed(result.cause)
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            CrashReporter.logException(error)
            return failed(error)
        } finally {
            if (!pending) lease?.let { freezeManager.finishFrozenUpdate(packageName, it) }
        }
    }

    /** Spool in bounded chunks so streams use the same package validation and freeze policy. */
    suspend fun installFromStream(apkStream: InputStream, packageName: String, sizeBytes: Long = -1): InstallResult {
        val temporary = File.createTempFile("store-install-", ".apk", context.cacheDir)
        return try {
            temporary.outputStream().use { apkStream.copyTo(it, 64 * 1024) }
            install(temporary, packageName)
        } finally { temporary.delete() }
    }
}
