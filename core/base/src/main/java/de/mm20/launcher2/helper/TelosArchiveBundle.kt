package de.mm20.launcher2.helper

import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** A portable archive with user-supplied metadata and a checksum of the original bytes. */
object TelosArchiveBundle {
    fun create(
        source: File,
        output: File,
        staging: File,
        title: String,
        notes: String,
        license: String?,
        checkpoint: () -> Unit,
    ) {
        require(Files.isRegularFile(source.toPath(), NOFOLLOW_LINKS)) { "Expected a regular file" }
        require(title.length <= 200 && notes.length <= 2000)
        val archive = File(staging, "archive.zip")
        val entry = "media/${source.name.replace('\\', '_')}"
        val digest = MessageDigest.getInstance("SHA-256")
        ZipOutputStream(archive.outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(entry))
            Files.newInputStream(source.toPath(), NOFOLLOW_LINKS).use { input ->
                val buffer = ByteArray(65536)
                while (true) {
                    checkpoint()
                    val count = input.read(buffer)
                    if (count < 0) break
                    zip.write(buffer, 0, count)
                    digest.update(buffer, 0, count)
                }
            }
            zip.closeEntry()
            val metadata = JSONObject()
                .put("format", "telos-archive-v1")
                .put("file", entry)
                .put("title", title)
                .put("notes", notes)
                .put("license", license ?: JSONObject.NULL)
                .put("sha256", digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) })
            zip.putNextEntry(ZipEntry("metadata.json"))
            zip.write(metadata.toString(2).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        PasswordFileEncryption.publish(archive, output, checkpoint)
    }
}
