package com.telos.desktop.sync.pairing

import com.telos.desktop.network.DesktopNetworkController
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.HexFormat
import java.util.Properties

/**
 * Persists opaque LAN pairing tokens as SHA-256 digests (never plaintext).
 * Tokens authorize later bundle push/pull after the short-lived code expires.
 */
object LanPairTokenStore {
    const val DIR_NAME = "lan-pair"
    const val INDEX_FILE = "tokens.properties"
    private val random = SecureRandom()

    data class StoredPair(
        val deviceId: String,
        val deviceName: String,
        val createdAtEpochMs: Long,
    )

    fun storeDir(): File =
        File(DesktopNetworkController.configHome(), "sync/$DIR_NAME").also { it.mkdirs() }

    fun indexFile(): File = File(storeDir(), INDEX_FILE)

    fun mintToken(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return HexFormat.of().formatHex(bytes)
    }

    fun digest(token: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val hash = md.digest(token.toByteArray(StandardCharsets.UTF_8))
        return HexFormat.of().formatHex(hash)
    }

    fun remember(token: String, deviceId: String, deviceName: String): Boolean {
        if (token.isBlank() || deviceId.isBlank()) return false
        return runCatching {
            val file = indexFile()
            val props = Properties()
            if (file.isFile) file.inputStream().use { props.load(it) }
            val key = digest(token)
            // Never store the raw token. deviceName is a display label only.
            props[key] = listOf(
                System.currentTimeMillis().toString(),
                sanitize(deviceId),
                sanitize(deviceName).take(64),
            ).joinToString("|")
            file.outputStream().use { props.store(it, "Telos LAN pairing token digests (no secrets)") }
            harden(file)
            true
        }.getOrDefault(false)
    }

    fun lookup(token: String): StoredPair? {
        if (token.isBlank()) return null
        val file = indexFile()
        if (!file.isFile) return null
        return runCatching {
            val props = Properties()
            file.inputStream().use { props.load(it) }
            val raw = props.getProperty(digest(token)) ?: return@runCatching null
            val parts = raw.split('|', limit = 3)
            if (parts.size < 2) return@runCatching null
            StoredPair(
                deviceId = parts.getOrElse(1) { "" },
                deviceName = parts.getOrElse(2) { "" },
                createdAtEpochMs = parts[0].toLongOrNull() ?: 0L,
            )
        }.getOrNull()
    }

    fun isAuthorized(token: String): Boolean = lookup(token) != null

    private fun sanitize(value: String): String =
        value.replace(Regex("[\\r\\n|=]"), "_").take(128)

    private fun harden(file: File) {
        runCatching {
            Files.setPosixFilePermissions(
                file.toPath(),
                setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
            )
        }
    }
}
