package de.mm20.launcher2.keepasstl

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

object SteganographyEngine {

    /**
     * Embeds a byte array into a Bitmap using Least Significant Bit (LSB) steganography.
     * The first 4 bytes of the hidden payload represent the length of the data.
     */
    fun embedInImage(sourceImage: File, payload: ByteArray, outputImage: File): Boolean {
        val bitmap = BitmapFactory.decodeFile(sourceImage.absolutePath) ?: return false

        // We need 4 bytes for length + payload size
        val totalBytes = 4 + payload.size
        val totalBits = totalBytes * 8

        // Ensure image is large enough (we hide 3 bits per pixel: R, G, B)
        val maxBits = bitmap.width * bitmap.height * 3
        if (totalBits > maxBits) return false

        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)

        val dataWithLength = ByteArray(totalBytes)
        // Store size in first 4 bytes (Big Endian)
        dataWithLength[0] = (payload.size shr 24).toByte()
        dataWithLength[1] = (payload.size shr 16).toByte()
        dataWithLength[2] = (payload.size shr 8).toByte()
        dataWithLength[3] = payload.size.toByte()
        System.arraycopy(payload, 0, dataWithLength, 4, payload.size)

        var bitIndex = 0
        var x = 0
        var y = 0

        while (bitIndex < totalBits) {
            val pixel = mutableBitmap.getPixel(x, y)
            var r = Color.red(pixel)
            var g = Color.green(pixel)
            var b = Color.blue(pixel)

            // Red
            if (bitIndex < totalBits) {
                val bit = getBit(dataWithLength, bitIndex)
                r = (r and 0xFE) or bit
                bitIndex++
            }
            // Green
            if (bitIndex < totalBits) {
                val bit = getBit(dataWithLength, bitIndex)
                g = (g and 0xFE) or bit
                bitIndex++
            }
            // Blue
            if (bitIndex < totalBits) {
                val bit = getBit(dataWithLength, bitIndex)
                b = (b and 0xFE) or bit
                bitIndex++
            }

            mutableBitmap.setPixel(x, y, Color.argb(Color.alpha(pixel), r, g, b))

            x++
            if (x >= mutableBitmap.width) {
                x = 0
                y++
            }
        }

        FileOutputStream(outputImage).use { out ->
            mutableBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        mutableBitmap.recycle()
        bitmap.recycle()
        return true
    }

    /**
     * Extracts a hidden payload from an LSB encoded Bitmap.
     */
    fun extractFromImage(sourceImage: File): ByteArray? {
        val bitmap = BitmapFactory.decodeFile(sourceImage.absolutePath) ?: return null

        var x = 0
        var y = 0

        // Read length (32 bits = 4 bytes)
        var lengthBits = 0
        var length = 0
        while (lengthBits < 32) {
            val pixel = bitmap.getPixel(x, y)
            val r = Color.red(pixel) and 1
            val g = Color.green(pixel) and 1
            val b = Color.blue(pixel) and 1

            if (lengthBits < 32) { length = (length shl 1) or r; lengthBits++ }
            if (lengthBits < 32) { length = (length shl 1) or g; lengthBits++ }
            if (lengthBits < 32) { length = (length shl 1) or b; lengthBits++ }

            x++
            if (x >= bitmap.width) { x = 0; y++ }
        }

        // Sanity check
        if (length <= 0 || length > 10 * 1024 * 1024) return null

        val payload = ByteArray(length)
        var bitIndex = 0
        var currentByte = 0

        // We might be mid-pixel from the length extraction, need to handle that if exact.
        // For simplicity, we just continue fetching bits. We fetched exactly 32 bits (10 pixels + 2 channels of 11th pixel)
        // Recalculate start safely by resetting and reading everything

        x = 0; y = 0
        var totalBitsRead = 0
        val targetBits = (4 + length) * 8
        val outStream = ByteArrayOutputStream()

        while (totalBitsRead < targetBits) {
            val pixel = bitmap.getPixel(x, y)
            val r = Color.red(pixel) and 1
            val g = Color.green(pixel) and 1
            val b = Color.blue(pixel) and 1

            val bits = intArrayOf(r, g, b)
            for (bit in bits) {
                if (totalBitsRead >= 32 && totalBitsRead < targetBits) {
                    currentByte = (currentByte shl 1) or bit
                    if ((totalBitsRead - 32 + 1) % 8 == 0) {
                        outStream.write(currentByte)
                        currentByte = 0
                    }
                }
                totalBitsRead++
            }

            x++
            if (x >= bitmap.width) { x = 0; y++ }
        }

        bitmap.recycle()
        return outStream.toByteArray()
    }

    private fun getBit(data: ByteArray, bitIndex: Int): Int {
        val byteIndex = bitIndex / 8
        val bitOffset = 7 - (bitIndex % 8)
        return (data[byteIndex].toInt() shr bitOffset) and 1
    }
}
