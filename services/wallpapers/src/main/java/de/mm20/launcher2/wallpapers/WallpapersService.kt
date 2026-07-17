package de.mm20.launcher2.wallpapers

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import de.mm20.launcher2.crashreporter.CrashReporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

enum class StaticWallpaperTarget {
    Home,
    Lock,
    Both,
}

class WallpapersService(private val context: Context) {

    /**
     * Sets a static image wallpaper for the given target. Home and lock screen can be set
     * independently ([WallpaperManager.FLAG_SYSTEM] / [WallpaperManager.FLAG_LOCK]).
     */
    suspend fun setStaticWallpaper(uri: Uri, target: StaticWallpaperTarget): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val flags = when (target) {
                        StaticWallpaperTarget.Home -> WallpaperManager.FLAG_SYSTEM
                        StaticWallpaperTarget.Lock -> WallpaperManager.FLAG_LOCK
                        StaticWallpaperTarget.Both ->
                            WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
                    }
                    WallpaperManager.getInstance(context).setStream(stream, null, true, flags)
                    true
                } == true
            } catch (e: Exception) {
                CrashReporter.logException(e)
                false
            }
        }
    }

    /**
     * Copies the given video into app-private storage and notifies a running wallpaper engine
     * to reload. If [appendToPlaylist] is true, adds it to the list instead of replacing.
     */
    suspend fun setVideoWallpaper(uri: Uri, appendToPlaylist: Boolean = false): Boolean {
        return withContext(Dispatchers.IO) {
            val dir = getVideoDir(context)
            dir.mkdirs()
            
            val filename = if (appendToPlaylist) "video_${System.currentTimeMillis()}" else "video"
            val target = File(dir, filename)
            
            if (!appendToPlaylist) {
                // Clear existing videos if not appending
                dir.listFiles()?.forEach { it.delete() }
            }
            
            val tmp = File(dir, "$filename.tmp")
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tmp.outputStream().use { output ->
                        input.copyTo(output)
                    }
                } ?: return@withContext false
                if (!tmp.renameTo(target)) return@withContext false
                VideoWallpaperService.videoChanged.tryEmit(Unit)
                true
            } catch (e: Exception) {
                CrashReporter.logException(e)
                tmp.delete()
                false
            }
        }
    }

    fun hasVideoWallpaper(): Boolean {
        return getVideoDir(context).listFiles()?.isNotEmpty() == true ||
                @Suppress("DEPRECATION") getVideoFile(context).exists()
    }

    /** True if our video wallpaper service is the currently active system live wallpaper. */
    fun isVideoWallpaperActive(): Boolean {
        val info = WallpaperManager.getInstance(context).wallpaperInfo ?: return false
        return info.packageName == context.packageName &&
                info.serviceName == VideoWallpaperService::class.java.name
    }

    /**
     * Opens the system live-wallpaper preview for our service. Activation always requires the
     * user to confirm there; there is no API for apps to set a live wallpaper directly.
     */
    fun getActivationIntent(): Intent {
        return Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
            putExtra(
                WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                ComponentName(context, VideoWallpaperService::class.java),
            )
        }
    }

    companion object {
        internal fun getVideoDir(context: Context): File {
            return File(context.filesDir, "wallpapers/video_playlist")
        }

        @Deprecated("Use getVideoDir")
        internal fun getVideoFile(context: Context): File {
            return File(context.filesDir, "wallpapers/video")
        }
    }
}
