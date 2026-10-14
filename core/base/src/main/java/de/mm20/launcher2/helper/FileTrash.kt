package de.mm20.launcher2.helper

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.util.Properties
import java.util.UUID

/** Atomic same-volume trash, with durable metadata written before moving the original. */
class FileTrash(private val directory: File, private val allowedRoot: File, private val now: () -> Long = System::currentTimeMillis) {
    data class Entry(val id: String, val name: String, val original: String, val deletedAt: Long, val expiresAt: Long, val file: File)
    private val lock = Any()
    private fun allowed(file: File): Boolean {
        val root = allowedRoot.canonicalFile
        val candidate = file.canonicalFile
        val trash = directory.canonicalFile
        return candidate != root && candidate.toPath().startsWith(root.toPath()) &&
            !candidate.toPath().startsWith(trash.toPath()) && !trash.toPath().startsWith(candidate.toPath()) &&
            !Files.isSymbolicLink(file.toPath())
    }
    fun moveToTrash(source: File, retentionDays: Int): Entry = synchronized(lock) {
        require(retentionDays in 1..365)
        require(source.exists() && allowed(source)) { "This path cannot be moved to this trash" }
        val original = source.canonicalFile
        val id = UUID.randomUUID().toString()
        val folder = File(directory, id)
        if (!folder.mkdirs()) throw IOException("Could not create trash entry")
        val payload = File(folder, "content")
        val at = now()
        val expires = at + retentionDays * 86_400_000L
        val meta = Properties().apply {
            setProperty("id", id); setProperty("name", source.name); setProperty("original", original.path)
            setProperty("deletedAt", at.toString()); setProperty("expiresAt", expires.toString())
        }
        try {
            FileOutputStream(File(folder, "metadata.properties")).use { output -> meta.store(output, null); output.fd.sync() }
            // No copy/delete fallback: if the move fails, the original remains untouched.
            Files.move(original.toPath(), payload.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE)
        } catch (e: Exception) {
            if (!payload.exists()) deleteTree(folder)
            throw e
        }
        Entry(id, source.name, original.path, at, expires, payload)
    }
    fun entries(): List<Entry> = synchronized(lock) {
        directory.listFiles().orEmpty().mapNotNull(::read).sortedByDescending { it.deletedAt }
    }
    private fun read(folder: File): Entry? = runCatching {
        if (!folder.isDirectory || Files.isSymbolicLink(folder.toPath()) || !ID.matches(folder.name)) return@runCatching null
        val metadata = File(folder, "metadata.properties")
        if (!metadata.isFile || metadata.length() > 64 * 1024 || Files.isSymbolicLink(metadata.toPath())) return@runCatching null
        val props = Properties().apply { metadata.inputStream().use(::load) }
        val payload = File(folder, "content")
        if (!payload.exists() || Files.isSymbolicLink(payload.toPath())) return@runCatching null
        val original = props.getProperty("original") ?: return@runCatching null
        val at = props.getProperty("deletedAt")?.toLongOrNull() ?: return@runCatching null
        val expires = props.getProperty("expiresAt")?.toLongOrNull()?.takeIf { it > at } ?: return@runCatching null
        val originalPath = File(original).toPath().normalize()
        val rootPath = allowedRoot.canonicalFile.toPath()
        val trashPath = directory.canonicalFile.toPath()
        if (props.getProperty("id") != folder.name || !originalPath.isAbsolute || originalPath == rootPath ||
            !originalPath.startsWith(rootPath) || originalPath.startsWith(trashPath) || trashPath.startsWith(originalPath)) return@runCatching null
        Entry(folder.name, props.getProperty("name", File(original).name), original, at, expires, payload)
    }.getOrNull()
    fun restore(id: String): File = synchronized(lock) {
        require(ID.matches(id))
        val entry = read(File(directory, id)) ?: throw IOException("Trash entry unavailable")
        val destination = File(entry.original)
        // A changed symlink in the original path must not redirect restoration.
        require(destination.canonicalPath == entry.original && allowed(destination))
        if (destination.exists()) throw IOException("A file already exists at the original path")
        val parent = destination.parentFile ?: throw IOException("Original folder unavailable")
        if (!parent.isDirectory && !parent.mkdirs()) throw IOException("Could not restore original folder")
        Files.move(entry.file.toPath(), destination.toPath())
        entry.file.parentFile?.let(::deleteTree)
        destination
    }
    fun deletePermanently(id: String): Boolean = synchronized(lock) {
        require(ID.matches(id))
        val entry = read(File(directory, id)) ?: return@synchronized false
        entry.file.parentFile?.let(::deleteTree) == true
    }
    fun purgeExpired(): Int = synchronized(lock) {
        var removed = 0
        for (entry in entries()) if (entry.expiresAt <= now() && deletePermanently(entry.id)) removed++
        removed
    }
    private fun deleteTree(folder: File): Boolean = try {
        Files.walkFileTree(folder.toPath(), object : java.nio.file.SimpleFileVisitor<java.nio.file.Path>() {
            override fun visitFile(file: java.nio.file.Path, attrs: java.nio.file.attribute.BasicFileAttributes): java.nio.file.FileVisitResult {
                Files.delete(file); return java.nio.file.FileVisitResult.CONTINUE
            }
            override fun postVisitDirectory(dir: java.nio.file.Path, error: IOException?): java.nio.file.FileVisitResult {
                if (error != null) throw error
                Files.delete(dir); return java.nio.file.FileVisitResult.CONTINUE
            }
        })
        true
    } catch (_: IOException) { false }
    private companion object { val ID = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}") }
}
