package com.telos.desktop.vault

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.util.concurrent.atomic.AtomicLong

/**
 * Vault-local clipboard history: bounded, TTL-evicted, sensitive-scrubbed.
 *
 * Never writes to the host home outside an active vault/portable isolator.
 * Optional persistence is a single owner-only (0600) file under
 * `~/.config/telos/opsec/clipboard-history` inside the vault — no cloud sync.
 */
data class ClipboardHistoryItem(
    val id: Long,
    val text: String,
    val pinned: Boolean,
    val createdAtMs: Long,
    val ttlMs: Long,
) {
    fun expiresAtMs(): Long? = if (pinned || ttlMs <= 0L) null else createdAtMs + ttlMs

    fun isExpired(nowMs: Long): Boolean {
        val until = expiresAtMs() ?: return false
        return nowMs >= until
    }
}

class ClipboardHistoryStore(
    val maxItems: Int = DEFAULT_MAX_ITEMS,
    val defaultTtlMs: Long = DEFAULT_TTL_MS,
    private val looksSensitive: (String) -> Boolean,
    private val clockMs: () -> Long = { System.currentTimeMillis() },
    private val vaultOnlyPersist: Boolean = true,
) {
    private val lock = Any()
    private val seq = AtomicLong(1L)
    private val items = ArrayList<ClipboardHistoryItem>()

    fun snapshot(): List<ClipboardHistoryItem> = synchronized(lock) {
        expireLocked(clockMs())
        items.toList()
    }

    /**
     * Offer a clipboard string for history. Returns false when blank, duplicate
     * of the newest entry, or scrubbed as sensitive (IBAN / private key / WG key / …).
     */
    fun offer(text: String): Boolean = synchronized(lock) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        if (looksSensitive(trimmed)) {
            // Scrub: never retain sensitive material in history (OpSec vault is separate).
            items.removeAll { looksSensitive(it.text) }
            persistLocked()
            return false
        }
        expireLocked(clockMs())
        if (items.firstOrNull()?.text == trimmed) return false
        items.removeAll { it.text == trimmed && !it.pinned }
        items.add(
            0,
            ClipboardHistoryItem(
                id = seq.getAndIncrement(),
                text = trimmed,
                pinned = false,
                createdAtMs = clockMs(),
                ttlMs = defaultTtlMs,
            ),
        )
        trimBoundLocked()
        persistLocked()
        true
    }

    fun setPinned(id: Long, pinned: Boolean): Boolean = synchronized(lock) {
        val idx = items.indexOfFirst { it.id == id }
        if (idx < 0) return false
        val cur = items[idx]
        if (cur.pinned == pinned) return true
        items[idx] = cur.copy(pinned = pinned, createdAtMs = if (pinned) cur.createdAtMs else clockMs())
        persistLocked()
        true
    }

    fun togglePinned(id: Long): Boolean {
        val item = snapshot().firstOrNull { it.id == id } ?: return false
        return setPinned(id, !item.pinned)
    }

    /** Drop expired unpinned entries. Pinned items survive until [onLock]. */
    fun expire(nowMs: Long = clockMs()): Int = synchronized(lock) {
        expireLocked(nowMs)
    }

    fun clear() = synchronized(lock) {
        items.clear()
        deletePersistFile()
    }

    /** Pin-until-lock ends here: wipe history (memory + vault file). */
    fun onLock() = clear()

    fun size(): Int = synchronized(lock) { expireLocked(clockMs()); items.size }

    fun historyFile(): File = File(TelosSessionAuditLog.auditDir(), HISTORY_FILE_NAME)

    fun loadFromVault() {
        synchronized(lock) {
            if (!canPersist()) return
            val file = historyFile()
            if (!file.isFile) return
            val loaded = ArrayList<ClipboardHistoryItem>()
            val now = clockMs()
            file.readLines().forEach { line ->
                val parsed = parseLine(line) ?: return@forEach
                if (looksSensitive(parsed.text)) return@forEach
                if (parsed.isExpired(now)) return@forEach
                loaded.add(parsed.copy(id = seq.getAndIncrement()))
            }
            items.clear()
            items.addAll(loaded.take(maxItems))
        }
    }

    private fun expireLocked(nowMs: Long): Int {
        val before = items.size
        items.removeAll { it.isExpired(nowMs) || looksSensitive(it.text) }
        val removed = before - items.size
        if (removed > 0) persistLocked()
        return removed
    }

    private fun trimBoundLocked() {
        while (items.size > maxItems) {
            val drop = items.indexOfLast { !it.pinned }
            if (drop < 0) {
                // All pinned and over bound — drop oldest pinned.
                items.removeAt(items.lastIndex)
            } else {
                items.removeAt(drop)
            }
        }
    }

    private fun canPersist(): Boolean {
        if (!vaultOnlyPersist) return true
        // Only when isolator remapped HOME into the vault/portable session.
        return PortableEnvironmentIsolator.active && PortableEnvironmentIsolator.vaultHome != null
    }

    private fun persistLocked() {
        if (!canPersist()) return
        val file = historyFile()
        runCatching {
            file.parentFile?.mkdirs()
            val body = items.joinToString("\n") { encodeLine(it) } + if (items.isEmpty()) "" else "\n"
            file.writeText(body)
            setOwnerOnly(file)
        }
    }

    private fun deletePersistFile() {
        if (!canPersist()) return
        runCatching { historyFile().delete() }
    }

    companion object {
        const val DEFAULT_MAX_ITEMS = 20
        const val DEFAULT_TTL_MS = 5L * 60L * 1000L
        const val HISTORY_FILE_NAME = "clipboard-history"

        fun encodeLine(item: ClipboardHistoryItem): String {
            val pin = if (item.pinned) "1" else "0"
            val safe = item.text
                .replace("\\", "\\\\")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
            return "${item.createdAtMs}\t${item.ttlMs}\t$pin\t$safe"
        }

        fun parseLine(line: String): ClipboardHistoryItem? {
            if (line.isBlank() || line.startsWith("#")) return null
            val parts = line.split("\t", limit = 4)
            if (parts.size < 4) return null
            val created = parts[0].toLongOrNull() ?: return null
            val ttl = parts[1].toLongOrNull() ?: return null
            val pinned = parts[2] == "1"
            val text = parts[3]
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\\", "\\")
            if (text.isBlank()) return null
            return ClipboardHistoryItem(
                id = 0L,
                text = text,
                pinned = pinned,
                createdAtMs = created,
                ttlMs = ttl,
            )
        }

        fun setOwnerOnly(file: File) {
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
    }
}
