package de.mm20.launcher2.appmanagement

import android.content.Context
import java.io.File

/**
 * Storage Auto-Clean Helper for Telos Launcher and Telos Store.
 * Automatically purges temporary downloaded .apk installers from application cache
 * after successful installation or after 24 hours of inactivity.
 */
object StaleApkCleaner {

    /**
     * Deletes stale .apk installer files from context cache directory older than [maxAgeMs].
     */
    fun purgeStaleApks(context: Context, maxAgeMs: Long = 24 * 60 * 60 * 1000L): Long {
        var freedBytes = 0L
        val cacheDir = context.cacheDir ?: return 0L
        val now = System.currentTimeMillis()

        try {
            cacheDir.walkTopDown()
                .filter { it.isFile && (it.extension.equals("apk", true) || it.extension.equals("apks", true) || it.extension.equals("xapk", true)) }
                .forEach { file ->
                    if (now - file.lastModified() > maxAgeMs) {
                        val len = file.length()
                        if (file.delete()) {
                            freedBytes += len
                        }
                    }
                }
        } catch (e: Exception) {
            // Ignore non-fatal storage cleanup errors
        }

        return freedBytes
    }

    /**
     * Safely deletes a specific temporary installer file.
     */
    fun deleteInstallerFile(file: File?): Boolean {
        if (file == null || !file.exists()) return false
        return try {
            file.delete()
        } catch (e: Exception) {
            false
        }
    }
}
