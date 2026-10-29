package de.mm20.launcher2.comms.privacy

import android.content.Context
import android.net.Uri
import android.util.Log
import android.media.ExifInterface
import java.io.File
import java.io.FileOutputStream

/**
 * EXIF & Metadata Scrubber for Telos Launcher.
 * Strips sensitive GPS coordinates, camera specs, and timestamp metadata
 * from images before sharing via Telos Drop, Messages, or external apps.
 */
object MediaExifScrubber {
    private const val TAG = "MediaExifScrubber"

    fun scrubExif(context: Context, sourceUri: Uri): File? {
        return runCatching {
            val inputStream = context.contentResolver.openInputStream(sourceUri) ?: return null
            val tempFile = File(context.cacheDir, "scrubbed_${System.currentTimeMillis()}.jpg")

            FileOutputStream(tempFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }

            val exif = ExifInterface(tempFile.absolutePath)

            // Scrub sensitive GPS location attributes
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, null)
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, null)
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, null)
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, null)
            exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE, null)
            exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF, null)
            exif.setAttribute(ExifInterface.TAG_GPS_DATESTAMP, null)
            exif.setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, null)

            // Scrub device hardware specs
            exif.setAttribute(ExifInterface.TAG_MAKE, null)
            exif.setAttribute(ExifInterface.TAG_MODEL, null)
            exif.setAttribute(ExifInterface.TAG_SOFTWARE, null)

            exif.saveAttributes()
            Log.i(TAG, "Successfully scrubbed EXIF metadata from image")
            tempFile
        }.getOrNull()
    }
}
