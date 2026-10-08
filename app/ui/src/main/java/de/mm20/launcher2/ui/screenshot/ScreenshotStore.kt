package de.mm20.launcher2.ui.screenshot

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ScreenshotFormat(val key: String, val extension: String, val mime: String) {
    Png("png", "png", "image/png"),
    Jpeg("jpeg", "jpg", "image/jpeg");

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: Png
    }
}

/** The settings of Telos Screenshot, kept in the app's preferences */
class ScreenshotSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("telos_screenshot", Context.MODE_PRIVATE)

    var format: ScreenshotFormat
        get() = ScreenshotFormat.fromKey(prefs.getString("format", null))
        set(value) = prefs.edit().putString("format", value.key).apply()

    /** Seconds to wait before a capture, so that menus and the keyboard can be opened first */
    var delay: Int
        get() = prefs.getInt("delay", 0).coerceIn(0, 10)
        set(value) = prefs.edit().putInt("delay", value).apply()

    /** Show a notification with share, edit and delete after a screenshot */
    var notify: Boolean
        get() = prefs.getBoolean("notify", true)
        set(value) = prefs.edit().putBoolean("notify", value).apply()
}

data class SavedScreenshot(val uri: Uri, val name: String, val modified: Long)

/** Saves, lists and deletes the screenshots of Telos, in Pictures/Screenshots */
object ScreenshotStore {

    private fun fileName(format: ScreenshotFormat) =
        "Screenshot_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + "_Telos." + format.extension

    fun save(context: Context, bitmap: Bitmap): Uri? {
        val settings = ScreenshotSettings(context)
        val format = settings.format
        val name = fileName(format)
        val compress = if (format == ScreenshotFormat.Png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        // a JPEG has no transparency (the outside of a partial screenshot), it becomes black
        val toSave = if (format == ScreenshotFormat.Jpeg && bitmap.hasAlpha()) {
            Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888).also {
                android.graphics.Canvas(it).apply { drawColor(android.graphics.Color.BLACK); drawBitmap(bitmap, 0f, 0f, null) }
            }
        } else bitmap
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(MediaStore.Images.Media.MIME_TYPE, format.mime)
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Screenshots")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
                val written = context.contentResolver.openOutputStream(uri)?.use { toSave.compress(compress, 95, it) } ?: false
                if (!written) {
                    runCatching { context.contentResolver.delete(uri, null, null) }
                    return null
                }
                context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
                uri
            } else {
                val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "Screenshots").apply { mkdirs() }
                val file = File(dir, name)
                file.outputStream().use { toSave.compress(compress, 95, it) }
                Uri.fromFile(file)
            }
        } catch (e: Exception) {
            null
        }
    }

    /** The screenshots that Telos made. Android only shows an app the files it made itself, which needs no permission. */
    fun list(context: Context): List<SavedScreenshot> {
        val result = mutableListOf<SavedScreenshot>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.DATE_MODIFIED)
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection,
                "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ? AND ${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?",
                arrayOf(Environment.DIRECTORY_PICTURES + "/Screenshots%", "Screenshot_%_Telos.%"),
                "${MediaStore.Images.Media.DATE_MODIFIED} DESC",
            )?.use { c ->
                while (c.moveToNext()) {
                    result += SavedScreenshot(
                        ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, c.getLong(0)),
                        c.getString(1) ?: continue, c.getLong(2) * 1000,
                    )
                }
            }
        } else {
            File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "Screenshots").listFiles()
                ?.sortedByDescending { it.lastModified() }
                ?.forEach { result += SavedScreenshot(Uri.fromFile(it), it.name, it.lastModified()) }
        }
        return result
    }

    fun delete(context: Context, uri: Uri): Boolean = try {
        if (uri.scheme == "file") File(uri.path!!).delete() else context.contentResolver.delete(uri, null, null) > 0
    } catch (e: Exception) {
        false
    }

    fun load(context: Context, uri: Uri): Bitmap? = try {
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
    } catch (e: Exception) {
        null
    }
}
