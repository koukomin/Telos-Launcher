package de.mm20.launcher2.appmanagement.shizuku

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import de.mm20.launcher2.appmanagement.stealth.TelosPrivilegedDaemon
import de.mm20.launcher2.coroutines.TelosDispatchers
import de.mm20.launcher2.ktx.ShizukuProcesses
import de.mm20.launcher2.ktx.TelosShellEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.util.Locale

data class ShizukuEngineStatus(
    val binderAlive: Boolean = false,
    val authorized: Boolean = false,
    val uid: Int = -1,
    val version: Int = -1,
    val seLinuxContext: String = "",
    val internalUserService: Boolean = false,
    val externalInstalled: Boolean = false,
    val externalEnabled: Boolean = false,
    val mode: TelosPrivilegedDaemon.Mode = TelosPrivilegedDaemon.Mode.None,
)

data class ShizukuClientApp(
    val packageName: String,
    val label: String,
    val apiV23: Boolean,
    val v3Metadata: Boolean,
    val granted: Boolean,
    val enabled: Boolean,
)

object TelosShizukuManager {
    private const val TAG = "TelosShizukuManager"
    const val SHIZUKU_PACKAGE = TelosPrivilegedDaemon.SHIZUKU_PACKAGE
    const val PERM_MANAGER_V23 = "moe.shizuku.manager.permission.API_V23"
    const val PERM_RIKKA_V23 = "rikka.shizuku.permission.API_V23"
    const val META_V3 = "moe.shizuku.client.V3_SUPPORT"

    val PRIVILEGED_PERMS = listOf(
        "android.permission.WRITE_SECURE_SETTINGS",
        "android.permission.DUMP",
        "android.permission.PACKAGE_USAGE_STATS",
    )

    private val SAFE_PKG = Regex("^[a-zA-Z0-9._]+$")

    private val _status = MutableStateFlow(ShizukuEngineStatus())
    val status: StateFlow<ShizukuEngineStatus> = _status.asStateFlow()

    private val _clients = MutableStateFlow<List<ShizukuClientApp>>(emptyList())
    val clients: StateFlow<List<ShizukuClientApp>> = _clients.asStateFlow()

    fun refreshStatus(context: Context) {
        val app = context.applicationContext
        ShizukuProcesses.init(app)
        val binder = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        val authorized = binder && runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        val uid = if (binder) runCatching { Shizuku.getUid() }.getOrDefault(-1) else -1
        val version = if (binder) runCatching { Shizuku.getVersion() }.getOrDefault(-1) else -1
        val seLinux = if (binder) {
            runCatching {
                Shizuku::class.java.getMethod("getSELinuxContext").invoke(null) as? String
            }.getOrNull().orEmpty()
        } else {
            ""
        }
        val installed = TelosPrivilegedDaemon.isExternalShizukuInstalled(app)
        val enabled = installed && runCatching {
            app.packageManager.getApplicationInfo(SHIZUKU_PACKAGE, 0).enabled
        }.getOrDefault(false)
        _status.value = ShizukuEngineStatus(
            binderAlive = binder,
            authorized = authorized,
            uid = uid,
            version = version,
            seLinuxContext = seLinux,
            internalUserService = ShizukuProcesses.available(),
            externalInstalled = installed,
            externalEnabled = enabled,
            mode = TelosPrivilegedDaemon.currentMode(app),
        )
    }

    suspend fun scanClientApps(context: Context): List<ShizukuClientApp> = withContext(TelosDispatchers.Disk) {
        val app = context.applicationContext
        val pm = app.packageManager
        val flags = PackageManager.GET_PERMISSIONS or PackageManager.GET_META_DATA
        val packages = runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledPackages(flags)
            }
        }.getOrDefault(emptyList())
        val rows = ArrayList<ShizukuClientApp>(16)
        for (info in packages) {
            val pkg = info.packageName ?: continue
            if (pkg == app.packageName || pkg == SHIZUKU_PACKAGE) continue
            val api = requestsShizukuPermission(info)
            val meta = hasV3Metadata(info.applicationInfo)
            if (!api && !meta) continue
            val label = runCatching {
                info.applicationInfo?.loadLabel(pm)?.toString()
            }.getOrDefault(pkg)
            val granted = permissionGranted(pm, pkg)
            val enabled = info.applicationInfo?.enabled != false
            rows.add(
                ShizukuClientApp(
                    packageName = pkg,
                    label = label.orEmpty().ifBlank { pkg },
                    apiV23 = api,
                    v3Metadata = meta,
                    granted = granted,
                    enabled = enabled,
                ),
            )
        }
        rows.sortBy { it.label.lowercase(Locale.ROOT) }
        _clients.value = rows
        rows
    }

    suspend fun setClientAccess(context: Context, packageName: String, allow: Boolean): Boolean {
        if (!SAFE_PKG.matches(packageName)) return false
        val perms = listOf(PERM_MANAGER_V23, PERM_RIKKA_V23)
        val verb = if (allow) "grant" else "revoke"
        var ok = true
        for (perm in perms) {
            val r = TelosPrivilegedDaemon.exec(
                "pm $verb $packageName $perm",
            )
            if (!r.isSuccess) {
                Log.w(TAG, "pm $verb $packageName $perm: ${r.stderr}")
                ok = false
            }
            val mode = if (allow) "allow" else "ignore"
            TelosPrivilegedDaemon.exec("cmd appops set $packageName $perm $mode")
        }
        scanClientApps(context)
        return ok
    }

    suspend fun grantPrivilegedPerms(context: Context, packageName: String, grant: Boolean): Boolean {
        if (!SAFE_PKG.matches(packageName)) return false
        val verb = if (grant) "grant" else "revoke"
        var ok = true
        for (perm in PRIVILEGED_PERMS) {
            val r = TelosShellEngine.execArgsResult(
                arrayOf("pm", verb, packageName, perm),
                timeoutMs = 10_000L,
            )
            if (!r.isSuccess) ok = false
        }
        refreshStatus(context)
        return ok
    }

    suspend fun setExternalShizukuEnabled(context: Context, enabled: Boolean): Boolean {
        val args = if (enabled) {
            arrayOf("pm", "enable", SHIZUKU_PACKAGE)
        } else {
            arrayOf("pm", "disable-user", "--user", "0", SHIZUKU_PACKAGE)
        }
        val r = TelosShellEngine.execArgsResult(args, timeoutMs = 10_000L)
        if (enabled) {
            TelosShellEngine.execArgsResult(arrayOf("pm", "unhide", SHIZUKU_PACKAGE), timeoutMs = 8_000L)
        }
        refreshStatus(context)
        return r.isSuccess
    }

    suspend fun freezeExternalShizuku(context: Context): Boolean {
        return TelosPrivilegedDaemon.hideExternalShizuku(context).also { refreshStatus(context) }
    }

    suspend fun forceStop(packageName: String): Boolean {
        if (!SAFE_PKG.matches(packageName)) return false
        return TelosShellEngine.execArgsResult(
            arrayOf("am", "force-stop", packageName),
            timeoutMs = 8_000L,
        ).isSuccess
    }

    private fun requestsShizukuPermission(info: PackageInfo): Boolean {
        val requested = info.requestedPermissions ?: return false
        return requested.any { it == PERM_MANAGER_V23 || it == PERM_RIKKA_V23 }
    }

    private fun hasV3Metadata(ai: ApplicationInfo?): Boolean {
        val bundle = ai?.metaData ?: return false
        return bundle.getBoolean(META_V3, false) || bundle.containsKey(META_V3)
    }

    private fun permissionGranted(pm: PackageManager, packageName: String): Boolean {
        val a = runCatching { pm.checkPermission(PERM_MANAGER_V23, packageName) }.getOrDefault(PackageManager.PERMISSION_DENIED)
        val b = runCatching { pm.checkPermission(PERM_RIKKA_V23, packageName) }.getOrDefault(PackageManager.PERMISSION_DENIED)
        return a == PackageManager.PERMISSION_GRANTED || b == PackageManager.PERMISSION_GRANTED
    }
}
