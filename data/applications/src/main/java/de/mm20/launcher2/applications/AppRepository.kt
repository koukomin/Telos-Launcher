package de.mm20.launcher2.applications

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import de.mm20.launcher2.ktx.getSerialNumber
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.preferences.freeze.FreezeSettings
import de.mm20.launcher2.profiles.Profile
import de.mm20.launcher2.profiles.ProfileManager
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.search.ResultScore
import de.mm20.launcher2.search.SearchableRepository
import de.mm20.launcher2.search.StringNormalizer
import de.mm20.launcher2.search.VirtualAppProvider
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

interface AppRepository : SearchableRepository<Application> {
    fun findOne(
        packageName: String,
        user: UserHandle,
    ): Flow<Application?>

    fun findMany(): Flow<ImmutableList<Application>>

    /**
     * One-shot scan for launchable activities that [findMany] skips because they have no icon
     * resource at all (see the `iconResource == 0` check in `getApplications`) - background
     * components and some system services resolve a MAIN/LAUNCHER activity but were never meant
     * to show up in an app list. Freeze Manager's "apps without icon" toggle needs to reach these
     * too, since anything with an activity can still be suspended/disabled like any other app.
     */
    suspend fun findIconlessApps(): List<Application>
}

internal class AppRepositoryImpl(
    private val context: Context,
    private val profileManager: ProfileManager,
    private val stringNormalizer: StringNormalizer,
    private val freezeSettings: FreezeSettings,
    private val virtualAppProviders: List<VirtualAppProvider> = emptyList(),
) : AppRepository {
    private val scope = CoroutineScope(Dispatchers.Default + Job())

    private val launcherApps =
        context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps

    private val installedApps = MutableStateFlow<List<LauncherApp>>(emptyList())

    private val profiles = profileManager.activeProfiles

    private val mutex = Mutex()

    init {
        launcherApps.registerCallback(object : LauncherApps.Callback() {
            override fun onPackagesUnavailable(
                packageNames: Array<out String>,
                user: UserHandle,
                replacing: Boolean
            ) {
                scope.launch {
                    mutex.withLock {
                        val apps = installedApps.value.toMutableList()
                        apps.removeAll { packageNames.contains(it.componentName.packageName) && it.user == user }
                        installedApps.value = apps
                    }
                }
            }

            override fun onPackageChanged(packageName: String, user: UserHandle) {
                scope.launch {
                    mutex.withLock {
                        val apps = installedApps.value.toMutableList()
                        apps.removeAll { packageName == it.componentName.packageName && it.user == user }
                        apps.addAll(getApplications(packageName, user))
                        installedApps.value = apps
                    }
                }
            }

            override fun onPackagesAvailable(
                packageNames: Array<out String>,
                user: UserHandle,
                replacing: Boolean
            ) {
                scope.launch {
                    mutex.withLock {
                        val apps = installedApps.value.toMutableList()
                        for (packageName in packageNames) {
                            apps.addAll(getApplications(packageName, user))
                        }
                        installedApps.value = apps
                    }
                }
            }

            override fun onPackageAdded(packageName: String, user: UserHandle) {
                scope.launch {
                    mutex.withLock {
                        val apps = installedApps.value.toMutableList()
                        apps.addAll(getApplications(packageName, user))
                        installedApps.value = apps
                    }
                }
            }

            override fun onPackageRemoved(packageName: String, user: UserHandle) {
                scope.launch {
                    mutex.withLock {
                        val apps = installedApps.value.toMutableList()
                        apps.removeAll { packageName == it.componentName.packageName && it.user == user }
                        installedApps.value = apps

                    }
                }
            }

            override fun onShortcutsChanged(
                packageName: String,
                shortcuts: MutableList<ShortcutInfo>,
                user: UserHandle
            ) {
                onPackageChanged(packageName, user)
            }

            override fun onPackagesSuspended(packageNames: Array<out String>?, user: UserHandle?) {
                packageNames ?: return
                scope.launch {
                    mutex.withLock {
                        val apps = installedApps.value.toMutableList()
                        apps.replaceAll {
                            if (packageNames.contains(it.componentName.packageName) && it.user == user) {
                                it.copy(isSuspended = true)
                            } else {
                                it
                            }
                        }
                        installedApps.value = apps
                    }
                }
            }

            override fun onPackagesUnsuspended(
                packageNames: Array<out String>?,
                user: UserHandle?
            ) {
                packageNames ?: return
                scope.launch {
                    mutex.withLock {
                        val apps = installedApps.value.toMutableList()
                        apps.replaceAll {
                            if (packageNames.contains(it.componentName.packageName) && it.user == user) {
                                it.copy(isSuspended = false)
                            } else {
                                it
                            }
                        }
                        installedApps.value = apps
                    }
                }
            }

        }, Handler(Looper.getMainLooper()))
        scope.launch {
            profiles.runningFold<List<Profile>, Pair<List<Profile>?, List<Profile>?>>(null to null) { acc, value ->
                acc.second to value
            }.collectLatest { (prev, curr) ->
                if (curr == null) return@collectLatest
                if (prev == null) {
                    curr.forEach { addProfile(it) }
                } else {
                    val added = curr - prev
                    val removed = prev - curr
                    added.forEach { addProfile(it) }
                    removed.forEach { removeProfile(it) }
                }
            }
        }
    }

    private suspend fun addProfile(profile: Profile) {
        mutex.withLock {
            val apps = installedApps.value.toMutableList()
            apps.addAll(getApplications(null, profile.userHandle))
            installedApps.value = apps
        }
    }

    private fun removeProfile(profile: Profile) {
        scope.launch {
            mutex.withLock {
                val apps = installedApps.value.toMutableList()
                apps.removeAll { it.user == profile.userHandle }
                installedApps.value = apps
            }
        }
    }

    // === TELOS_PENDING_REVIEW_START: dual_apps_and_multi_user_fix ===
    private suspend fun getApplications(
        packageName: String?,
        userHandle: UserHandle,
        includeIconless: Boolean = false,
    ): List<LauncherApp> {
        if (packageName == context.packageName && !context.packageName.endsWith(".debug")) return emptyList()

        val apps = mutableListOf<LauncherApp>()
        val seenPackages = mutableSetOf<String>()

        // 1. Query LauncherApps directly for the specific profile/userHandle.
        // This is multi-user and profile aware (Dual Apps, Work Profiles, Private Space).
        val activityList = try {
            launcherApps.getActivityList(packageName, userHandle)
        } catch (e: Exception) {
            emptyList()
        }

        for (info in activityList) {
            val pkg = info.applicationInfo.packageName
            if (pkg == context.packageName && !context.packageName.endsWith(".debug")) continue

            if (pkg in seenPackages) continue

            val app = LauncherApp(context, info)
            apps.add(app)
            seenPackages.add(pkg)
        }

        // 2. Only for the primary user, query PackageManager for disabled/frozen components
        // whose LauncherActivityInfo is null because they were disabled via pm disable.
        if (userHandle == Process.myUserHandle()) {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            if (packageName != null) intent.`package` = packageName

            val flags = PackageManager.MATCH_DISABLED_COMPONENTS or
                    PackageManager.MATCH_DIRECT_BOOT_AWARE or
                    PackageManager.MATCH_DIRECT_BOOT_UNAWARE

            val allActivities = try {
                if (isAtLeastApiLevel(33)) {
                    pm.queryIntentActivities(
                        intent,
                        PackageManager.ResolveInfoFlags.of(flags.toLong())
                    )
                } else {
                    pm.queryIntentActivities(intent, flags)
                }
            } catch (e: Exception) {
                emptyList()
            }

            for (resolveInfo in allActivities) {
                val activityInfo = resolveInfo.activityInfo ?: continue
                val pkg = activityInfo.packageName
                
                if (pkg in seenPackages) continue
                if (pkg == context.packageName && !context.packageName.endsWith(".debug")) continue

                val compName = ComponentName(pkg, activityInfo.name)
                val label = resolveInfo.loadLabel(pm).toString()
                if (label.isEmpty()) continue
                if (!includeIconless && resolveInfo.iconResource == 0 && activityInfo.applicationInfo.icon == 0) continue

                val isAppEnabled = activityInfo.applicationInfo.enabled
                val isActivityEnabled = activityInfo.enabled
                val isSuspended = (activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SUSPENDED) != 0

                apps.add(
                    LauncherApp(
                        componentName = compName,
                        label = label,
                        user = userHandle,
                        launcherActivityInfo = null,
                        applicationInfo = activityInfo.applicationInfo,
                        versionName = LauncherApp.getPackageVersionName(context, pkg),
                        isSuspended = isSuspended || !isAppEnabled || !isActivityEnabled,
                        userSerialNumber = userHandle.getSerialNumber(context),
                        disabledActivityInfo = activityInfo,
                    )
                )
                seenPackages.add(pkg)
            }
        }

        return apps
    }
    // === TELOS_PENDING_REVIEW_END: dual_apps_and_multi_user_fix ===


    private fun getApplication(
        launcherActivityInfo: LauncherActivityInfo
    ): LauncherApp? {
        if (launcherActivityInfo.applicationInfo.packageName == context.packageName && !context.packageName.endsWith(
                ".debug"
            )
        ) return null
        return LauncherApp(context, launcherActivityInfo)
    }

    override fun findOne(
        packageName: String,
        user: UserHandle,
    ): Flow<Application?> {
        return installedApps.map {
            it.firstOrNull {
                it.componentName.packageName == packageName && it.user == user
            }
        }
    }

    override fun findMany(): Flow<ImmutableList<Application>> {
        val virtualApps = virtualAppProviders.flatMap { it.getVirtualApps() }
        return installedApps.map { (virtualApps + it).toImmutableList() }
    }

    override suspend fun findIconlessApps(): List<Application> = withContext(Dispatchers.Default) {
        val existing = installedApps.value.map { it.componentName.packageName to it.user }.toSet()
        val pm = context.packageManager
        profiles.first().flatMap { profile ->
            // MATCH_UNINSTALLED_PACKAGES is required to see packages hidden via
            // DevicePolicyManager.setApplicationHidden (the Icebox/Island freeze mechanism) -
            // without it they're excluded just like truly uninstalled packages. Filter back down
            // to FLAG_INSTALLED so we don't surface stale data-only leftovers as noise.
            pm.getInstalledApplications(PackageManager.GET_META_DATA or PackageManager.MATCH_UNINSTALLED_PACKAGES).mapNotNull { appInfo ->
                if (appInfo.packageName == context.packageName) return@mapNotNull null
                if ((appInfo.flags and ApplicationInfo.FLAG_INSTALLED) == 0) return@mapNotNull null
                if ((appInfo.packageName to profile.userHandle) in existing) return@mapNotNull null

                val label = appInfo.loadLabel(pm).toString()
                if (label.isEmpty()) return@mapNotNull null

                // === TELOS_PENDING_REVIEW_START: iconless_apps_iconpack_fix ===
                val launchClassName = pm.getLaunchIntentForPackage(appInfo.packageName)?.component?.className ?: ""
                // === TELOS_PENDING_REVIEW_END: iconless_apps_iconpack_fix ===

                LauncherApp(
                    componentName = ComponentName(appInfo.packageName, launchClassName),
                    label = label,
                    user = profile.userHandle,
                    launcherActivityInfo = null,
                    applicationInfo = appInfo,
                    versionName = LauncherApp.getPackageVersionName(context, appInfo.packageName),
                    isSuspended = (appInfo.flags and ApplicationInfo.FLAG_SUSPENDED) != 0 || !appInfo.enabled,
                    userSerialNumber = profile.userHandle.getSerialNumber(context),
                    disabledActivityInfo = null,
                )
            }
        }
    }

    override fun search(query: String, allowNetwork: Boolean): Flow<ImmutableList<Application>> {
        val normalizedQuery = stringNormalizer.normalize(query)

        return installedApps.map { apps ->
            withContext(Dispatchers.Default) {
                val normalizerId = stringNormalizer.id
                val appResults = mutableListOf<Application>()
                
                // === TELOS_PENDING_REVIEW_START: virtual_app_koin_fix ===
                val virtualApps = virtualAppProviders.flatMap { it.getVirtualApps() }
                // === TELOS_PENDING_REVIEW_END: virtual_app_koin_fix ===

                if (query.isEmpty()) {
                    appResults.addAll(apps)
                    appResults.addAll(virtualApps)
                } else {
                    // === TELOS_PENDING_REVIEW_START: perf_optimizations ===
                    apps.mapNotNullTo(appResults) { app ->
                        val cachedLabel = app.cachedNormalizerResult
                        val score = ResultScore.from(
                            query = normalizedQuery,
                            primaryFields = listOf(
                                if (cachedLabel?.first == normalizerId) {
                                    cachedLabel.second
                                } else {
                                    stringNormalizer.normalize(app.label).also {
                                        app.cachedNormalizerResult = normalizerId to it
                                    }
                                }
                            ),
                        )
                        if (score.score < 0.8f) return@mapNotNullTo null
                        app.copy(
                            score = score
                        )
                    }
                    // === TELOS_PENDING_REVIEW_END: perf_optimizations ===

                    val componentName = ComponentName.unflattenFromString(query)
                    getActivityByComponentName(componentName)?.let { appResults.add(it) }

                    // Also search virtual apps
                    virtualApps.forEach { vApp ->
                        val score = ResultScore.from(
                            query = normalizedQuery,
                            primaryFields = listOf(stringNormalizer.normalize(vApp.label))
                        )
                        if (score.score >= 0.8f) {
                            appResults.add(vApp)
                        }
                    }
                }
                appResults.sortByDescending { it.score.score }
                appResults.toImmutableList()
            }
        }
    }

    private fun getActivityByComponentName(componentName: ComponentName?): LauncherApp? {
        componentName ?: return null
        val intent = Intent().setComponent(componentName)
        val lai = launcherApps.resolveActivity(intent, Process.myUserHandle())
        return lai?.let {
            LauncherApp(context, lai)
        }
    }
}