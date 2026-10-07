package de.mm20.launcher2.comms.sms

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream

/** Prepares a picture or video for a multimedia message: carriers accept only small messages. */
internal object MmsMedia {
    private const val MAX_BYTES = 600_000

    fun read(context: Context, uri: Uri, index: Int): MmsPart? = runCatching {
        val type = context.contentResolver.getType(uri) ?: "application/octet-stream"
        if (type.startsWith("image/")) image(context, uri, index)
        else {
            val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
            if (bytes.size > MAX_BYTES * 4) null else MmsPart(type, "media$index.${type.substringAfter('/')}", bytes)
        }
    }.getOrNull()

    private fun image(context: Context, uri: Uri, index: Int): MmsPart? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2
        val bitmap = context.contentResolver.openInputStream(uri)!!.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        var quality = 85
        var bytes: ByteArray
        do {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            bytes = out.toByteArray()
            quality -= 15
        } while (bytes.size > MAX_BYTES && quality > 25)
        bitmap.recycle()
        return MmsPart("image/jpeg", "image$index.jpg", bytes)
    }
}
