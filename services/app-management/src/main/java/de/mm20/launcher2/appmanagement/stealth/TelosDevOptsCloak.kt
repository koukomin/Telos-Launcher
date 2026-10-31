package de.mm20.launcher2.appmanagement.stealth

import android.content.Context
import android.provider.Settings
import android.util.Log
import de.mm20.launcher2.coroutines.TelosDispatchers
import de.mm20.launcher2.security.AppLaunchGate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Hides Developer Options / ADB flags from blacklisted apps. A shell UID 2000
 * daemon already in memory keeps working after these globals are set to 0.
 */
object TelosDevOptsCloak {
    private const val TAG = "TelosDevOptsCloak"
    private const val PREFS = "telos_stealth_cloak"
    private const val KEY_PERMANENT = "permanent_stealth"
    private const val KEY_HIDE_SHIZUKU = "hide_shizuku_on_cloak"
    private const val KEY_SAVED_DEV = "saved_development_settings"
    private const val KEY_SAVED_ADB = "saved_adb_enabled"
    private const val KEY_SAVED_WIFI = "saved_adb_wifi_enabled"
    private const val KEY_CLOAKED = "currently_cloaked"

    private val scope = CoroutineScope(SupervisorJob() + TelosDispatchers.Disk)

    private val _status = MutableStateFlow(CloakStatus())
    val status: StateFlow<CloakStatus> = _status.asStateFlow()

    data class CloakStatus(
        val writeSecureSettings: Boolean = false,
        val developmentEnabled: Int = -1,
        val adbEnabled: Int = -1,
        val adbWifiEnabled: Int = -1,
        val permanent: Boolean = false,
        val hideShizuku: Boolean = true,
        val mode: TelosPrivilegedDaemon.Mode = TelosPrivilegedDaemon.Mode.None,
        val externalShizukuInstalled: Boolean = false,
    )

    fun start(context: Context) {
        val app = context.applicationContext
        TelosPrivilegedDaemon.warmup(app)
        TelosStealthBlacklist.start(app)
        AppLaunchGate.preLaunch = { ctx, pkg ->
            cloakIfNeeded(ctx, pkg)
            de.mm20.launcher2.appmanagement.clipboard.TelosClipboardShield.onPreLaunch(ctx, pkg)
        }
        AppLaunchGate.onHomeResumed = { ctx ->
            restoreIfNeeded(ctx)
            de.mm20.launcher2.appmanagement.clipboard.TelosClipboardShield.onHomeResumed(ctx)
        }
        de.mm20.launcher2.appmanagement.clipboard.TelosClipboardShield.start(app)
        scope.launch {
            TelosPrivilegedDaemon.grantWriteSecureSettings(app)
            if (isPermanent(app)) applyHidden(app, persistSaved = false)
            refreshStatus(app)
        }
    }

    fun isPermanent(context: Context) = prefs(context).getBoolean(KEY_PERMANENT, false)
    fun hideShizukuOnCloak(context: Context) = prefs(context).getBoolean(KEY_HIDE_SHIZUKU, true)

    fun setPermanent(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_PERMANENT, enabled).apply()
        if (enabled) cloakIfNeeded(context, force = true)
        else restoreIfNeeded(context, force = true)
        scope.launch { refreshStatus(context) }
    }

    fun setHideShizukuOnCloak(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_HIDE_SHIZUKU, enabled).apply()
        scope.launch { refreshStatus(context) }
    }

    fun cloakIfNeeded(context: Context, packageName: String = "", force: Boolean = false) {
        val app = context.applicationContext
        if (!force && packageName.isNotEmpty() && !TelosStealthBlacklist.isProtected(app, packageName)) {
            return
        }
        applyHidden(app, persistSaved = true)
        if (hideShizukuOnCloak(app) && TelosPrivilegedDaemon.isExternalShizukuInstalled(app)) {
            scope.launch { TelosPrivilegedDaemon.hideExternalShizuku(app) }
        }
        refreshStatusSync(app)
    }

    fun restoreIfNeeded(context: Context, force: Boolean = false) {
        val app = context.applicationContext
        if (!force && isPermanent(app)) {
            applyHidden(app, persistSaved = false)
            refreshStatusSync(app)
            return
        }
        val p = prefs(app)
        if (!p.getBoolean(KEY_CLOAKED, false) && !force) {
            refreshStatusSync(app)
            return
        }
        val dev = p.getInt(KEY_SAVED_DEV, 1)
        val adb = p.getInt(KEY_SAVED_ADB, 1)
        val wifi = p.getInt(KEY_SAVED_WIFI, 1)
        writeGlobals(app, dev, adb, wifi)
        p.edit().putBoolean(KEY_CLOAKED, false).apply()
        refreshStatusSync(app)
    }

    fun refreshStatus(context: Context) {
        refreshStatusSync(context.applicationContext)
    }

    private fun applyHidden(context: Context, persistSaved: Boolean) {
        val p = prefs(context)
        if (persistSaved && !p.getBoolean(KEY_CLOAKED, false)) {
            p.edit()
                .putInt(KEY_SAVED_DEV, readGlobal(context, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0))
                .putInt(KEY_SAVED_ADB, readGlobal(context, Settings.Global.ADB_ENABLED, 0))
                .putInt(KEY_SAVED_WIFI, readGlobal(context, "adb_wifi_enabled", 0))
                .putBoolean(KEY_CLOAKED, true)
                .apply()
        }
        writeGlobals(context, 0, 0, 0)
    }

    private fun writeGlobals(context: Context, development: Int, adb: Int, wifi: Int) {
        if (TelosPrivilegedDaemon.hasWriteSecureSettings(context)) {
            runCatching {
                Settings.Global.putInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, development)
                Settings.Global.putInt(context.contentResolver, Settings.Global.ADB_ENABLED, adb)
                Settings.Global.putInt(context.contentResolver, "adb_wifi_enabled", wifi)
            }.onFailure { Log.w(TAG, "Settings.Global write failed", it) }
            return
        }
        scope.launch {
            TelosPrivilegedDaemon.exec(
                "settings put global development_settings_enabled $development; " +
                    "settings put global adb_enabled $adb; " +
                    "settings put global adb_wifi_enabled $wifi",
            )
        }
    }

    private fun refreshStatusSync(context: Context) {
        _status.value = CloakStatus(
            writeSecureSettings = TelosPrivilegedDaemon.hasWriteSecureSettings(context),
            developmentEnabled = readGlobal(context, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, -1),
            adbEnabled = readGlobal(context, Settings.Global.ADB_ENABLED, -1),
            adbWifiEnabled = readGlobal(context, "adb_wifi_enabled", -1),
            permanent = isPermanent(context),
            hideShizuku = hideShizukuOnCloak(context),
            mode = TelosPrivilegedDaemon.currentMode(context),
            externalShizukuInstalled = TelosPrivilegedDaemon.isExternalShizukuInstalled(context),
        )
    }

    private fun readGlobal(context: Context, name: String, default: Int): Int {
        return runCatching { Settings.Global.getInt(context.contentResolver, name) }.getOrDefault(default)
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
