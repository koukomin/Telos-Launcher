package de.mm20.launcher2.search

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Universal QR & Barcode Generator/Parser for Telos Search and Quick Actions.
 * Generates custom QR codes (URLs, Wi-Fi keys, vCards) off the main thread.
 */
object TelosQrScannerEngine {

    private const val TAG = "TelosQrScannerEngine"

    suspend fun generateQrCode(text: String, sizePx: Int = 512): Bitmap? = withContext(Dispatchers.Default) {
        if (text.isBlank()) return@withContext null
        runCatching {
            val writer = MultiFormatWriter()
            val matrix = writer.encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx)
            val width = matrix.width
            val height = matrix.height
            val pixels = IntArray(width * height)

            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
                }
            }

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            bitmap
        }.getOrElse { e ->
            Log.e(TAG, "Failed to generate QR code", e)
            null
        }
    }
}
