package de.mm20.launcher2.appmanagement.stealth

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import de.mm20.launcher2.coroutines.TelosDispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Snapshots Developer Options–related [Settings.Global] / [Settings.System] values so they
 * survive `development_settings_enabled=0` (manual disable, stealth cloak, or OEM clear).
 *
 * Android and many OEMs drop or ignore per-option values while Developer Options is off;
 * restoring the master flag alone does not bring animator scales, stay-awake, etc. back.
 */
internal object TelosDevOptsSnapshot {
    private const val TAG = "TelosDevOptsSnapshot"
    private const val PREFS = "telos_stealth_cloak"
    private const val KEY_JSON = "saved_devopts_snapshot_json"
    private const val KEY_HAD_ENABLED = "snapshot_had_dev_enabled"

    /** Curated keys commonly lost when Developer Options is turned off. */
    val GLOBAL_KEYS: List<String> = listOf(
        Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
        Settings.Global.ADB_ENABLED,
        "adb_wifi_enabled",
        Settings.Global.STAY_ON_WHILE_PLUGGED_IN,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        Settings.Global.TRANSITION_ANIMATION_SCALE,
        Settings.Global.WINDOW_ANIMATION_SCALE,
        Settings.Global.ALWAYS_FINISH_ACTIVITIES,
        "wait_for_debugger",
        "debug_app",
        "force_allow_on_external",
        "enable_freeform_support",
        "force_resizable_activities",
        "overlay_display_devices",
        "mobile_data_always_on",
        "wifi_display_on",
        "art_verifier_verify_debuggable",
        "verifier_verify_adb_installs",
        "package_verifier_enable",
        "upload_apk_enable",
        "enable_gpu_debug_layers",
        "gpu_debug_app",
        "gpu_debug_layers",
        "angle_gl_driver_selection_values",
        "angle_gl_driver_selection_pkgs",
        "debug_view_attributes",
        "show_first_crash_dialog",
        "force_desktop_mode_on_external_displays",
        "enable_non_resizable_multi_window",
    )

    val SYSTEM_KEYS: List<String> = listOf(
        "show_touches",
        "pointer_location",
        "show_key_presses",
    )

    private val scope = CoroutineScope(SupervisorJob() + TelosDispatchers.Disk)

    @Volatile
    private var observerRegistered = false

    fun ensureObserver(context: Context) {
        if (observerRegistered) return
        val app = context.applicationContext
        val handler = Handler(Looper.getMainLooper())
        val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                onDevelopmentFlagChanged(app)
            }
        }
        runCatching {
            app.contentResolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED),
                false,
                observer,
            )
            observerRegistered = true
        }.onFailure { Log.w(TAG, "DevOpts observer not registered", it) }
        // Seed snapshot while options are currently on.
        if (readGlobal(app, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1) {
            capture(app)
        }
    }

    /** Capture current values (call while Developer Options is still enabled when possible). */
    fun capture(context: Context) {
        val app = context.applicationContext
        val json = JSONObject()
        GLOBAL_KEYS.forEach { key ->
            readGlobalRaw(app, key)?.let { json.put("g:$key", it) }
        }
        SYSTEM_KEYS.forEach { key ->
            readSystemRaw(app, key)?.let { json.put("s:$key", it) }
        }
        if (json.length() == 0) return
        prefs(app).edit()
            .putString(KEY_JSON, json.toString())
            .putBoolean(KEY_HAD_ENABLED, readGlobal(app, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1)
            .apply()
    }

    /** Restore every captured key via Settings APIs or privileged shell. */
    fun restore(context: Context, writeSecureSettings: Boolean, execShell: (String) -> Unit) {
        val app = context.applicationContext
        val raw = prefs(app).getString(KEY_JSON, null) ?: return
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return
        val shellParts = ArrayList<String>()
        json.keys().forEach { compound ->
            val value = json.optString(compound, "")
            if (value.isEmpty() && !json.has(compound)) return@forEach
            when {
                compound.startsWith("g:") -> {
                    val key = compound.removePrefix("g:")
                    if (writeSecureSettings) {
                        runCatching { Settings.Global.putString(app.contentResolver, key, value) }
                            .onFailure { Log.w(TAG, "Global restore failed for $key", it) }
                    } else {
                        shellParts += "settings put global ${shellQuote(key)} ${shellQuote(value)}"
                    }
                }
                compound.startsWith("s:") -> {
                    val key = compound.removePrefix("s:")
                    if (writeSecureSettings) {
                        runCatching { Settings.System.putString(app.contentResolver, key, value) }
                            .onFailure { Log.w(TAG, "System restore failed for $key", it) }
                    } else {
                        shellParts += "settings put system ${shellQuote(key)} ${shellQuote(value)}"
                    }
                }
            }
        }
        if (shellParts.isNotEmpty()) {
            execShell(shellParts.joinToString("; "))
        }
    }

    fun hasSnapshot(context: Context): Boolean =
        !prefs(context).getString(KEY_JSON, null).isNullOrBlank()

    private fun onDevelopmentFlagChanged(context: Context) {
        val enabled = readGlobal(context, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
        val prefs = prefs(context)
        val wasEnabled = prefs.getBoolean(KEY_HAD_ENABLED, false)
        if (enabled) {
            if (!wasEnabled && hasSnapshot(context)) {
                // Mark enabled first so nested Settings writes do not re-enter restore.
                prefs.edit().putBoolean(KEY_HAD_ENABLED, true).apply()
                // Re-enabled after disable: put back animator scales / stay-awake / etc.
                restore(
                    context,
                    writeSecureSettings = TelosPrivilegedDaemon.hasWriteSecureSettings(context),
                ) { cmd ->
                    scope.launch { TelosPrivilegedDaemon.exec(cmd) }
                }
                Log.i(TAG, "Developer Options re-enabled; restored snapshot")
            }
            capture(context)
        } else if (wasEnabled) {
            // Just flipped off — keep the last capture taken while enabled; mark state.
            prefs.edit().putBoolean(KEY_HAD_ENABLED, false).apply()
            Log.i(TAG, "Developer Options disabled; retaining ${if (hasSnapshot(context)) "snapshot" else "no snapshot"}")
        }
    }

    private fun readGlobal(context: Context, name: String, default: Int): Int =
        runCatching { Settings.Global.getInt(context.contentResolver, name) }.getOrDefault(default)

    private fun readGlobalRaw(context: Context, name: String): String? =
        runCatching { Settings.Global.getString(context.contentResolver, name) }.getOrNull()

    private fun readSystemRaw(context: Context, name: String): String? =
        runCatching { Settings.System.getString(context.contentResolver, name) }.getOrNull()

    private fun shellQuote(value: String): String =
        "'" + value.replace("'", "'\\''") + "'"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
