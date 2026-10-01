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
            val method = Shizuku::class.java.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
            method.isAccessible = true
            val process = method.invoke(null, arrayOf("sh", "-c", command), null, null)
            val waitForMethod = process::class.java.getMethod("waitFor")
            waitForMethod.invoke(process) == 0
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
            val method = Shizuku::class.java.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
            method.isAccessible = true
            val process = method.invoke(null, arrayOf("sh", "-c", command), null, null)
            val waitForMethod = process::class.java.getMethod("waitFor")
            waitForMethod.invoke(process) == 0
        } catch (e: Exception) {
            Log.e("DesktopWindowManager", "Failed to close task $taskId", e)
            false
        }
    }
}
// === TELOS_PENDING_REVIEW_END: desktop_window_snapping ===
