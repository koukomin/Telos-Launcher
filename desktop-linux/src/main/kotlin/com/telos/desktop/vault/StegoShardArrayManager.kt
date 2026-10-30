package com.telos.desktop.vault

import com.telos.desktop.linux.Sys
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class ShardBinding(
    val file: File,
    val originalSize: Long,
    val payloadOffset: Long,
    val payloadBytes: Long,
    val loopDev: String? = null,
)

class StegoShardArrayManager {
    val spanName = "telos_stego_span"
    val mapperPath get() = "/dev/mapper/$spanName"
    val loops = ArrayList<String>()
    val shards = ArrayList<ShardBinding>()

    fun listCarriers(path: File): List<File> {
        if (path.isFile) return listOf(path)
        if (!path.isDirectory) return emptyList()
        val ext = setOf("mp3", "flac", "mp4", "png", "jpg", "jpeg", "webm", "mkv", "tvault")
        return path.walkTopDown().maxDepth(2).filter { it.isFile && it.extension.lowercase() in ext }.sortedBy { it.absolutePath }.toList()
    }

    fun originalHash(file: File): ByteArray {
        val md = MessageDigest.getInstance("SHA-512")
        val head = ByteArray(65536)
        RandomAccessFile(file, "r").use { raf ->
            val n = raf.read(head)
            if (n > 0) md.update(head, 0, n)
            md.update(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(file.length()).array())
        }
        return md.digest()
    }

    fun orderCarriers(masterKey: ByteArray, files: List<File>): List<File> {
        return files.sortedBy { f ->
            HardwareCryptoAccelerator.hmacSha512(masterKey, originalHash(f) + f.name.toByteArray()).joinToString("") { "%02x".format(it) }
        }
    }

    fun payloadOffset(masterKey: ByteArray, file: File, originalSize: Long): Long {
        val mac = HardwareCryptoAccelerator.hmacSha512(masterKey, originalHash(file) + "offset".toByteArray())
        val jitter = (mac[0].toInt() and 0x0f) * 4096L
        return align4096(originalSize) + jitter
    }

    fun preparePool(masterKey: ByteArray, files: List<File>, perShardBytes: Long = 20L * 1024 * 1024): List<ShardBinding> {
        val ordered = orderCarriers(masterKey, files)
        return ordered.map { file ->
            val orig = detectOriginalSize(masterKey, file) ?: file.length()
            val offset = payloadOffset(masterKey, file, orig)
            ensurePayload(file, orig, offset, perShardBytes, masterKey)
            ShardBinding(file, orig, offset, perShardBytes)
        }
    }

    fun attachSpan(bindings: List<ShardBinding>): Boolean {
        detach()
        shards.clear()
        shards += bindings
        val table = StringBuilder()
        var sector = 0L
        for (b in bindings) {
            val loop = Sys.run("pkexec", "losetup", "--offset", b.payloadOffset.toString(), "--sizelimit", b.payloadBytes.toString(), "-f", "--show", b.file.absolutePath, timeoutSec = 15)
            val dev = loop.stdout.trim().ifBlank {
                Sys.run("losetup", "--offset", b.payloadOffset.toString(), "--sizelimit", b.payloadBytes.toString(), "-f", "--show", b.file.absolutePath, timeoutSec = 15).stdout.trim()
            }
            if (dev.isBlank()) return false
            loops += dev
            val sectors = (b.payloadBytes / 512L).coerceAtLeast(1)
            table.append("$sector $sectors linear $dev 0\n")
            sector += sectors
        }
        val create = Sys.run("pkexec", "dmsetup", "create", spanName, timeoutSec = 15, input = table.toString())
        val ok = create.ok || Sys.run("dmsetup", "create", spanName, timeoutSec = 15, input = table.toString()).ok
        return ok && File(mapperPath).exists()
    }

    fun reloadSpan(): Boolean {
        if (shards.isEmpty()) return false
        val table = StringBuilder()
        var sector = 0L
        shards.forEachIndexed { i, b ->
            val dev = loops.getOrNull(i) ?: return false
            Sys.run("pkexec", "losetup", "-c", dev)
            Sys.run("losetup", "-c", dev)
            val sectors = (b.payloadBytes / 512L).coerceAtLeast(1)
            table.append("$sector $sectors linear $dev 0\n")
            sector += sectors
        }
        Sys.run("pkexec", "dmsetup", "suspend", spanName)
        Sys.run("pkexec", "dmsetup", "reload", spanName, input = table.toString())
        Sys.run("pkexec", "dmsetup", "resume", spanName)
        return true
    }

    fun expandEvenly(extraTotal: Long) {
        if (shards.isEmpty()) return
        val each = (extraTotal / shards.size).coerceAtLeast(8L * 1024 * 1024)
        for (i in shards.indices) {
            val b = shards[i]
            val newLen = b.payloadBytes + each
            RandomAccessFile(b.file, "rw").use { raf ->
                val target = b.payloadOffset + newLen + TRAILER
                if (raf.length() < target) raf.setLength(target)
            }
            shards[i] = b.copy(payloadBytes = newLen)
        }
        reloadSpan()
    }

    fun detach() {
        Sys.run("pkexec", "dmsetup", "remove", spanName)
        Sys.run("dmsetup", "remove", spanName)
        loops.forEach { d ->
            Sys.run("pkexec", "losetup", "-d", d)
            Sys.run("losetup", "-d", d)
        }
        loops.clear()
    }

    fun wipeInnerHeaders(bindings: List<ShardBinding>) {
        val noise = HardwareCryptoAccelerator.random(4096)
        bindings.forEach { b ->
            runCatching {
                RandomAccessFile(b.file, "rw").use { raf ->
                    raf.seek(b.payloadOffset)
                    raf.write(noise)
                    raf.fd.sync()
                }
            }
        }
    }

    private fun ensurePayload(file: File, orig: Long, offset: Long, payload: Long, key: ByteArray) {
        RandomAccessFile(file, "rw").use { raf ->
            val need = offset + payload + TRAILER
            if (raf.length() < need) raf.setLength(need)
            writeTrailer(raf, orig, key, file)
        }
    }

    private fun writeTrailer(raf: RandomAccessFile, orig: Long, key: ByteArray, file: File) {
        val iv = HardwareCryptoAccelerator.hmacSha512(key, originalHash(file) + "iv".toByteArray()).copyOf(12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key.copyOf(32), "AES"), GCMParameterSpec(128, iv))
        val pt = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).putLong(orig).putLong(MAGIC).array()
        val ct = cipher.doFinal(pt)
        raf.seek(raf.length() - TRAILER)
        raf.write(iv)
        raf.write(ct)
    }

    private fun detectOriginalSize(key: ByteArray, file: File): Long? {
        if (file.length() < TRAILER) return null
        return runCatching {
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(raf.length() - TRAILER)
                val iv = ByteArray(12)
                raf.readFully(iv)
                val ct = ByteArray((TRAILER - 12).coerceAtMost(64).toInt())
                val n = raf.read(ct)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key.copyOf(32), "AES"), GCMParameterSpec(128, iv))
                val pt = cipher.doFinal(ct, 0, n.coerceAtLeast(32).coerceAtMost(ct.size))
                val bb = ByteBuffer.wrap(pt).order(ByteOrder.LITTLE_ENDIAN)
                val orig = bb.long
                val magic = bb.long
                if (magic == MAGIC) orig else null
            }
        }.getOrNull()
    }

    private fun align4096(n: Long) = (n + 4095) and 4095L.inv()

    companion object {
        private const val TRAILER = 8192L
        private const val MAGIC = 0x54454C4F53535447L // TELOSSTG
    }
}
