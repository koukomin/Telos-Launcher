package de.mm20.launcher2.network.impl

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import androidx.core.content.ContextCompat
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.network.api.AppDirectory
import de.mm20.launcher2.network.api.AppEntry
import de.mm20.launcher2.network.api.FlowInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Reads the installed packages from the PackageManager and follows install, update and uninstall. */
internal class DefaultAppDirectory(private val context: Context) : AppDirectory {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    override val apps: StateFlow<List<AppEntry>> = _apps

    @Volatile
    private var byAppId: Map<Int, AppEntry> = emptyMap()

    @Volatile
    private var byPackageName: Map<String, AppEntry> = emptyMap()

    override val ownAppId: Int = Process.myUid() % FlowInfo.PER_USER_RANGE

    init {
        scope.launch { refresh() }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                scope.launch { refresh() }
            }
        }
        try {
            ContextCompat.registerReceiver(context.applicationContext, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        } catch (e: Exception) {
            // the list is then only refreshed on demand
        }
    }

    override fun byUid(uid: Int): AppEntry? {
        if (uid < 0) return null
        return byAppId[uid % FlowInfo.PER_USER_RANGE]
    }

    override fun byPackage(packageName: String): AppEntry? = byPackageName[packageName]

    override fun labelFor(uid: Int): String {
        byUid(uid)?.let { return it.label }
        if (uid < 0) return context.getString(I18nR.string.net_app_unknown, uid)
        val appId = uid % FlowInfo.PER_USER_RANGE
        return if (appId < FIRST_APPLICATION_UID) {
            context.getString(I18nR.string.net_app_system)
        } else {
            context.packageManager.getNameForUid(uid) ?: context.getString(I18nR.string.net_app_unknown, uid)
        }
    }

    override suspend fun refresh() {
        mutex.withLock {
            val list = try {
                load()
            } catch (e: Exception) {
                return
            }
            byAppId = list.associateBy { it.appId }
            byPackageName = buildMap { list.forEach { e -> e.packages.forEach { put(it, e) } } }
            _apps.value = list
        }
    }

    @Suppress("DEPRECATION")
    private fun load(): List<AppEntry> {
        val pm = context.packageManager
        val packages: List<PackageInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
        } else {
            pm.getInstalledPackages(0)
        }
        return packages
            .mapNotNull { info -> info.applicationInfo?.let { info to it } }
            .groupBy { (_, app) -> app.uid % FlowInfo.PER_USER_RANGE }
            .map { (appId, group) ->
                val (info, app) = group.first()
                val hasInternet = group.any { (i, _) ->
                    pm.checkPermission(android.Manifest.permission.INTERNET, i.packageName) == PackageManager.PERMISSION_GRANTED
                }
                AppEntry(
                    appId = appId,
                    packageName = info.packageName,
                    label = runCatching { pm.getApplicationLabel(app).toString() }.getOrDefault(info.packageName),
                    packages = group.map { it.first.packageName },
                    isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    hasInternet = hasInternet,
                    installedAtMs = group.minOf { it.first.firstInstallTime },
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    private companion object {
        /** Uids below this belong to the system, not to installed apps. */
        const val FIRST_APPLICATION_UID = 10000
    }
}
