package de.mm20.launcher2.ui.media.photos

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream

/** Reading, editing and removing EXIF metadata of images. */
object PhotoExif {

    /** Tags that are shown and can be edited or removed. Label to tag. */
    val tags: List<Pair<String, String>> = listOf(
        "Date taken" to ExifInterface.TAG_DATETIME_ORIGINAL,
        "Camera make" to ExifInterface.TAG_MAKE,
        "Camera model" to ExifInterface.TAG_MODEL,
        "Lens" to ExifInterface.TAG_LENS_MODEL,
        "Exposure" to ExifInterface.TAG_EXPOSURE_TIME,
        "Aperture" to ExifInterface.TAG_F_NUMBER,
        "ISO" to ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
        "Focal length" to ExifInterface.TAG_FOCAL_LENGTH,
        "Software" to ExifInterface.TAG_SOFTWARE,
        "Artist" to ExifInterface.TAG_ARTIST,
        "Description" to ExifInterface.TAG_IMAGE_DESCRIPTION,
        "Copyright" to ExifInterface.TAG_COPYRIGHT,
    )

    private val gpsTags = listOf(
        ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LATITUDE_REF,
        ExifInterface.TAG_GPS_LONGITUDE, ExifInterface.TAG_GPS_LONGITUDE_REF,
        ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_GPS_ALTITUDE_REF,
        ExifInterface.TAG_GPS_TIMESTAMP, ExifInterface.TAG_GPS_DATESTAMP,
        ExifInterface.TAG_GPS_PROCESSING_METHOD, ExifInterface.TAG_GPS_SPEED,
        ExifInterface.TAG_GPS_IMG_DIRECTION, ExifInterface.TAG_GPS_DEST_LATITUDE,
        ExifInterface.TAG_GPS_DEST_LONGITUDE,
    )

    data class Info(
        val values: Map<String, String>,
        val width: Int,
        val height: Int,
        val latLong: DoubleArray?,
    ) {
        val hasGps get() = latLong != null
    }

    fun read(resolver: ContentResolver, uri: Uri): Info? = runCatching {
        resolver.openInputStream(uri)!!.use { stream ->
            val exif = ExifInterface(stream)
            val values = LinkedHashMap<String, String>()
            for ((_, tag) in tags) values[tag] = exif.getAttribute(tag).orEmpty()
            Info(
                values = values,
                width = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0),
                height = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0),
                latLong = exif.latLong,
            )
        }
    }.getOrNull()

    /** Writes the given tag values in place. The caller must hold write access to [uri]. */
    fun write(resolver: ContentResolver, uri: Uri, values: Map<String, String>): Boolean = runCatching {
        resolver.openFileDescriptor(uri, "rw")!!.use { pfd ->
            val exif = ExifInterface(pfd.fileDescriptor)
            for ((tag, value) in values) exif.setAttribute(tag, value.ifBlank { null })
            exif.saveAttributes()
        }
        true
    }.getOrDefault(false)

    /** Removes the location only, or all known tags, in place. */
    fun strip(resolver: ContentResolver, uri: Uri, gpsOnly: Boolean): Boolean = runCatching {
        resolver.openFileDescriptor(uri, "rw")!!.use { pfd ->
            val exif = ExifInterface(pfd.fileDescriptor)
            for (tag in gpsTags) exif.setAttribute(tag, null)
            if (!gpsOnly) {
                for ((_, tag) in tags) exif.setAttribute(tag, null)
                for (tag in listOf(
                    ExifInterface.TAG_DATETIME, ExifInterface.TAG_DATETIME_DIGITIZED,
                    ExifInterface.TAG_MAKER_NOTE, ExifInterface.TAG_USER_COMMENT,
                    ExifInterface.TAG_BODY_SERIAL_NUMBER, ExifInterface.TAG_CAMERA_OWNER_NAME,
                    ExifInterface.TAG_LENS_SERIAL_NUMBER, ExifInterface.TAG_IMAGE_UNIQUE_ID,
                )) exif.setAttribute(tag, null)
            }
            exif.saveAttributes()
        }
        true
    }.getOrDefault(false)

    /** Creates a copy without any metadata (re-encoded) in the cache and returns it. */
    fun cleanCopy(context: Context, uri: Uri): File? = runCatching {
        // a big photo is scaled down while it is read: a 50 megapixel bitmap does not fit in memory
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 4096) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, options) }
            ?: return null
        val rotated = applyOrientation(context, uri, bitmap)
        val dir = File(context.cacheDir, "photos").apply { mkdirs() }
        val file = File(dir, "clean_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { rotated.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        file
    }.getOrNull()

    /** Bakes the EXIF orientation into the pixels so the clean copy is not rotated wrongly. */
    fun applyOrientation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)!!.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val m = android.graphics.Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
    }
}
