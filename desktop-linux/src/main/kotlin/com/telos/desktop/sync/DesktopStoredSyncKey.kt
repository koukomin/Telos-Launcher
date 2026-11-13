package com.telos.desktop.sync

import com.telos.desktop.network.DesktopNetworkController
import com.telos.desktop.vault.HardwareCryptoAccelerator
import com.telos.desktop.vault.PhantomVault
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Opt-in storage for the Cloud Sync / session-bundle passphrase.
 *
 * The sealed blob lives under the unlocked vault config directory (LUKS) when
 * available, otherwise under `~/.config/telos/sync/`. The passphrase is never
 * written as plaintext; AES-GCM + a purpose-bound wrap key prevents casual
 * grepping. Never invents credentials — [store] requires a non-empty passphrase
 * supplied by the user (or extracted from KeePass after an explicit unlock).
 */
object DesktopStoredSyncKey {
    const val FILE_NAME = "session-sync.key"
    private val MAGIC = byteArrayOf('T'.code.toByte(), 'S'.code.toByte(), 'K'.code.toByte(), '1'.code.toByte())
    private const val PURPOSE = "TelosDesktopSessionSyncKey/v1"

    fun location(configDir: File? = null): File {
        val dir = configDir?.takeIf { it.isDirectory || it.mkdirs() }
            ?: runCatching { PhantomVault.engine.configDir }.getOrNull()?.takeIf { it.isDirectory || it.mkdirs() }
            ?: File(DesktopNetworkController.configHome(), "sync").also { it.mkdirs() }
        return File(dir, FILE_NAME)
    }

    fun hasStoredKey(configDir: File? = null): Boolean {
        val f = location(configDir)
        return f.isFile && f.length() > MAGIC.size + 16 + 12 + 16
    }

    fun store(passphrase: CharArray, configDir: File? = null): Boolean {
        if (passphrase.isEmpty()) return false
        return runCatching {
            val f = location(configDir)
            f.parentFile?.mkdirs()
            val salt = HardwareCryptoAccelerator.random(16)
            val iv = HardwareCryptoAccelerator.random(12)
            val key = wrapKey(salt)
            try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
                val pt = String(passphrase).toByteArray(StandardCharsets.UTF_8)
                try {
                    val ct = cipher.doFinal(pt)
                    f.writeBytes(MAGIC + salt + iv + ct)
                } finally {
                    pt.fill(0)
                }
            } finally {
                key.fill(0)
            }
            hardenPermissions(f)
            true
        }.getOrDefault(false)
    }

    fun load(configDir: File? = null): CharArray? {
        val f = location(configDir)
        if (!f.isFile) return null
        return runCatching {
            val raw = f.readBytes()
            if (raw.size < MAGIC.size + 16 + 12 + 16) return@runCatching null
            if (!raw.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) return@runCatching null
            var o = MAGIC.size
            val salt = raw.copyOfRange(o, o + 16)
            o += 16
            val iv = raw.copyOfRange(o, o + 12)
            o += 12
            val ct = raw.copyOfRange(o, raw.size)
            val key = wrapKey(salt)
            try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
                val pt = cipher.doFinal(ct)
                try {
                    String(pt, StandardCharsets.UTF_8).toCharArray()
                } finally {
                    pt.fill(0)
                }
            } finally {
                key.fill(0)
            }
        }.getOrNull()
    }

    fun clear(configDir: File? = null) {
        val f = location(configDir)
        if (!f.isFile) return
        runCatching {
            val len = f.length().toInt().coerceAtLeast(0).coerceAtMost(1_048_576)
            if (len > 0) f.writeBytes(ByteArray(len))
        }
        f.delete()
    }

    private fun wrapKey(salt: ByteArray): ByteArray =
        HardwareCryptoAccelerator.hmacSha512(PURPOSE.toByteArray(StandardCharsets.UTF_8), salt).copyOf(32)

    private fun hardenPermissions(file: File) {
        runCatching {
            Files.setPosixFilePermissions(
                file.toPath(),
                setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
            )
        }
    }
}
