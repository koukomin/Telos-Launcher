package de.mm20.launcher2.helper

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS

/** Android PNG adapter for the bounded, authenticated pixel payload. */
object EncryptedPngCarrier {
    private const val MAX_PIXELS = 8_000_000L

    fun embed(openCarrier: () -> InputStream, payload: File, output: File, password: CharArray,
              staging: File, checkpoint: () -> Unit) {
        val bitmap = decode(openCarrier)
        try {
            val encrypted = File(staging, "pixel-payload")
            PasswordFileEncryption.encrypt(payload, encrypted, password, staging, checkpoint)
            val pixels = bitmap.pixels()
            PngPixelPayload.embed(pixels, encrypted, checkpoint)
            bitmap.setPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val encoded = File(staging, "carrier.png")
            encoded.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            PasswordFileEncryption.publish(encoded, output, checkpoint)
        } finally {
            bitmap.recycle()
        }
    }

    fun extract(carrier: File, output: File, password: CharArray, staging: File,
                checkpoint: () -> Unit) {
        val bitmap = decode { Files.newInputStream(carrier.toPath(), NOFOLLOW_LINKS) }
        val encrypted = File(staging, "pixel-payload")
        try {
            PngPixelPayload.extract(bitmap.pixels(), encrypted, checkpoint)
        } finally {
            bitmap.recycle()
        }
        PasswordFileEncryption.decrypt(encrypted, output, password, staging, checkpoint)
    }

    private fun decode(open: () -> InputStream): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open().use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outMimeType == "image/png" && bounds.outWidth > 0 && bounds.outHeight > 0 &&
            bounds.outWidth.toLong() * bounds.outHeight <= MAX_PIXELS) { "Expected a PNG within the pixel limit" }
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
            inMutable = true
        }
        val bitmap = checkNotNull(open().use { BitmapFactory.decodeStream(it, null, options) })
        if (bitmap.width != bounds.outWidth || bitmap.height != bounds.outHeight ||
            options.outMimeType != "image/png") {
            bitmap.recycle()
            error("Carrier changed while decoding")
        }
        return bitmap
    }

    private fun Bitmap.pixels(): IntArray =
        IntArray(width * height).also { getPixels(it, 0, width, 0, 0, width, height) }
}
