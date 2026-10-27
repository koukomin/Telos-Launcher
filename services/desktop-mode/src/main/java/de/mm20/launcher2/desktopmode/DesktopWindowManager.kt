// === TELOS_PENDING_REVIEW_START: desktop_window_snapping ===
package de.mm20.launcher2.desktopmode

import android.graphics.Rect
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import android.content.pm.PackageManager

class DesktopWindowManager {

    private fun hasShizukuPermission(): Boolean {
        return try {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    suspend fun snapTask(taskId: Int, bounds: Rect): Boolean = withContext(Dispatchers.IO) {
        if (!hasShizukuPermission()) {
            Log.e("DesktopWindowManager", "Shizuku permission not granted")
            return@withContext false
        }
        try {
            val command = "cmd activity task resize $taskId ${bounds.left} ${bounds.top} ${bounds.right} ${bounds.bottom}"

            val process = de.mm20.launcher2.ktx.ShizukuProcesses.start(arrayOf("sh", "-c", command))
            try { process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS) && process.exitValue() == 0 }
            finally { process.destroy() }
        } catch (e: Exception) {
            Log.e("DesktopWindowManager", "Failed to resize task $taskId", e)
            false
        }
    }

    suspend fun closeTask(taskId: Int): Boolean = withContext(Dispatchers.IO) {
        if (!hasShizukuPermission()) {
            Log.e("DesktopWindowManager", "Shizuku permission not granted")
            return@withContext false
        }
        try {
            // Note: 'am force-stop' expects package name, to close a task we can try 'am task rm' if it exists.
            // On recent Android versions, 'am task rm <taskId>' might remove the task.
            // Using 'cmd activity remove-task <taskId>' is more reliable.
            val command = "cmd activity remove-task $taskId"

            val process = de.mm20.launcher2.ktx.ShizukuProcesses.start(arrayOf("sh", "-c", command))
            try { process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS) && process.exitValue() == 0 }
            finally { process.destroy() }
        } catch (e: Exception) {
            Log.e("DesktopWindowManager", "Failed to close task $taskId", e)
            false
        }
    }
}
// === TELOS_PENDING_REVIEW_END: desktop_window_snapping ===
