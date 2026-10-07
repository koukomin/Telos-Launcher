package de.mm20.launcher2.ui.files

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** A place files live: the phone's storage, the root file system. Others (network, cloud, vaults) can be added. */
interface Fs {
    /** True for the backend that works as the superuser */
    val isRoot: Boolean
    fun list(path: String): List<FsEntry>
    fun mkdir(parent: String, name: String): Boolean
    fun createFile(parent: String, name: String): Boolean
    fun rename(path: String, newName: String): Boolean
    fun delete(path: String): Boolean
    fun copy(src: String, dstDir: String, newName: String): Boolean
    fun move(src: String, dstDir: String, newName: String): Boolean
    fun chmod(path: String, mode: String): Boolean
    fun exists(path: String): Boolean
    /** Size of a file or of everything in a folder */
    fun totalSize(path: String): Long
}

/** A name that does not exist in [dir] yet: "photo.jpg", "photo (1).jpg", ... */
fun Fs.freeName(dir: String, wanted: String): String {
    if (!exists(joinPath(dir, wanted))) return wanted
    val dot = wanted.lastIndexOf('.')
    val base = if (dot > 0) wanted.substring(0, dot) else wanted
    val ext = if (dot > 0) wanted.substring(dot) else ""
    var i = 1
    while (true) {
        val candidate = "$base ($i)$ext"
        if (!exists(joinPath(dir, candidate))) return candidate
        i++
    }
}

class LocalFs : Fs {
    override val isRoot = false

    override fun list(path: String): List<FsEntry> {
        val files = File(path).listFiles() ?: throw IOException("Cannot open this folder")
        return files.map { f ->
            FsEntry(
                path = f.path,
                name = f.name,
                isDir = f.isDirectory,
                size = if (f.isDirectory) -1 else f.length(),
                modified = f.lastModified(),
                permissions = permissionsOf(f),
                isLink = runCatching { Files.isSymbolicLink(f.toPath()) }.getOrDefault(false),
            )
        }
    }

    private fun permissionsOf(f: File): String = runCatching {
        PosixFilePermissions.toString(Files.getPosixFilePermissions(f.toPath()))
    }.getOrElse { (if (f.canRead()) "r" else "-") + (if (f.canWrite()) "w" else "-") + (if (f.canExecute()) "x" else "-") }

    override fun mkdir(parent: String, name: String) = File(parent, name).mkdirs()
    override fun createFile(parent: String, name: String) = runCatching { File(parent, name).createNewFile() }.getOrDefault(false)
    override fun rename(path: String, newName: String): Boolean {
        val f = File(path)
        return f.renameTo(File(f.parentFile, newName))
    }
    override fun delete(path: String) = File(path).deleteRecursively()
    override fun copy(src: String, dstDir: String, newName: String): Boolean = runCatching {
        File(src).copyRecursively(File(dstDir, newName), overwrite = false)
    }.getOrDefault(false)
    override fun move(src: String, dstDir: String, newName: String): Boolean {
        val from = File(src)
        val to = File(dstDir, newName)
        if (from.renameTo(to)) return true
        // another volume: copy, then remove the original
        return runCatching { from.copyRecursively(to, overwrite = false) && from.deleteRecursively() }.getOrDefault(false)
    }
    override fun chmod(path: String, mode: String) = false
    override fun exists(path: String) = File(path).exists()
    override fun totalSize(path: String): Long = File(path).walkTopDown().filter { it.isFile }.sumOf { it.length() }
}

class ShellResult(val code: Int, val out: String, val err: String) { val ok get() = code == 0 }

/** Runs commands as the superuser (`su`). Only used after the user switched root access on. */
object RootShell {
    @Volatile private var checked: Boolean? = null

    fun q(path: String) = "'" + path.replace("'", "'\\''") + "'"

    fun run(command: String, timeoutSeconds: Long = 60): ShellResult = runCatching {
        val process = ProcessBuilder("su", "-c", command).start()
        var out = ""
        var err = ""
        val outThread = Thread { out = process.inputStream.bufferedReader().readText() }.also { it.start() }
        val errThread = Thread { err = process.errorStream.bufferedReader().readText() }.also { it.start() }
        val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        if (!finished) process.destroyForcibly()
        outThread.join(2000); errThread.join(2000)
        ShellResult(if (finished) process.exitValue() else -1, out, err)
    }.getOrElse { ShellResult(-1, "", it.message.orEmpty()) }

    /** Whether the superuser grants access. Asking makes the root manager show its dialog. */
    fun available(force: Boolean = false): Boolean {
        if (!force) checked?.let { return it }
        val result = run("id", 20)
        return (result.ok && result.out.contains("uid=0")).also { checked = it }
    }
}

class RootFs : Fs {
    override val isRoot = true

    private val line = Regex("""^([-dlcbps])([rwxsStT-]{9})[.+]?\s+\d+\s+(\S+)\s+(\S+)\s+(?:\d+,\s+)?(\d+)\s+(\d{4}-\d{2}-\d{2})\s+(\d{2}:\d{2})\s+(.*)$""")
    private val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

    override fun list(path: String): List<FsEntry> {
        val result = RootShell.run("ls -lA ${RootShell.q(path)}")
        if (!result.ok && result.out.isBlank()) throw IOException(result.err.ifBlank { "Cannot open this folder" })
        val entries = result.out.lineSequence().mapNotNull { text ->
            val m = line.find(text.trimEnd()) ?: return@mapNotNull null
            val (type, perms, owner, _, size, day, time, rest) = m.destructured
            val isLink = type == "l"
            val name = if (isLink) rest.substringBefore(" -> ") else rest
            FsEntry(
                path = joinPath(path, name),
                name = name,
                isDir = type == "d",
                size = if (type == "d") -1 else size.toLongOrNull() ?: 0,
                modified = runCatching { date.parse("$day $time")!!.time }.getOrDefault(0),
                permissions = perms,
                owner = owner,
                isLink = isLink,
                linkTarget = if (isLink) rest.substringAfter(" -> ", "") else null,
            )
        }.toList()
        // a link to a folder opens like a folder
        val links = entries.filter { it.isLink }
        if (links.isEmpty()) return entries
        val check = links.joinToString("; ") { "[ -d ${RootShell.q(it.path)} ] && echo ${RootShell.q(it.path)}" }
        val dirs = RootShell.run(check).out.lines().toSet()
        return entries.map { if (it.isLink && it.path in dirs) it.copy(isDir = true) else it }
    }

    private fun ok(command: String) = RootShell.run(command).ok

    override fun mkdir(parent: String, name: String) = ok("mkdir -p ${RootShell.q(joinPath(parent, name))}")
    override fun createFile(parent: String, name: String) = ok("touch ${RootShell.q(joinPath(parent, name))}")
    override fun rename(path: String, newName: String) = ok("mv ${RootShell.q(path)} ${RootShell.q(joinPath(parentOf(path) ?: "/", newName))}")
    override fun delete(path: String) = ok("rm -rf ${RootShell.q(path)}")
    override fun copy(src: String, dstDir: String, newName: String) = ok("cp -a ${RootShell.q(src)} ${RootShell.q(joinPath(dstDir, newName))}")
    override fun move(src: String, dstDir: String, newName: String) = ok("mv ${RootShell.q(src)} ${RootShell.q(joinPath(dstDir, newName))}")
    override fun chmod(path: String, mode: String) = mode.all { it in '0'..'7' } && mode.length in 3..4 && ok("chmod $mode ${RootShell.q(path)}")
    override fun exists(path: String) = ok("[ -e ${RootShell.q(path)} ] || [ -L ${RootShell.q(path)} ]")
    override fun totalSize(path: String): Long =
        RootShell.run("du -sk ${RootShell.q(path)}").out.trim().substringBefore('\t').substringBefore(' ').toLongOrNull()?.times(1024) ?: -1

    /** Mounts a partition (such as /system) writable or read-only again. Many devices refuse this. */
    fun remount(mountPoint: String, writable: Boolean) =
        ok("mount -o remount,${if (writable) "rw" else "ro"} ${RootShell.q(mountPoint)}")
}

/** Cancels a running operation */
class CancelFlag { @Volatile var cancelled = false }

/** Copying, moving and deleting many files with progress. [progress] gets (done, total) in bytes. */
object FsOps {

    fun copyTree(src: File, dst: File, cancel: CancelFlag, progress: (Long) -> Unit) {
        if (cancel.cancelled) throw IOException("Cancelled")
        if (src.isDirectory) {
            dst.mkdirs()
            src.listFiles()?.forEach { copyTree(it, File(dst, it.name), cancel, progress) }
        } else {
            src.inputStream().use { input ->
                dst.outputStream().use { output ->
                    val buffer = ByteArray(256 * 1024)
                    while (true) {
                        if (cancel.cancelled) throw IOException("Cancelled")
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        progress(n.toLong())
                    }
                }
            }
            dst.setLastModified(src.lastModified())
        }
    }

    fun sizeOf(file: File): Long = if (file.isDirectory) file.walkTopDown().filter { it.isFile }.sumOf { it.length() } else file.length()

    /** Packs [sources] into a zip file at [target] */
    fun zip(sources: List<File>, target: File, cancel: CancelFlag, progress: (Long) -> Unit) {
        ZipOutputStream(target.outputStream().buffered()).use { zip ->
            fun add(file: File, entryName: String) {
                if (cancel.cancelled) throw IOException("Cancelled")
                if (file.isDirectory) {
                    zip.putNextEntry(ZipEntry("$entryName/"))
                    zip.closeEntry()
                    file.listFiles()?.forEach { add(it, "$entryName/${it.name}") }
                } else {
                    zip.putNextEntry(ZipEntry(entryName).apply { time = file.lastModified() })
                    file.inputStream().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            zip.write(buffer, 0, n)
                            progress(n.toLong())
                        }
                    }
                    zip.closeEntry()
                }
            }
            sources.forEach { add(it, it.name) }
        }
    }

    /** Unpacks a zip file into [targetDir]. A file that would end up outside it is skipped. */
    fun unzip(zipFile: File, targetDir: File, cancel: CancelFlag, progress: (Long) -> Unit) {
        targetDir.mkdirs()
        val root = targetDir.canonicalPath + File.separator
        ZipFile(zipFile).use { zip ->
            for (entry in zip.entries()) {
                if (cancel.cancelled) throw IOException("Cancelled")
                val out = File(targetDir, entry.name)
                if (!out.canonicalPath.startsWith(root)) continue
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        out.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val n = input.read(buffer)
                                if (n < 0) break
                                output.write(buffer, 0, n)
                                progress(n.toLong())
                            }
                        }
                    }
                }
            }
        }
    }

    fun hash(file: File, algorithm: String): String {
        val digest = MessageDigest.getInstance(algorithm)
        file.inputStream().use { input ->
            val buffer = ByteArray(256 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
