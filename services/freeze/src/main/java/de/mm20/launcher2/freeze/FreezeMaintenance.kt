package de.mm20.launcher2.freeze

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Process
import de.mm20.launcher2.preferences.FrozenAppRestore
import de.mm20.launcher2.preferences.freeze.FreezeSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class FrozenAppGroup { User, System, SystemWithoutLauncher }
data class FrozenAppInventory(val state: FrozenAppRestore, val group: FrozenAppGroup)

/** Device-local, write-ahead journal. Preparing a lease never changes an app before saving it. */
internal class FreezeMaintenance(
    private val context: Context,
    private val settings: FreezeSettings,
    private val mutex: Mutex,
    private val isProtected: (String) -> Boolean,
    private val provider: suspend (String?) -> Pair<String, PrivilegedAccessProvider>?,
    private val foregroundPackage: () -> String?,
) {
    private val activeInstalls = mutableSetOf<String>()
    private val pm get() = context.packageManager
    private val userId get() = Process.myUid() / 100000
    private val flags = PackageManager.MATCH_DISABLED_COMPONENTS or PackageManager.MATCH_UNINSTALLED_PACKAGES
    private fun key(pkg: String) = "$userId:$pkg"

    private fun launcherComponents(packageName: String? = null) = pm.queryIntentActivities(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).apply { `package` = packageName }, flags)
        .mapNotNull { it.activityInfo }

    private fun capture(packageName: String, backend: String = "", installer: Boolean = false): FrozenAppRestore? {
        require(packageName.matches(Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)*")))
        val info = try { pm.getApplicationInfo(packageName, flags) }
            catch (_: PackageManager.NameNotFoundException) { return null }
        // MATCH_UNINSTALLED also finds retained data. Never reinstall or freeze those records.
        if (info.flags and ApplicationInfo.FLAG_INSTALLED == 0) return null
        val components = launcherComponents(packageName).mapNotNull { activity ->
            val cn = ComponentName(activity.packageName, activity.name)
            val setting = pm.getComponentEnabledSetting(cn)
            if (setting >= PackageManager.COMPONENT_ENABLED_STATE_DISABLED) cn.flattenToString() to setting else null
        }.toMap()
        val hidden = try { pm.getApplicationInfo(packageName, PackageManager.MATCH_DISABLED_COMPONENTS); false }
            catch (_: PackageManager.NameNotFoundException) { true }
        val enabled = pm.getApplicationEnabledSetting(packageName)
        val suspended = info.flags and ApplicationInfo.FLAG_SUSPENDED != 0
        val hasEnabledLauncher = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(packageName), 0
        ).any { it.activityInfo?.enabled == true }
        if (!hidden && !suspended && enabled < 2 && !isComponentFreeze(hasEnabledLauncher, components.isNotEmpty())) return null
        return FrozenAppRestore(packageName, userId, suspended, enabled, hidden, components, installer, backend)
    }

    suspend fun scan(): List<FrozenAppInventory> = withContext(Dispatchers.IO) {
        val launchers = launcherComponents().map { it.packageName }.toSet()
        pm.getInstalledApplications(flags).asSequence()
            .filter { it.flags and ApplicationInfo.FLAG_INSTALLED != 0 && !isProtected(it.packageName) }
            .mapNotNull { info -> capture(info.packageName)?.let { state ->
                val system = info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                FrozenAppInventory(state, if (!system) FrozenAppGroup.User else
                    if (info.packageName in launchers) FrozenAppGroup.System else FrozenAppGroup.SystemWithoutLauncher)
            } }.take(10_000).toList()
    }

    private fun valid(entry: FrozenAppRestore, allowProtected: Boolean = false) {
        require(entry.userId == userId && (allowProtected || !isProtected(entry.packageName)))
        require(entry.packageName.matches(Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)*")))
        require(entry.enabledState in 0..4 && entry.components.size <= 1000)
        require(entry.hidden || entry.suspended || entry.enabledState >= 2 || entry.components.isNotEmpty())
        entry.components.forEach { (component, state) ->
            val cn = requireNotNull(ComponentName.unflattenFromString(component))
            require(cn.packageName == entry.packageName && cn.className.matches(Regex("[A-Za-z0-9_.$]+")) && state in 2..4)
        }
    }

    private suspend fun thaw(entry: FrozenAppRestore, backend: PrivilegedAccessProvider, allowProtected: Boolean = false): Boolean {
        valid(entry, allowProtected)
        val pkg = entry.packageName
        if (entry.hidden && !backend.setHidden(pkg, false, userId)) return false
        val changed = transitionFreezeState(listOf(pkg), false,
            suspendPackages = { names, value -> backend.setPackagesSuspended(names, value, userId) },
            enablePackages = { names, value -> backend.setPackagesEnabled(names, value, userId) })
        if (pkg !in changed) return false
        val hasLauncher = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(pkg), 0
        ).any { it.activityInfo?.enabled == true }
        for (component in if (hasLauncher) emptyList() else entry.components.keys.take(1)) {
            if (!backend.setComponentSetting(component, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, userId)) return false
        }
        repeat(30) {
            if (capture(pkg) == null) return true
            kotlinx.coroutines.delay(100)
        }
        return false
    }

    private suspend fun restore(entry: FrozenAppRestore, backend: PrivilegedAccessProvider): Boolean {
        valid(entry)
        val pkg = entry.packageName
        if (settings.excludeForeground.first() && foregroundPackage() == pkg) return false
        val current = try { pm.getApplicationInfo(pkg, flags) } catch (_: PackageManager.NameNotFoundException) { return false }
        if (current.flags and ApplicationInfo.FLAG_INSTALLED == 0) return false
        val before = capture(pkg)
        if (before != null && matches(entry, before)) return true
        // Re-establish protection first; even a retired launcher alias must not leave an app thawed.
        if (entry.enabledState >= 2 && pm.getApplicationEnabledSetting(pkg) != entry.enabledState &&
            !backend.setEnabledSetting(pkg, entry.enabledState, userId)) return false
        if (entry.components.isNotEmpty() && entry.enabledState < 2 && !entry.suspended && !entry.hidden &&
            pkg !in backend.setPackagesEnabled(listOf(pkg), false, userId)) return false
        if (entry.suspended && pkg !in backend.setPackagesSuspended(listOf(pkg), true, userId)) return false
        if (entry.hidden && !backend.setHidden(pkg, true, userId)) return false
        for ((component, state) in entry.components) {
            if (pm.getComponentEnabledSetting(ComponentName.unflattenFromString(component)!!) != state &&
                !backend.setComponentSetting(component, state, userId)) return false
        }
        if (pm.getApplicationEnabledSetting(pkg) != entry.enabledState &&
            !backend.setEnabledSetting(pkg, entry.enabledState, userId)) return false
        if (!entry.suspended && pkg !in backend.setPackagesSuspended(listOf(pkg), false, userId)) return false
        val actual = capture(pkg) ?: return false
        return matches(entry, actual)
    }

    private fun matches(entry: FrozenAppRestore, actual: FrozenAppRestore) =
        actual.suspended == entry.suspended && actual.hidden == entry.hidden &&
            actual.enabledState == entry.enabledState && entry.components.all { (c, state) -> actual.components[c] == state }


    suspend fun unfreezeAll(groups: Set<FrozenAppGroup>): Int = withContext(NonCancellable + Dispatchers.IO) {
        mutex.withLock {
            val (name, backend) = provider(null) ?: return@withLock -1
            var failed = 0
            for (row in scan().filter { it.group in groups }) {
                try {
                    val pkg = row.state.packageName
                    val existing = settings.maintenance.first()[key(pkg)]
                    if (existing?.installer == true || existing?.operationId?.isNotEmpty() == true) { failed++; continue }
                    val entry = existing ?: row.state.copy(backend = name)
                    if (!saveThenThaw(entry, { settings.saveMaintenance(key(pkg), it) }, { thaw(it, backend) })) failed++
                } catch (error: Exception) {
                    android.util.Log.w("FreezeMaintenance", "App operation failed; recovery history retained", error)
                    failed++
                }
            }
            failed
        }
    }

    suspend fun restoreAll(): Int = withContext(NonCancellable + Dispatchers.IO) {
        mutex.withLock {
            var failed = 0
            for ((id, entry) in settings.maintenance.first()) {
                try {
                    // Active installer sessions are completed by their callback, never by bulk actions.
                    if (entry.installer) continue
                    if (entry.operationId.isNotEmpty() && (id in activeInstalls ||
                        pm.packageInstaller.mySessions.any { it.appPackageName == entry.packageName })) { failed++; continue }
                    val backend = provider(entry.backend)?.second
                    if (backend == null || !restoreThenForget(entry, { restore(it, backend) },
                        { settings.saveMaintenance(id, null) })) failed++
                } catch (error: Exception) {
                    android.util.Log.w("FreezeMaintenance", "App operation failed; recovery history retained", error)
                    failed++
                }
            }
            failed
        }
    }

    suspend fun beginInstall(packageName: String, backendName: String, thawFirst: Boolean): String? = withContext(NonCancellable + Dispatchers.IO) {
        mutex.withLock {
            val existing = settings.maintenance.first()[key(packageName)]
            check(existing?.operationId.isNullOrEmpty()) { "An update is already pending" }
            val token = java.util.UUID.randomUUID().toString()
            val entry = existing?.copy(operationId = token)
                ?: capture(packageName, backendName, installer = true)?.copy(operationId = token)
                ?: return@withLock null
            valid(entry)
            if (existing == null) {
                check(provider(backendName) != null) { "Freeze backend unavailable" }
                // Device Owner cannot restore arbitrary package/component disable overrides.
                check(backendName != "DeviceOwner" || (entry.enabledState < 2 && entry.components.isEmpty())) {
                    "This frozen state requires Root or Shizuku"
                }
            }
            // Write ahead before compatibility thawing; automatic freeze respects this hold.
            settings.saveMaintenance(key(packageName), entry)
            if (thawFirst && existing == null && (backendName == "Shizuku" || backendName == "Root")) {
                val backend = provider(backendName)?.second ?: error("Freeze backend unavailable")
                try {
                    check(thaw(entry, backend)) { "Could not temporarily unfreeze app for installation" }
                } catch (error: Exception) {
                    if (runCatching { restore(entry, backend) }.getOrDefault(false))
                        settings.saveMaintenance(key(packageName), null)
                    throw error
                }
            }
            activeInstalls += key(packageName)
            entry.operationId
        }
    }

    suspend fun finishInstall(packageName: String, token: String): Boolean = withContext(NonCancellable + Dispatchers.IO) {
        mutex.withLock {
            try {
                val entry = settings.maintenance.first()[key(packageName)] ?: return@withLock true
                if (!ownsFrozenUpdate(entry.operationId, token)) return@withLock false
                activeInstalls -= key(packageName)
                if (!entry.installer) {
                    settings.saveMaintenance(key(packageName), entry.copy(operationId = ""))
                    return@withLock true
                }
                val backend = provider(entry.backend)?.second ?: return@withLock false
                restoreThenForget(entry, { restore(it, backend) }, { settings.saveMaintenance(key(packageName), null) })
            } catch (error: Exception) {
                android.util.Log.w("FreezeMaintenance", "Update restoration pending", error)
                false
            }
        }
    }

    /** Explicit recovery, after an interrupted installer; never silently forget its original state. */
    suspend fun recoverInstalls(): Int = withContext(NonCancellable + Dispatchers.IO) {
        mutex.withLock {
            var failed = 0
            for ((id, entry) in settings.maintenance.first().filterValues { it.installer }) {
                try {
                    val ownSession = pm.packageInstaller.mySessions.any { it.appPackageName == entry.packageName }
                    if (ownSession || (id in activeInstalls && entry.backend != "DeviceOwner")) { failed++; continue }
                    activeInstalls -= id
                    val backend = provider(entry.backend)?.second
                    if (backend == null || !restoreThenForget(entry, { restore(it, backend) },
                        { settings.saveMaintenance(id, null) })) failed++
                } catch (error: Exception) {
                    android.util.Log.w("FreezeMaintenance", "App operation failed; recovery history retained", error)
                    failed++
                }
            }
            failed
        }
    }
    /** Survives removal of the old app row after enablement; waits for an actual launcher activity. */
    suspend fun launchThawed(packageName: String, user: android.os.UserHandle, preferred: ComponentName,
        launch: (ComponentName) -> Boolean): Boolean = withContext(NonCancellable) {
        mutex.withLock {
            val apps = requireNotNull(context.getSystemService(android.content.pm.LauncherApps::class.java))
            if (user == Process.myUserHandle()) {
                val entry = withContext(Dispatchers.IO) { capture(packageName) }
                if (entry != null) {
                    val backend = provider(null)?.second
                    if (backend != null) {
                        withContext(Dispatchers.IO) { thaw(entry, backend, allowProtected = true) }
                    }
                }
            } else {
                val info = runCatching { apps.getApplicationInfo(packageName, flags, user) }.getOrNull()
                if (info == null || !info.enabled || !apps.isPackageEnabled(packageName, user) ||
                    info.flags and ApplicationInfo.FLAG_SUSPENDED != 0) {
                    val (_, backend) = provider(null) ?: (null to null)
                    if (backend != null) {
                        val targetUser = user.hashCode()
                        transitionFreezeState(listOf(packageName), false,
                            suspendPackages = { names, value -> backend.setPackagesSuspended(names, value, targetUser) },
                            enablePackages = { names, value -> backend.setPackagesEnabled(names, value, targetUser) })
                    }
                }
            }
            launchWhenReady(
                find = {
                    val ready = runCatching {
                        val info = apps.getApplicationInfo(packageName, flags, user)
                        info.enabled && apps.isPackageEnabled(packageName, user) &&
                            info.flags and ApplicationInfo.FLAG_SUSPENDED == 0
                    }.getOrDefault(false)
                    val activities = if (ready) runCatching {
                        apps.getActivityList(packageName, user)
                    }.getOrDefault(emptyList()) else emptyList()
                    val found = activities.firstOrNull { it.componentName == preferred }?.componentName
                        ?: activities.firstOrNull()?.componentName
                    found ?: runCatching {
                        context.packageManager.getLaunchIntentForPackage(packageName)?.component
                    }.getOrNull() ?: preferred
                }, launch = launch,
            )
        }
    }

}
