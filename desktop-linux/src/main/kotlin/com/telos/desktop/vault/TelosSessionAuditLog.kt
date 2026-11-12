package com.telos.desktop.vault

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.time.Instant

/**
 * Append-only session audit log under the vault (owner-only 0600).
 * Records unlock, network policy changes, profile switches, panic, and light attestation.
 * Never stores passwords or WireGuard private keys.
 */
object TelosSessionAuditLog {
    private val lock = Any()
    private val controlChars = Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]")

    fun auditDir(): File {
        val home = PortableEnvironmentIsolator.vaultHome
            ?: runCatching { PhantomVault.engine.mountPoint.takeIf { it.exists() } }.getOrNull()
            ?: File(System.getProperty("user.home"))
        return File(home, ".config/telos/opsec").apply { mkdirs() }
    }

    fun auditFile(): File = File(auditDir(), "audit.log")

    fun append(kind: String, detail: String = "") {
        synchronized(lock) {
            val file = auditFile()
            file.parentFile?.mkdirs()
            if (!file.exists()) {
                file.createNewFile()
            }
            setOwnerOnly(file)
            val line = buildString {
                append(Instant.now().toString())
                append('\t')
                append(sanitize(kind).ifBlank { "event" })
                val cleaned = sanitize(detail).take(500)
                if (cleaned.isNotEmpty()) {
                    append('\t')
                    append(cleaned)
                }
                append('\n')
            }
            file.appendText(line)
            setOwnerOnly(file)
        }
    }

    fun readTail(maxLines: Int = 80): List<String> {
        val file = auditFile()
        if (!file.isFile) return emptyList()
        val lines = runCatching { file.readLines() }.getOrDefault(emptyList())
        if (lines.isEmpty()) return emptyList()
        val n = maxLines.coerceAtLeast(1)
        return if (lines.size <= n) lines else lines.takeLast(n)
    }

    internal fun setOwnerOnly(file: File) {
        runCatching {
            Files.setPosixFilePermissions(
                file.toPath(),
                setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
            )
        }
        runCatching {
            file.setReadable(false, false)
            file.setWritable(false, false)
            file.setExecutable(false, false)
            file.setReadable(true, true)
            file.setWritable(true, true)
        }
    }

    private fun sanitize(value: String): String =
        value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')
            .replace(controlChars, "")
            .trim()
}
