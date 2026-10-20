package de.mm20.launcher2.ui.terminal

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream

/** Constrains the existing experimental bootstrap path; does not assert Termux compatibility. */
internal object TerminalRuntimeArchive {
    fun extract(input: InputStream, target: File, maxBytes: Long = 256L * 1024 * 1024,
        maxEntryBytes: Long = 64L * 1024 * 1024, maxEntries: Int = 10_000, checkActive: () -> Unit = {}) {
        require(maxBytes > 0 && maxEntryBytes > 0 && maxEntries > 0)
        check(target.isDirectory || target.mkdirs())
        val root = target.canonicalFile
        val prefix = root.path + File.separator
        var total = 0L
        var count = 0
        val buffer = ByteArray(8192)
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                checkActive()
                if (++count > maxEntries) throw IOException("Runtime archive entry limit exceeded")
                val file = File(root, entry.name).canonicalFile
                if (!file.path.startsWith(prefix)) throw IOException("Invalid runtime archive path")
                if (entry.isDirectory) check(file.isDirectory || file.mkdirs())
                else {
                    check(file.parentFile!!.isDirectory || file.parentFile!!.mkdirs())
                    file.outputStream().use { output ->
                        var bytes = 0L
                        var size = zip.read(buffer)
                        while (size != -1) {
                            checkActive()
                            bytes += size
                            total += size
                            if (bytes > maxEntryBytes || total > maxBytes) throw IOException("Runtime archive size limit exceeded")
                            output.write(buffer, 0, size)
                            size = zip.read(buffer)
                        }
                    }
                    if (entry.name.startsWith("bin/")) file.setExecutable(true)
                }
                entry = zip.nextEntry
            }
        }
    }
}
