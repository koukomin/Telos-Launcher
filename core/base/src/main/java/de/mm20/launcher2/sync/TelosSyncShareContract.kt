package de.mm20.launcher2.sync

import android.content.Context

/**
 * Universal Sync Share Contract for All Present & Future Telos Virtual Apps
 * (Telos Files, Viewer, Media, Downloads, Notes, Camera, Voice Recorder, Ermis).
 *
 * Ensures that any app or module with file system access automatically offers
 * "Copy Telos Sync Link" whenever Telos Sync is active.
 */
object TelosSyncShareContract {

    fun isSyncShareEnabled(context: Context): Boolean {
        val prefs = context.applicationContext.getSharedPreferences("telos_sync_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("sync_enabled", true)
    }

    fun copySyncShareLink(context: Context, filePath: String, folderId: String = "default"): Boolean {
        if (!isSyncShareEnabled(context) || filePath.isBlank()) return false
        TelosSyncLinkGenerator.shareFileOrFolderLink(context, filePath, folderId)
        return true
    }
}
