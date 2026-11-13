package com.telos.desktop.search

import com.telos.desktop.vault.BootMode
import com.telos.desktop.vault.HardwareCryptoAccelerator
import com.telos.desktop.vault.PhantomVault
import com.telos.desktop.vault.PortableEnvironmentIsolator
import com.telos.desktop.vault.PortableTraceGuard
import com.telos.desktop.vault.TelosSessionHome
import com.telos.desktop.xdg.XdgUserDirs
import com.telos.desktop.xdg.XdgUserDirsManager
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.util.Properties
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Opt-in, vault-local file **name** index for DesktopSearch / Files.
 *
 * Privacy rules:
 * - Indexes only under vault HOME / Portable Data roots (never the host profile
 *   unless BootMode.HostHome).
 * - Stores an AES-GCM blob under `{session}/.cache/telos/file-index` with
 *   owner-only permissions.
 * - Filenames (+ optional extension) only — no file content, no cloud upload.
 * - Disabled by default; enable via Settings → OpSec / privacy toggle.
 */
data class IndexedFileName(
    val absolutePath: String,
    val fileName: String,
    val extension: String,
) {
    val file: File get() = File(absolutePath)
}

object VaultFileNameIndex {
    const val REL_DIR = ".cache/telos/file-index"
    const val BLOB_NAME = "names.enc"
    const val META_NAME = "meta.properties"
    const val DEFAULT_MIN_RESCAN_MS = 60_000L
    const val DEFAULT_MAX_ENTRIES = 50_000
    const val DEFAULT_MAX_DEPTH = 12

    private val MAGIC = byteArrayOf('T'.code.toByte(), 'F'.code.toByte(), 'I'.code.toByte(), '1'.code.toByte())
    private const val PURPOSE = "TelosDesktopVaultFileNameIndex/v1"

    @Volatile
    var enabled: Boolean = false

    private val cached = AtomicReference<List<IndexedFileName>>(emptyList())
    private val lastScanAt = AtomicLong(0L)
    private val scanLock = Any()

    fun indexDir(sessionHome: File = resolveSessionHome()): File =
        File(sessionHome, REL_DIR)

    fun resolveSessionHome(): File {
        TelosSessionHome.vaultMount?.takeIf { it.isDirectory }?.let { return it }
        PortableEnvironmentIsolator.vaultHome?.takeIf { it.isDirectory }?.let { return it }
        if (PortableTraceGuard.isPortable()) return PortableTraceGuard.dataRoot()
        return File(System.getProperty("user.home"))
    }

    fun isHostHomeMode(
        bootMode: BootMode? = runCatching { PhantomVault.engine.bootMode }.getOrNull(),
    ): Boolean {
        if (bootMode == BootMode.HostHome) return true
        val prop = System.getProperty("telos.boot.mode")?.trim()
        return prop.equals("HostHome", ignoreCase = true)
    }

    /**
     * Roots that may be scanned. Host profile paths are excluded unless
     * [isHostHomeMode] is true.
     */
    fun allowedRoots(
        sessionHome: File = resolveSessionHome(),
        hostHome: File = PortableTraceGuard.hostProfileHome(),
        portableRoot: File? = if (PortableTraceGuard.isPortable()) PortableTraceGuard.dataRoot() else null,
        hostHomeMode: Boolean = isHostHomeMode(),
        xdg: XdgUserDirs = XdgUserDirsManager.resolve(),
    ): List<File> {
        val bases = allowedBases(sessionHome, hostHome, portableRoot, hostHomeMode)
        if (bases.isEmpty()) return emptyList()
        val candidates = listOf(
            xdg.desktop,
            xdg.download,
            xdg.documents,
            xdg.music,
            xdg.pictures,
            xdg.videos,
            xdg.home,
            sessionHome,
            portableRoot,
        ).filterNotNull()
        return candidates
            .mapNotNull { runCatching { it.canonicalFile }.getOrNull() }
            .filter { it.isDirectory && bases.any { base -> isSelfOrChild(it, base) } }
            .distinctBy { it.absolutePath }
    }

    fun allowedBases(
        sessionHome: File = resolveSessionHome(),
        hostHome: File = PortableTraceGuard.hostProfileHome(),
        portableRoot: File? = if (PortableTraceGuard.isPortable()) PortableTraceGuard.dataRoot() else null,
        hostHomeMode: Boolean = isHostHomeMode(),
    ): List<File> {
        val host = runCatching { hostHome.canonicalFile }.getOrElse { hostHome.absoluteFile }
        val session = runCatching { sessionHome.canonicalFile }.getOrElse { sessionHome.absoluteFile }
        val portable = portableRoot?.let { root ->
            runCatching { root.canonicalFile }.getOrElse { root.absoluteFile }
        }
        if (hostHomeMode) {
            return listOfNotNull(host, session, portable).distinctBy { it.absolutePath }
        }
        val out = ArrayList<File>(3)
        if (portable != null) out += portable
        val vaultish =
            TelosSessionHome.vaultMount != null ||
                PortableEnvironmentIsolator.active ||
                PortableTraceGuard.isPortable() ||
                session != host
        if (vaultish) {
            if (session != host || portable != null || TelosSessionHome.vaultMount != null || PortableEnvironmentIsolator.active) {
                out += session
            }
        }
        // Privacy-first: with no vault/portable and not Host Home, refuse host profiling.
        return out.distinctBy { it.absolutePath }
    }

    fun isUnderAllowedRoot(file: File, roots: List<File> = allowedRoots()): Boolean {
        val target = runCatching { file.canonicalFile }.getOrElse { file.absoluteFile }
        return roots.any { isSelfOrChild(target, it) }
    }

    fun isSelfOrChild(child: File, parent: File): Boolean {
        val c = runCatching { child.canonicalFile }.getOrElse { child.absoluteFile }
        val p = runCatching { parent.canonicalFile }.getOrElse { parent.absoluteFile }
        if (c == p) return true
        val prefix = p.absolutePath.trimEnd(File.separatorChar) + File.separator
        return c.absolutePath.startsWith(prefix)
    }

    fun query(
        rawQuery: String,
        limit: Int = 80,
        entries: List<IndexedFileName> = cached.get(),
    ): List<IndexedFileName> {
        val q = rawQuery.trim()
        if (q.isEmpty() || q.length < 2) return emptyList()
        val lower = q.lowercase()
        val extOnly = lower.removePrefix(".")
        val out = ArrayList<IndexedFileName>(limit.coerceAtMost(64))
        for (entry in entries) {
            if (out.size >= limit) break
            val name = entry.fileName.lowercase()
            val ext = entry.extension.lowercase()
            val hit =
                name == lower ||
                    name.startsWith(lower) ||
                    name.contains(lower) ||
                    (extOnly.isNotEmpty() && (ext == extOnly || name.endsWith(".$extOnly")))
            if (hit) out += entry
        }
        return out.sortedWith(
            compareByDescending<IndexedFileName> { score(lower, it.fileName) }
                .thenBy { it.fileName.lowercase() },
        )
    }

    fun score(queryLower: String, fileName: String): Int {
        val t = fileName.lowercase()
        return when {
            t == queryLower -> 100
            t.startsWith(queryLower) -> 80
            t.contains(queryLower) -> 50
            else -> 30
        }
    }

    fun snapshot(): List<IndexedFileName> = cached.get()

    fun clearMemory() {
        cached.set(emptyList())
        lastScanAt.set(0L)
    }

    fun clearOnDisk(sessionHome: File = resolveSessionHome()) {
        clearMemory()
        val dir = indexDir(sessionHome)
        runCatching { File(dir, BLOB_NAME).delete() }
        runCatching { File(dir, META_NAME).delete() }
    }

    /**
     * Incremental rescan, throttled. No-op when [enabled] is false.
     * Never uploads; writes only under the vault/portable cache dir.
     */
    fun maybeRescan(
        force: Boolean = false,
        minIntervalMs: Long = DEFAULT_MIN_RESCAN_MS,
        maxEntries: Int = DEFAULT_MAX_ENTRIES,
        maxDepth: Int = DEFAULT_MAX_DEPTH,
        sessionHome: File = resolveSessionHome(),
        roots: List<File> = allowedRoots(sessionHome = sessionHome),
        nowMs: Long = System.currentTimeMillis(),
    ): Int {
        if (!enabled) return 0
        if (!force && nowMs - lastScanAt.get() < minIntervalMs && cached.get().isNotEmpty()) {
            return cached.get().size
        }
        synchronized(scanLock) {
            if (!force && nowMs - lastScanAt.get() < minIntervalMs && cached.get().isNotEmpty()) {
                return cached.get().size
            }
            val entries = scanRoots(roots, maxEntries, maxDepth)
            cached.set(entries)
            lastScanAt.set(nowMs)
            persist(sessionHome, entries, nowMs)
            return entries.size
        }
    }

    fun loadFromDisk(sessionHome: File = resolveSessionHome()): List<IndexedFileName> {
        val blob = File(indexDir(sessionHome), BLOB_NAME)
        if (!blob.isFile) return emptyList()
        val decrypted = decryptBlob(blob.readBytes()) ?: return emptyList()
        val roots = allowedRoots(sessionHome = sessionHome)
        val entries = ArrayList<IndexedFileName>()
        for (line in decrypted.lineSequence()) {
            val path = line.trim()
            if (path.isEmpty()) continue
            val file = File(path)
            if (!isUnderAllowedRoot(file, roots)) continue
            entries += toEntry(file)
        }
        cached.set(entries)
        return entries
    }

    fun scanRoots(
        roots: List<File>,
        maxEntries: Int = DEFAULT_MAX_ENTRIES,
        maxDepth: Int = DEFAULT_MAX_DEPTH,
    ): List<IndexedFileName> {
        val out = ArrayList<IndexedFileName>(minOf(maxEntries, 1024))
        val seen = HashSet<String>()
        val indexCachePath = runCatching {
            indexDir().canonicalFile.absolutePath
        }.getOrNull()
        for (root in roots) {
            if (out.size >= maxEntries) break
            walk(root, maxDepth) { file ->
                if (out.size >= maxEntries) return@walk false
                val path = runCatching { file.canonicalFile.absolutePath }.getOrElse { file.absolutePath }
                if (indexCachePath != null && (path == indexCachePath || path.startsWith(indexCachePath + File.separator))) {
                    return@walk true
                }
                if (!seen.add(path)) return@walk true
                if (file.isFile) out += toEntry(file)
                true
            }
        }
        return out
    }

    private fun walk(root: File, maxDepth: Int, visit: (File) -> Boolean) {
        if (!root.isDirectory) return
        val queue = ArrayDeque<Pair<File, Int>>()
        queue.add(root to 0)
        while (queue.isNotEmpty()) {
            val (dir, depth) = queue.removeFirst()
            val children = dir.listFiles() ?: continue
            for (child in children) {
                if (!visit(child)) return
                if (child.isDirectory && depth < maxDepth) {
                    val name = child.name
                    // Skip common noise / circular cache; still allow ordinary folders.
                    if (name == "." || name == "..") continue
                    if (name == "file-index" && dir.name == "telos") continue
                    queue.addLast(child to depth + 1)
                }
            }
        }
    }

    private fun toEntry(file: File): IndexedFileName {
        val name = file.name
        val dot = name.lastIndexOf('.')
        val ext = if (dot > 0 && dot < name.length - 1) name.substring(dot + 1) else ""
        return IndexedFileName(
            absolutePath = file.absolutePath,
            fileName = name,
            extension = ext,
        )
    }

    private fun persist(sessionHome: File, entries: List<IndexedFileName>, nowMs: Long) {
        val dir = indexDir(sessionHome)
        dir.mkdirs()
        hardenDir(dir)
        val plaintext = entries.joinToString("\n") { it.absolutePath }.toByteArray(StandardCharsets.UTF_8)
        try {
            val blob = encryptBlob(plaintext)
            val out = File(dir, BLOB_NAME)
            out.writeBytes(blob)
            hardenFile(out)
        } finally {
            plaintext.fill(0)
        }
        val meta = Properties()
        meta["lastScanMs"] = nowMs.toString()
        meta["count"] = entries.size.toString()
        meta["version"] = "1"
        val metaFile = File(dir, META_NAME)
        metaFile.outputStream().use { meta.store(it, "Telos vault-local file name index (no content)") }
        hardenFile(metaFile)
    }

    internal fun encryptBlob(plaintext: ByteArray): ByteArray {
        val salt = HardwareCryptoAccelerator.random(16)
        val iv = HardwareCryptoAccelerator.random(12)
        val key = wrapKey(salt)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            val ct = cipher.doFinal(plaintext)
            return MAGIC + salt + iv + ct
        } finally {
            key.fill(0)
        }
    }

    internal fun decryptBlob(raw: ByteArray): String? {
        if (raw.size < MAGIC.size + 16 + 12 + 16) return null
        if (!raw.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) return null
        var o = MAGIC.size
        val salt = raw.copyOfRange(o, o + 16)
        o += 16
        val iv = raw.copyOfRange(o, o + 12)
        o += 12
        val ct = raw.copyOfRange(o, raw.size)
        val key = wrapKey(salt)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            val pt = cipher.doFinal(ct)
            try {
                String(pt, StandardCharsets.UTF_8)
            } finally {
                pt.fill(0)
            }
        } catch (_: Exception) {
            null
        } finally {
            key.fill(0)
        }
    }

    private fun wrapKey(salt: ByteArray): ByteArray =
        HardwareCryptoAccelerator.hmacSha512(PURPOSE.toByteArray(StandardCharsets.UTF_8), salt).copyOf(32)

    private fun hardenDir(dir: File) {
        runCatching {
            Files.setPosixFilePermissions(
                dir.toPath(),
                setOf(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE,
                ),
            )
        }
    }

    private fun hardenFile(file: File) {
        runCatching {
            Files.setPosixFilePermissions(
                file.toPath(),
                setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
            )
        }
    }
}
