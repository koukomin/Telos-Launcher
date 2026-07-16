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
     * Copies the given video into app-private storage (so access survives permission loss and
     * file moves) and notifies a running wallpaper engine to reload. Returns false on I/O errors.
     *
     * Note that this does not activate the wallpaper: live wallpapers can only be activated by
     * the user through the system preview - use [getActivationIntent].
     */
    suspend fun setVideoWallpaper(uri: Uri): Boolean {
        return withContext(Dispatchers.IO) {
            val target = getVideoFile(context)
            val tmp = File(target.parentFile, "${target.name}.tmp")
            try {
                target.parentFile?.mkdirs()
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
        return getVideoFile(context).exists()
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
        internal fun getVideoFile(context: Context): File {
            return File(context.filesDir, "wallpapers/video")
        }
    }
}
