package de.mm20.launcher2.appmanagement.shizuku

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

object ShizukuWirelessPairing {
    private const val TAG = "ShizukuWirelessPairing"

    @androidx.annotation.ChecksSdkIntAtLeast(api = Build.VERSION_CODES.R)
    fun isSupported(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    }

    fun isShizukuRunning(): Boolean {
        return runCatching { Shizuku.pingBinder() }.getOrDefault(false)
    }

    suspend fun startShizukuService(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (isShizukuRunning()) return@withContext true
        try {
            // Execute starter via local shell if root is available
            val result = de.mm20.launcher2.ktx.PrivilegedShell.run("sh /sdcard/Android/data/moe.shizuku.privileged.api/starter.sh")
            result?.exitCode == 0 && isShizukuRunning()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to execute local Shizuku starter", e)
            false
        }
    }
}
