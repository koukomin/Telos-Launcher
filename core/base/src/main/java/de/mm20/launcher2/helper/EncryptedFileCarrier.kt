package de.mm20.launcher2.helper

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.InputStream
import java.nio.channels.Channels
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.StandardOpenOption.READ

/** Stores a Telos AES payload after the original carrier bytes, followed by its length and marker. */
object EncryptedFileCarrier {
    private const val MARKER = 0x54454C4F53434131L
    private const val FOOTER_SIZE = 16L

    fun embed(carrier: InputStream, payload: File, output: File, password: CharArray,
              staging: File, checkpoint: () -> Unit) {
        val encrypted = File(staging, "payload")
        PasswordFileEncryption.encrypt(payload, encrypted, password, staging, checkpoint)
        val combined = File(staging, "carrier")
        DataOutputStream(combined.outputStream().buffered()).use { writer ->
            val buffer = ByteArray(65536)
            while (true) {
                checkpoint()
                val count = carrier.read(buffer)
                if (count < 0) break
                writer.write(buffer, 0, count)
            }
            encrypted.inputStream().use { reader ->
                while (true) {
                    checkpoint()
                    val count = reader.read(buffer)
                    if (count < 0) break
                    writer.write(buffer, 0, count)
                }
            }
            writer.writeLong(encrypted.length())
            writer.writeLong(MARKER)
        }
        PasswordFileEncryption.publish(combined, output, checkpoint)
    }

    fun extract(carrier: File, output: File, password: CharArray, staging: File,
                checkpoint: () -> Unit) {
        require(Files.isRegularFile(carrier.toPath(), NOFOLLOW_LINKS))
        val encrypted = File(staging, "payload")
        Files.newByteChannel(carrier.toPath(), READ, NOFOLLOW_LINKS).use { channel ->
            val end = channel.size() - FOOTER_SIZE
            require(end >= 0) { "Missing Telos carrier footer" }
            val reader = DataInputStream(Channels.newInputStream(channel))
            channel.position(end)
            var remaining = reader.readLong()
            require(reader.readLong() == MARKER && remaining in 1..end) { "Invalid Telos carrier footer" }
            channel.position(end - remaining)
            encrypted.outputStream().buffered().use { writer ->
                val buffer = ByteArray(65536)
                while (remaining > 0) {
                    checkpoint()
                    val count = reader.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                    if (count < 0) throw EOFException()
                    writer.write(buffer, 0, count)
                    remaining -= count
                }
            }
        }
        PasswordFileEncryption.decrypt(encrypted, output, password, staging, checkpoint)
    }
}
