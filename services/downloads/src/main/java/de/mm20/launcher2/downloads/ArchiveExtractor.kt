package de.mm20.launcher2.downloads

import de.mm20.launcher2.downloads.logic.ArchiveLogic
import de.mm20.launcher2.downloads.logic.MimeTypes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipInputStream
import kotlin.coroutines.coroutineContext

/**
 * Unpacks a finished zip archive into a folder next to it (the chosen folder, below a folder named like the archive).
 * Every entry goes through a temporary file in the app storage first, because the target can be a Storage Access
 * Framework folder. Limits on the number and the total size protect against zip bombs.
 */
class ArchiveExtractor(private val files: DownloadFiles) {

    /** @return the number of files written */
    suspend fun extractZip(task: DownloadTask, defaultFolder: String): Int = withContext(Dispatchers.IO) {
        val uri = task.fileUri ?: throw DownloadException(ErrorKind.Storage, "No file", false)
        val input = files.openInput(uri) ?: throw DownloadException(ErrorKind.Storage, "The archive can not be read", false)
        val folder = task.treeUri ?: defaultFolder.ifBlank { null }
        val root = ArchiveLogic.folderName(task.name.ifBlank { "archive" })
        val tmpDir = File(files.mediaStagingDir("extract-" + task.id).path)
        var count = 0
        var total = 0L
        try {
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    coroutineContext.ensureActive()
                    val entry = zip.nextEntry ?: break
                    val segments = ArchiveLogic.targetSegments(entry.name, entry.isDirectory) ?: continue
                    if (++count > ArchiveLogic.MAX_ENTRIES) throw DownloadException(ErrorKind.Validation, "Too many files in the archive", false)
                    val tmp = File(tmpDir, "entry.tmp")
                    tmp.outputStream().use { out ->
                        val buffer = ByteArray(128 * 1024)
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = zip.read(buffer)
                            if (n < 0) break
                            total += n
                            if (total > ArchiveLogic.MAX_TOTAL_BYTES) throw DownloadException(ErrorKind.Validation, "The archive is too large", false)
                            out.write(buffer, 0, n)
                        }
                    }
                    files.copyTorrentFile(folder, listOf(root) + segments, tmp, MimeTypes.forName(segments.last()), { false }, {})
                    tmp.delete()
                }
            }
        } catch (e: DownloadException) {
            throw e
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            throw DownloadException(ErrorKind.Storage, "Can not extract: ${e.message}", false, e)
        } finally {
            tmpDir.deleteRecursively()
        }
        count
    }
}
