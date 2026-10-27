package de.mm20.launcher2.freeze

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

data class ShizukuSystemStatus(
    val isAndroid11OrHigher: Boolean,
    val isShizukuInstalled: Boolean,
    val isDeveloperOptionsEnabled: Boolean,
    val isWifiConnected: Boolean,
    val hasWriteSecureSettings: Boolean,
    val isWirelessDebuggingEnabled: Boolean,
    val isShizukuRunning: Boolean,
    val isShizukuAuthorized: Boolean,
)

object ShizukuBootManager {

    private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    private const val SHIZUKU_PROVIDER_AUTHORITY = "moe.shizuku.privileged.api.shizuku"

    @SuppressLint("MissingPermission")
    fun checkSystemStatus(context: Context): ShizukuSystemStatus {
        val cr = context.contentResolver
        val isAndroid11OrHigher = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

        val isShizukuInstalled = runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
            }
            true
        }.getOrDefault(false)

        val isDeveloperOptionsEnabled = Settings.Global.getInt(
            cr,
            Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
            0
        ) == 1

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNetwork = cm?.activeNetwork
        val caps = cm?.getNetworkCapabilities(activeNetwork)
        val isWifiConnected = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

        val hasWriteSecureSettings = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_SECURE_SETTINGS
        ) == PackageManager.PERMISSION_GRANTED

        val isWirelessDebuggingEnabled = Settings.Global.getInt(cr, "adb_wifi_enabled", 0) == 1

        val isShizukuRunning = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

        val isShizukuAuthorized = if (isShizukuRunning) {
            runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)
        } else false

        return ShizukuSystemStatus(
            isAndroid11OrHigher = isAndroid11OrHigher,
            isShizukuInstalled = isShizukuInstalled,
            isDeveloperOptionsEnabled = isDeveloperOptionsEnabled,
            isWifiConnected = isWifiConnected,
            hasWriteSecureSettings = hasWriteSecureSettings,
            isWirelessDebuggingEnabled = isWirelessDebuggingEnabled,
            isShizukuRunning = isShizukuRunning,
            isShizukuAuthorized = isShizukuAuthorized,
        )
    }

    suspend fun trySelfGrantWriteSecureSettings(context: Context): Boolean = withContext(Dispatchers.IO) {
        val status = checkSystemStatus(context)
        if (status.hasWriteSecureSettings) return@withContext true
        if (!status.isShizukuRunning || !status.isShizukuAuthorized) return@withContext false

        val pkg = context.packageName
        val cmd = arrayOf("pm", "grant", pkg, "android.permission.WRITE_SECURE_SETTINGS")
        runCatching {

            val process = de.mm20.launcher2.ktx.ShizukuProcesses.start(cmd)
            try { process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS) }
            finally { process.destroy() }
        }
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_SECURE_SETTINGS
        ) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun wakeAndTriggerShizuku(context: Context) = withContext(Dispatchers.IO) {
        // Stage 1 - Unstop Package via ContentProvider & Explicit Intent
        runCatching {
            val providerClient = context.contentResolver.acquireUnstableContentProviderClient(SHIZUKU_PROVIDER_AUTHORITY)
            if (Build.VERSION.SDK_INT >= 24) {
                providerClient?.close()
            } else {
                @Suppress("DEPRECATION")
                providerClient?.release()
            }
        }

        val broadcastIntent = Intent("$SHIZUKU_PACKAGE.intent.action.START_SERVICE").apply {
            setPackage(SHIZUKU_PACKAGE)
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        }
        runCatching { context.sendBroadcast(broadcastIntent) }

        // Stage 2 - Service Poke
        runCatching {
            val pm = context.packageManager
            val serviceIntent = Intent("$SHIZUKU_PACKAGE.intent.action.START_SERVICE").apply {
                setPackage(SHIZUKU_PACKAGE)
                addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            }
            val resolvedServices = if (Build.VERSION.SDK_INT >= 33) {
                pm.queryIntentServices(serviceIntent, PackageManager.ResolveInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentServices(serviceIntent, 0)
            }

            for (resolveInfo in resolvedServices) {
                val serviceComponent = ComponentName(
                    resolveInfo.serviceInfo.packageName,
                    resolveInfo.serviceInfo.name
                )
                val pokeIntent = Intent().apply {
                    component = serviceComponent
                    addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                }
                runCatching { ContextCompat.startForegroundService(context, pokeIntent) }
            }
        }

        // Poll for Shizuku binder for up to 4 seconds
        for (step in 0 until 4) {
            delay(1000)
            if (runCatching { Shizuku.pingBinder() }.getOrDefault(false)) {
                return@withContext
            }
        }

        // Stage 3 - Fallback Silent Activity Launch (if Shizuku.pingBinder() is still false after 4 seconds)
        val launchIntent = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        }
        if (launchIntent != null) {
            runCatching { context.startActivity(launchIntent) }
            delay(1500)

            // Bring Launcher back to the front
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
            runCatching { context.startActivity(homeIntent) }
        }
    }

    suspend fun verifyAndStartShizuku(context: Context, isBootTrigger: Boolean): Result<String> = withContext(Dispatchers.IO) {
        if (isBootTrigger) {
            var wifiReady = false
            for (step in 0 until 15) {
                val status = checkSystemStatus(context)
                if (status.isWifiConnected) {
                    wifiReady = true
                    break
                }
                delay(2000)
            }
            if (!wifiReady) {
                return@withContext Result.failure(Exception("Wi-Fi is not connected. Wireless Debugging requires an active Wi-Fi connection."))
            }
        } else {
            val status = checkSystemStatus(context)
            if (!status.isWifiConnected) {
                return@withContext Result.failure(Exception("Wi-Fi is not connected. Please connect to a Wi-Fi network first."))
            }
        }

        var hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_SECURE_SETTINGS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            hasPermission = trySelfGrantWriteSecureSettings(context)
        }

        if (!hasPermission) {
            return@withContext Result.failure(Exception("Missing WRITE_SECURE_SETTINGS permission. Grant it via Shizuku or ADB."))
        }

        val cr = context.contentResolver
        try {
            Settings.Global.putInt(cr, Settings.Global.ADB_ENABLED, 1)
            Settings.Global.putInt(cr, "adb_wifi_enabled", 1)
        } catch (e: SecurityException) {
            return@withContext Result.failure(Exception("SecurityException when modifying Wireless Debugging: ${e.message}"))
        }

        val verified = Settings.Global.getInt(cr, "adb_wifi_enabled", 0) == 1
        if (!verified) {
            return@withContext Result.failure(Exception("The OS blocked or reset adb_wifi_enabled. Wireless Debugging configuration failed."))
        }

        if (runCatching { Shizuku.pingBinder() }.getOrDefault(false)) {
            return@withContext Result.success("Verified: Wireless Debugging manipulation works and Shizuku is active!")
        }

        // Execute Multi-Stage Wake-Up Mechanism
        wakeAndTriggerShizuku(context)

        if (runCatching { Shizuku.pingBinder() }.getOrDefault(false)) {
            Result.success("Verified: Shizuku daemon started successfully!")
        } else {
            Result.failure(Exception(context.getString(de.mm20.launcher2.i18n.R.string.desktop_mode_freeform_shizuku_unavailable)))
        }
    }
}
