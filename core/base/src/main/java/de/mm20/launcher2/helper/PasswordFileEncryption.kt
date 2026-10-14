package de.mm20.launcher2.helper

import java.io.*
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.WRITE
import java.nio.file.attribute.BasicFileAttributes
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Telos authenticated file format v1. Original files are never removed or overwritten. */
object PasswordFileEncryption {
    private val magic = byteArrayOf(84, 69, 76, 79, 83, 65, 69, 83)
    private const val VERSION = 1
    private const val ITERATIONS = 600_000
    private const val CHUNK = 65_536
    private const val TAG = 16
    private const val HEADER_SIZE = 52

    fun encrypt(source: File, destination: File, password: CharArray, privateStaging: File,
        checkpoint: () -> Unit = {}, progress: (Long, Long) -> Unit = { _, _ -> }) =
        transform(source, destination, password, privateStaging, false, checkpoint, progress)

    fun decrypt(source: File, destination: File, password: CharArray, privateStaging: File,
        checkpoint: () -> Unit = {}, progress: (Long, Long) -> Unit = { _, _ -> }) =
        transform(source, destination, password, privateStaging, true, checkpoint, progress)

    private fun transform(source: File, destination: File, password: CharArray, staging: File,
        decrypt: Boolean, checkpoint: () -> Unit, progress: (Long, Long) -> Unit) {
        require(password.isNotEmpty() && password.size <= 1024) { "Invalid password length" }
        val input = source.toPath()
        val original = Files.readAttributes(input, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
        require(original.isRegularFile && !original.isSymbolicLink) { "A regular file is required" }
        require(source.canonicalFile != destination.canonicalFile) { "Source and destination must differ" }
        require(staging.isDirectory && !Files.isSymbolicLink(staging.toPath())) { "Private staging directory is required" }
        if (Files.exists(destination.toPath(), NOFOLLOW_LINKS)) throw java.nio.file.FileAlreadyExistsException(destination.path)
        val temporary = Files.createTempFile(staging.toPath(), "telos-aes-", ".part").toFile()
        // The caller supplies an app-private no-backup directory, never a shared destination.
        temporary.setReadable(false, false); temporary.setWritable(false, false)
        temporary.setReadable(true, true); temporary.setWritable(true, true)
        try {
            checkpoint()
            Files.newInputStream(input, NOFOLLOW_LINKS).use { raw ->
                DataInputStream(BufferedInputStream(raw, CHUNK)).use { reader ->
                    FileOutputStream(temporary).use { rawOutput ->
                        val writer = DataOutputStream(BufferedOutputStream(rawOutput, CHUNK))
                        try {
                            if (decrypt) decryptData(reader, writer, password, checkpoint, progress)
                            else encryptData(reader, writer, original.size(), password, checkpoint, progress)
                            writer.flush(); rawOutput.fd.sync()
                        } finally { writer.close() }
                    }
                }
            }
            val after = Files.readAttributes(input, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
            require(after.isRegularFile && after.size() == original.size() && after.lastModifiedTime() == original.lastModifiedTime() &&
                (original.fileKey() == null || original.fileKey() == after.fileKey())) { "Source changed" }
            checkpoint()
            publish(temporary, destination, checkpoint)
        } finally { temporary.delete() }
    }

    private fun derive(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val specification = PBEKeySpec(password, salt, iterations, 256)
        val encoded = try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(specification).encoded }
            finally { specification.clearPassword() }
        return try { SecretKeySpec(encoded, "AES") } finally { encoded.fill(0) }
    }

    private fun cipher(mode: Int, key: SecretKeySpec, prefix: ByteArray, index: Int, size: Int, header: ByteArray): Cipher =
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            val nonce = ByteBuffer.allocate(12).put(prefix).putInt(index).array()
            init(mode, key, GCMParameterSpec(128, nonce))
            updateAAD(header)
            updateAAD(ByteBuffer.allocate(8).putInt(index).putInt(size).array())
        }

    private fun encryptData(reader: DataInputStream, writer: DataOutputStream, length: Long, password: CharArray,
        checkpoint: () -> Unit, progress: (Long, Long) -> Unit) {
        require(length >= 0 && length <= CHUNK.toLong() * (Int.MAX_VALUE - 1)) { "File is too large" }
        val random = SecureRandom()
        val salt = ByteArray(16).also(random::nextBytes)
        val prefix = ByteArray(8).also(random::nextBytes)
        val header = ByteBuffer.allocate(HEADER_SIZE).put(magic).putInt(VERSION).putInt(ITERATIONS)
            .put(salt).put(prefix).putInt(CHUNK).putLong(length).array()
        val key = derive(password, salt, ITERATIONS)
        checkpoint(); writer.write(header)
        val buffer = ByteArray(CHUNK)
        var completed = 0L; var index = 0
        try {
            while (completed < length) {
                checkpoint()
                val size = minOf(CHUNK.toLong(), length - completed).toInt()
                reader.readFully(buffer, 0, size)
                val encrypted = cipher(Cipher.ENCRYPT_MODE, key, prefix, index++, size, header).doFinal(buffer, 0, size)
                writer.writeInt(size); writer.write(encrypted)
                completed += size; progress(completed, length)
            }
            require(reader.read() == -1) { "Source changed" }
            writer.writeInt(0)
            writer.write(cipher(Cipher.ENCRYPT_MODE, key, prefix, index, 0, header).doFinal())
        } finally { buffer.fill(0) }
    }

    private fun decryptData(reader: DataInputStream, writer: DataOutputStream, password: CharArray,
        checkpoint: () -> Unit, progress: (Long, Long) -> Unit) {
        val header = ByteArray(HEADER_SIZE); reader.readFully(header)
        val data = ByteBuffer.wrap(header)
        require(ByteArray(8).also(data::get).contentEquals(magic) && data.int == VERSION) { "Unsupported encrypted file" }
        val iterations = data.int
        // Exact v1 work factor bounds CPU even for hostile input; future versions need a new parser.
        require(iterations == ITERATIONS) { "Unsupported key derivation" }
        val salt = ByteArray(16).also(data::get); val prefix = ByteArray(8).also(data::get)
        require(data.int == CHUNK) { "Unsupported chunk size" }
        val length = data.long
        require(length >= 0 && length <= CHUNK.toLong() * (Int.MAX_VALUE - 1)) { "Invalid file size" }
        val key = derive(password, salt, iterations)
        var completed = 0L; var index = 0
        while (true) {
            checkpoint()
            val size = reader.readInt()
            val expected = minOf(CHUNK.toLong(), length - completed).toInt()
            require(size == expected && size in 0..CHUNK) { "Invalid encrypted chunk" }
            val ciphertext = ByteArray(size + TAG); reader.readFully(ciphertext)
            val plaintext = cipher(Cipher.DECRYPT_MODE, key, prefix, index++, size, header).doFinal(ciphertext)
            try {
                if (size == 0) {
                    require(completed == length && reader.read() == -1) { "Invalid encrypted footer" }
                    return
                }
                // Each chunk is authenticated before any plaintext is written to private staging.
                writer.write(plaintext); completed += size; progress(completed, length)
            } finally { plaintext.fill(0) }
        }
    }

    private fun publish(temporary: File, destination: File, checkpoint: () -> Unit) {
        // CREATE_NEW is exclusive even on shared storage. Publish starts only after the complete
        // encrypted input has authenticated. Power loss during this final copy can leave a partial
        // destination; this intentionally does not claim atomic rename on emulated/FAT storage.
        var identity: Any? = null
        var created = false
        try {
            Files.newByteChannel(destination.toPath(), CREATE_NEW, WRITE).use { channel ->
                created = true
                identity = Files.readAttributes(destination.toPath(), BasicFileAttributes::class.java, NOFOLLOW_LINKS).fileKey()
                FileInputStream(temporary).use { source ->
                    val buffer = ByteArray(CHUNK)
                    try {
                        while (true) {
                            checkpoint(); val count = source.read(buffer)
                            if (count < 0) break
                            val bytes = ByteBuffer.wrap(buffer, 0, count)
                            while (bytes.hasRemaining()) { checkpoint(); channel.write(bytes) }
                        }
                    } finally { buffer.fill(0) }
                }
                if (channel is java.nio.channels.FileChannel) channel.force(true)
            }
        } catch (error: Throwable) {
            // Never delete a replacement created by somebody else. If filesystem identity is
            // unavailable, leaving our incomplete file is safer than deleting an unknown file.
            if (created && identity != null) runCatching {
                val current = Files.readAttributes(destination.toPath(), BasicFileAttributes::class.java, NOFOLLOW_LINKS)
                if (current.fileKey() == identity) Files.delete(destination.toPath())
            }
            throw error
        }
    }
}
