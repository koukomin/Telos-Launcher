package de.mm20.launcher2.search

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.app.NotificationCompat
import de.mm20.launcher2.base.R

/**
 * Recents and notifications of built-in apps must show that app's icon, not the Telos launcher icon.
 * Recents only reliably picks up a [Bitmap] [ActivityManager.TaskDescription]; a resource id is
 * ignored or crashes on older APIs and with vector drawables.
 */
object VirtualAppAppearance {

    @DrawableRes
    fun glyph(domain: String): Int? = when (domain) {
        "telos_phone_app" -> R.drawable.ic_app_phone_fg
        "telos_messages_app", "telos_ermis_app" -> R.drawable.ic_app_messages_fg
        "telos_radio_app" -> R.drawable.ic_app_radio_fg
        "telos_music_app" -> R.drawable.ic_app_music_fg
        "telos_youtube_app" -> R.drawable.ic_app_youtube_fg
        "telos_youtube_music_app" -> R.drawable.ic_app_youtube_music_fg
        "telos_video_app" -> R.drawable.ic_app_video_fg
        "telos_photos_app" -> R.drawable.ic_app_photos_fg
        "telos_viewer_app" -> R.drawable.ic_app_viewer_fg
        "telos_files_app" -> R.drawable.ic_app_files_fg
        "telos_calculator_app" -> R.drawable.ic_app_calculator_fg
        "telos_voice_recorder_app" -> R.drawable.ic_app_voice_recorder_fg
        "telos_camera_app" -> R.drawable.ic_app_camera_fg
        "telos_screen_recorder_app" -> R.drawable.ic_app_screen_recorder_fg
        "telos_screenshot_app" -> R.drawable.ic_app_screenshot_fg
        "telos_notes_app" -> R.drawable.ic_app_notes_fg
        "telos_calendar_app" -> R.drawable.ic_app_calendar_fg
        "telos_downloads_app" -> R.drawable.ic_app_downloads_fg
        "telos_network_app" -> R.drawable.ic_app_network_fg
        "telos_system_app" -> R.drawable.ic_app_system_fg
        "telos_media_app" -> R.drawable.ic_app_media_fg
        "telos_store_app" -> R.drawable.ic_app_store_fg
        "telos_akou_app" -> R.drawable.ic_app_akou_fg
        "telos_sync_app" -> R.drawable.ic_app_sync_fg
        "telos_backup_app" -> R.drawable.ic_app_backup_fg
        "telos_trash_app" -> R.drawable.ic_app_trash_fg
        "telos_clock_app" -> R.drawable.ic_app_clock_fg
        "telos_magnifier_app" -> R.drawable.ic_app_magnifier_fg
        "telos_flashlight_app" -> R.drawable.ic_app_flashlight_fg
        else -> TelosAppIcons.forDomain(domain)
    }

    fun bitmap(context: Context, @DrawableRes res: Int, sizePx: Int = 192): Bitmap? {
        val d = AppCompatResources.getDrawable(context, res) ?: return null
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        d.setBounds(0, 0, sizePx, sizePx)
        d.draw(canvas)
        return bmp
    }

    fun largeIcon(context: Context, domain: String): Bitmap? {
        val res = TelosAppIcons.forDomain(domain) ?: return null
        val px = (48f * context.resources.displayMetrics.density).toInt().coerceIn(96, 256)
        return bitmap(context, res, px)
    }

    fun applyRecents(activity: Activity, domain: String) {
        val label = TelosAppLabels.forDomain(activity, domain)
        activity.title = label
        val res = TelosAppIcons.forDomain(domain)
        val bmp = res?.let { bitmap(activity, it) }
        runCatching {
            @Suppress("DEPRECATION")
            if (bmp != null) {
                activity.setTaskDescription(ActivityManager.TaskDescription(label, bmp))
            } else {
                activity.setTaskDescription(ActivityManager.TaskDescription(label))
            }
        }
    }

    fun decorate(builder: NotificationCompat.Builder, context: Context, domain: String): NotificationCompat.Builder {
        val small = glyph(domain)
        if (small != null) builder.setSmallIcon(small)
        largeIcon(context, domain)?.let { builder.setLargeIcon(it) }
        return builder
    }
}
