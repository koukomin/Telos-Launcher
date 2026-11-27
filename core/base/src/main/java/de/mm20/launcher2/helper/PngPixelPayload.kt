package de.mm20.launcher2.helper

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/** Stores one bit in the blue channel of each fully opaque pixel. */
object PngPixelPayload {
    private const val MAGIC = 0x544C5031
    private const val HEADER_SIZE = 8

    fun embed(pixels: IntArray, encrypted: File, checkpoint: () -> Unit) {
        val capacity = pixels.count { it ushr 24 == 255 } / 8 - HEADER_SIZE
        val size = encrypted.length()
        require(size in 1..capacity.toLong()) { "Image has insufficient opaque pixels" }
        val cursor = Cursor(pixels)
        val sink = object : OutputStream() {
            override fun write(value: Int) = cursor.writeByte(value)
        }
        DataOutputStream(sink).use { output ->
            output.writeInt(MAGIC)
            output.writeInt(size.toInt())
            encrypted.inputStream().use { input -> transfer(input, output, size.toInt(), checkpoint) }
        }
    }

    fun extract(pixels: IntArray, encrypted: File, checkpoint: () -> Unit) {
        val capacity = pixels.count { it ushr 24 == 255 } / 8 - HEADER_SIZE
        val cursor = Cursor(pixels)
        val source = object : InputStream() {
            override fun read(): Int = cursor.readByte()
        }
        DataInputStream(source).use { input ->
            require(input.readInt() == MAGIC) { "Missing Telos PNG payload" }
            val size = input.readInt()
            require(size in 1..capacity) { "Invalid Telos PNG payload length" }
            encrypted.outputStream().use { output -> transfer(input, output, size, checkpoint) }
        }
    }

    private fun transfer(input: InputStream, output: OutputStream, size: Int, checkpoint: () -> Unit) {
        val buffer = ByteArray(8192)
        var remaining = size
        val reader = DataInputStream(input)
        while (remaining > 0) {
            checkpoint()
            val count = minOf(buffer.size, remaining)
            reader.readFully(buffer, 0, count)
            output.write(buffer, 0, count)
            remaining -= count
        }
    }

    private class Cursor(private val pixels: IntArray) {
        private var index = 0

        private fun nextOpaque(): Int {
            while (index < pixels.size && pixels[index] ushr 24 != 255) index++
            require(index < pixels.size) { "Image payload exceeds capacity" }
            return index++
        }

        fun writeByte(value: Int) {
            for (bit in 7 downTo 0) {
                val i = nextOpaque()
                pixels[i] = (pixels[i] and 1.inv()) or ((value ushr bit) and 1)
            }
        }

        fun readByte(): Int {
            var value = 0
            repeat(8) { value = (value shl 1) or (pixels[nextOpaque()] and 1) }
            return value
        }
    }
}
