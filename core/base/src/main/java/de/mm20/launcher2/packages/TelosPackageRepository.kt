package de.mm20.launcher2.packages

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.runtime.Immutable
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import de.mm20.launcher2.coroutines.TelosDispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

@Immutable
data class TelosAppEntry(
    val packageName: String,
    val label: String,
    val versionName: String,
    val versionCode: Long,
    val isSystemApp: Boolean,
    val isFrozen: Boolean,
    val isEnabled: Boolean,
    val isSuspended: Boolean,
    val sourceDir: String?,
    val apkBytes: Long,
    val firstInstallTime: Long,
    val lastUpdateTime: Long,
    val uid: Int,
)

/**
 * Single shared, reactive package repository eliminating redundant `PackageManager` full scans
 * across Smart Freeze Manager, ART Optimizer, Telos Store, and system diagnostics.
 *
 * - Exposes a single `StateFlow<List<TelosAppEntry>>` where every element is `@Immutable`.
 * - Registers one system-wide [PackageChangeReceiver] (`ADDED`, `REMOVED`, `CHANGED`, `REPLACED`,
 *   `PACKAGES_SUSPENDED`, `PACKAGES_UNSUSPENDED`) that incrementally updates only the affected package(s)
 *   in O(1) PackageManager lookups instead of reloading all 200+ installed apps.
 */
class TelosPackageRepository private constructor(context: Context) {

    private val appContext: Context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + TelosDispatchers.Disk)
    private val updateMutex = Mutex()
    private val entriesByPkg = ConcurrentHashMap<String, TelosAppEntry>(256)

    private val receiverRegistered = AtomicBoolean(false)
    private val initialLoadCompleted = AtomicBoolean(false)

    private val _packages = MutableStateFlow<ImmutableList<TelosAppEntry>>(persistentListOf())
    val packages: StateFlow<ImmutableList<TelosAppEntry>> = _packages.asStateFlow()

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            when (action) {
                Intent.ACTION_PACKAGES_SUSPENDED,
                Intent.ACTION_PACKAGES_UNSUSPENDED -> {
                    val changedPkgs = intent.getStringArrayExtra(Intent.EXTRA_CHANGED_PACKAGE_LIST)
                        ?.filter { it.isNotBlank() }
                        .orEmpty()
                    if (changedPkgs.isNotEmpty()) {
                        scope.launch { refreshPackagesIncremental(changedPkgs, invalidateIcons = false) }
                    }
                }
                Intent.ACTION_PACKAGE_REMOVED -> {
                    val pkg = intent.data?.schemeSpecificPart ?: return
                    val isReplacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
                    if (!isReplacing) {
                        scope.launch { removePackageIncremental(pkg) }
                    }
                }
                Intent.ACTION_PACKAGE_ADDED,
                Intent.ACTION_PACKAGE_CHANGED,
                Intent.ACTION_PACKAGE_REPLACED -> {
                    val pkg = intent.data?.schemeSpecificPart ?: return
                    val invalidateIcon = action == Intent.ACTION_PACKAGE_REPLACED ||
                        action == Intent.ACTION_PACKAGE_ADDED
                    scope.launch { refreshPackagesIncremental(listOf(pkg), invalidateIcons = invalidateIcon) }
                }
            }
        }
    }

    /**
     * Ensures the broadcast receiver is registered and returns the current (or freshly loaded)
     * immutable snapshot off the main thread.
     */
    suspend fun awaitPackages(forceFullReload: Boolean = false): List<TelosAppEntry> =
        withContext(TelosDispatchers.Disk) {
            ensureReceiverRegistered()
            if (!forceFullReload && initialLoadCompleted.get()) {
                return@withContext _packages.value
            }
            updateMutex.withLock {
                if (!forceFullReload && initialLoadCompleted.get()) {
                    return@withLock _packages.value
                }
                reloadAllLocked()
            }
        }

    fun triggerAsyncWarmup() {
        ensureReceiverRegistered()
        if (!initialLoadCompleted.get()) {
            scope.launch {
                awaitPackages(forceFullReload = false)
            }
        }
    }

    fun getSnapshotEntry(packageName: String): TelosAppEntry? = entriesByPkg[packageName]

    suspend fun refreshSinglePackage(packageName: String) {
        ensureReceiverRegistered()
        refreshPackagesIncremental(listOf(packageName), invalidateIcons = false)
    }

    private fun ensureReceiverRegistered() {
        if (!receiverRegistered.compareAndSet(false, true)) return
        runCatching {
            val pkgFilter = IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addDataScheme("package")
            }
            appContext.registerReceiver(packageReceiver, pkgFilter)

            val suspendFilter = IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGES_SUSPENDED)
                addAction(Intent.ACTION_PACKAGES_UNSUSPENDED)
            }
            appContext.registerReceiver(packageReceiver, suspendFilter)
        }.onFailure {
            receiverRegistered.set(false)
        }
    }

    private fun reloadAllLocked(): List<TelosAppEntry> {
        val pm = appContext.packageManager
        val installed = runCatching {
            pm.getInstalledApplications(PackageManager.MATCH_DISABLED_COMPONENTS)
        }.getOrDefault(emptyList())

        val freshMap = HashMap<String, TelosAppEntry>(installed.size)
        for (appInfo in installed) {
            if ((appInfo.flags and ApplicationInfo.FLAG_INSTALLED) == 0) continue
            val entry = buildAppEntry(pm, appInfo)
            freshMap[entry.packageName] = entry
        }

        entriesByPkg.clear()
        entriesByPkg.putAll(freshMap)
        val sorted = buildSortedSnapshot()
        _packages.value = sorted.toImmutableList()
        initialLoadCompleted.set(true)
        return sorted
    }

    private suspend fun removePackageIncremental(packageName: String) {
        if (!initialLoadCompleted.get()) return
        updateMutex.withLock {
            val removed = entriesByPkg.remove(packageName)
            TelosIconCache.invalidate(packageName, appContext)
            if (removed != null) {
                _packages.value = buildSortedSnapshot().toImmutableList()
            }
        }
    }

    private suspend fun refreshPackagesIncremental(
        packageNames: List<String>,
        invalidateIcons: Boolean,
    ) {
        if (packageNames.isEmpty()) return
        if (!initialLoadCompleted.get()) {
            awaitPackages(forceFullReload = false)
            return
        }
        updateMutex.withLock {
            val pm = appContext.packageManager
            var changed = false
            for (pkg in packageNames) {
                if (invalidateIcons) {
                    TelosIconCache.invalidate(pkg, appContext)
                }
                val appInfo = runCatching {
                    pm.getApplicationInfo(pkg, PackageManager.MATCH_DISABLED_COMPONENTS)
                }.getOrNull()

                if (appInfo == null || (appInfo.flags and ApplicationInfo.FLAG_INSTALLED) == 0) {
                    if (entriesByPkg.remove(pkg) != null) {
                        changed = true
                    }
                } else {
                    val updated = buildAppEntry(pm, appInfo)
                    val previous = entriesByPkg.put(pkg, updated)
                    if (previous != updated) {
                        changed = true
                    }
                }
            }
            if (changed) {
                _packages.value = buildSortedSnapshot().toImmutableList()
            }
        }
    }

    private fun buildAppEntry(pm: PackageManager, appInfo: ApplicationInfo): TelosAppEntry {
        val pkg = appInfo.packageName
        val pkgInfo = runCatching {
            pm.getPackageInfo(pkg, PackageManager.MATCH_DISABLED_COMPONENTS)
        }.getOrNull()

        val label = runCatching { pm.getApplicationLabel(appInfo).toString() }
            .getOrDefault(pkg)
            .ifBlank { pkg }

        val versionName = pkgInfo?.versionName.orEmpty()
        val versionCode = pkgInfo?.let { PackageInfoCompat.getLongVersionCode(it) } ?: 0L
        val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        val isEnabled = appInfo.enabled
        val isSuspended = (appInfo.flags and ApplicationInfo.FLAG_SUSPENDED) != 0
        val isFrozen = isPackageFrozenOrDisabled(pm, appInfo, isEnabled, isSuspended)
        val srcDir = appInfo.sourceDir
        val apkBytes = if (!srcDir.isNullOrEmpty()) {
            runCatching { File(srcDir).length() }.getOrDefault(0L)
        } else {
            0L
        }

        return TelosAppEntry(
            packageName = pkg,
            label = label,
            versionName = versionName,
            versionCode = versionCode,
            isSystemApp = isSystem,
            isFrozen = isFrozen,
            isEnabled = isEnabled,
            isSuspended = isSuspended,
            sourceDir = srcDir,
            apkBytes = apkBytes,
            firstInstallTime = pkgInfo?.firstInstallTime ?: 0L,
            lastUpdateTime = pkgInfo?.lastUpdateTime ?: 0L,
            uid = appInfo.uid,
        )
    }

    private fun buildSortedSnapshot(): List<TelosAppEntry> {
        return entriesByPkg.values.sortedWith(ENTRY_COMPARATOR)
    }

    companion object {
        private val ENTRY_COMPARATOR = compareBy<TelosAppEntry>(
            { it.label.lowercase() },
            { it.packageName },
        )

        @Volatile
        private var instance: TelosPackageRepository? = null

        fun getInstance(context: Context): TelosPackageRepository {
            return instance ?: synchronized(this) {
                instance ?: TelosPackageRepository(context.applicationContext).also {
                    instance = it
                }
            }
        }

        fun isPackageFrozenOrDisabled(
            pm: PackageManager,
            appInfo: ApplicationInfo,
            isEnabled: Boolean = appInfo.enabled,
            isSuspended: Boolean = (appInfo.flags and ApplicationInfo.FLAG_SUSPENDED) != 0,
        ): Boolean {
            if (!isEnabled || isSuspended) return true
            return runCatching {
                val intent = Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setPackage(appInfo.packageName)
                val resolved = pm.resolveActivity(intent, PackageManager.MATCH_DISABLED_COMPONENTS)?.activityInfo
                if (resolved != null) {
                    val hasEnabled = pm.queryIntentActivities(intent, 0).any { it.activityInfo?.enabled == true }
                    !hasEnabled && (
                        !resolved.enabled ||
                            pm.getComponentEnabledSetting(
                                ComponentName(resolved.packageName, resolved.name)
                            ) == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                        )
                } else {
                    false
                }
            }.getOrDefault(false)
        }
    }
}
