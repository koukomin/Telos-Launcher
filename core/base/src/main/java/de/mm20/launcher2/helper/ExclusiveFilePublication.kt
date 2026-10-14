package de.mm20.launcher2.helper

import java.io.File
import java.io.IOException
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.WRITE
import java.nio.file.attribute.BasicFileAttributes

/** Exclusive bounded publication. Power loss can leave a partial output, never overwrite an existing file. */
object ExclusiveFilePublication {
    fun copy(temporary: File, destination: File, checkpoint: () -> Unit = {}): Any? {
        // CREATE_NEW is exclusive even on shared storage. Publish starts only after the archive
        // has been completed. Power loss during this final copy can leave a partial
        // destination; this intentionally does not claim atomic rename on emulated/FAT storage.
        var identity: Any? = null
        var created = false
        try {
            Files.newByteChannel(destination.toPath(), CREATE_NEW, WRITE).use { channel ->
                created = true
                identity = Files.readAttributes(destination.toPath(), BasicFileAttributes::class.java, NOFOLLOW_LINKS).fileKey()
                FileInputStream(temporary).use { source ->
                    val buffer = ByteArray(64 * 1024)
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
            return identity
        } catch (error: Throwable) {
            // Never delete a replacement created by somebody else. If filesystem identity is
            // unavailable, leaving our incomplete file is safer than deleting an unknown file.
            if (created) {
                val removed = runCatching {
                    if (!Files.exists(destination.toPath(), NOFOLLOW_LINKS)) true
                    else if (identity != null && Files.readAttributes(destination.toPath(), BasicFileAttributes::class.java, NOFOLLOW_LINKS).fileKey() == identity) {
                        Files.deleteIfExists(destination.toPath())
                    } else false
                }.getOrDefault(false)
                if (!removed) error.addSuppressed(IOException("Unfinished output retained: ${destination.name}"))
            }
            throw error
        }
    }
}
