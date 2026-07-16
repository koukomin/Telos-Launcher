package de.mm20.launcher2.freeze

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import de.mm20.launcher2.freeze.providers.RootProvider
import de.mm20.launcher2.freeze.providers.ShizukuProvider
import de.mm20.launcher2.preferences.FreezeBackendPreference
import de.mm20.launcher2.preferences.freeze.FreezeSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

class FreezeManager internal constructor(
    private val context: Context,
    private val settings: FreezeSettings,
) {
    private val shizukuProvider = ShizukuProvider(context)
    private val rootProvider = RootProvider()

    private val _activeBackend = MutableStateFlow<FreezeBackendType?>(null)
    val activeBackend: StateFlow<FreezeBackendType?> = _activeBackend.asStateFlow()

    /** Re-evaluates which backend (if any) is currently usable, based on the user's preference. */
    suspend fun refreshBackendState() {
        _activeBackend.value = when (settings.backend.first()) {
            FreezeBackendPreference.ShizukuOnly ->
                FreezeBackendType.Shizuku.takeIf { shizukuProvider.isAvailable() }

            FreezeBackendPreference.RootOnly ->
                FreezeBackendType.Root.takeIf { rootProvider.isAvailable() }

            FreezeBackendPreference.Auto -> when {
                shizukuProvider.isAvailable() -> FreezeBackendType.Shizuku
                rootProvider.isAvailable() -> FreezeBackendType.Root
                else -> null
            }
        }
    }

    suspend fun hasPermission(): Boolean = when (_activeBackend.value) {
        FreezeBackendType.Shizuku -> shizukuProvider.hasPermission()
        FreezeBackendType.Root -> rootProvider.hasPermission()
        null -> false
    }

    /** Prompts for permission on the active backend, if any. Suspends until the user responds. */
    suspend fun requestPermission(): Boolean = when (_activeBackend.value) {
        FreezeBackendType.Shizuku -> shizukuProvider.requestPermission()
        FreezeBackendType.Root -> rootProvider.requestPermission()
        null -> false
    }

    suspend fun freeze(packageName: String): Boolean = setSuspended(listOf(packageName), true)

    suspend fun unfreeze(packageName: String): Boolean = setSuspended(listOf(packageName), false)

    suspend fun freeze(packageNames: List<String>): Boolean = setSuspended(packageNames, true)

    private suspend fun setSuspended(packageNames: List<String>, suspended: Boolean): Boolean {
        if (packageNames.isEmpty()) return true
        val provider = when (_activeBackend.value) {
            FreezeBackendType.Shizuku -> shizukuProvider
            FreezeBackendType.Root -> rootProvider
            null -> return false
        }
        return provider.setPackagesSuspended(packageNames, suspended)
    }

    /** Live read of the OS-level suspended flag; not dependent on any cached launcher state. */
    fun isFrozen(packageName: String): Boolean {
        return try {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            (info.flags and ApplicationInfo.FLAG_SUSPENDED) != 0
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    val autoFreezeEnabled = settings.autoFreezeEnabled
    val autoFreezeCandidates = settings.candidates

    fun setAutoFreezeCandidate(packageName: String, enabled: Boolean) {
        settings.setCandidateEnabled(packageName, enabled)
    }
}
