package de.mm20.launcher2.backup

import android.content.Context
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.*
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class BackupManager(
    private val context: Context,
    private val components: List<Backupable>,
) {
    private val scope = CoroutineScope(Dispatchers.Default + Job())

    /**
     * Create a backup
     * @param groups the parts to put into the backup, all of them by default
     */
    suspend fun backup(
        uri: Uri,
        groups: Set<BackupGroup> = BackupGroup.entries.toSet(),
    ) {

        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)

        val meta = BackupMetadata(
            appVersionName = packageInfo.versionName ?: "",
            timestamp = System.currentTimeMillis(),
            deviceName = Build.MODEL,
            format = BackupFormat,
            groups = groups,
        )

        withContext(Dispatchers.IO) {
            val outputStream = context.contentResolver.openOutputStream(uri) ?: return@withContext null
            val backupDir = File(context.cacheDir, "backup")
            if (backupDir.exists()) {
                backupDir.deleteRecursively()
            }
            backupDir.mkdirs()

            val metaFile = File(backupDir, "meta")
            meta.writeToFile(metaFile)

            for (component in components) {
                if (component.group in groups) component.backup(backupDir)
            }

            createArchive(backupDir, outputStream)
            outputStream.close()

        }
    }

    /**
     * Restores a backup
     * @param groups the parts to restore, all that the backup contains by default. A part that the
     * backup does not contain is never touched.
     */
    suspend fun restore(
        uri: Uri,
        groups: Set<BackupGroup>? = null,
    ) {
        val job = scope.launch {
            withContext(Dispatchers.IO) {
                val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext
                val restoreDir = File(context.cacheDir, "restore")
                if (restoreDir.exists()) {
                    restoreDir.deleteRecursively()
                }
                restoreDir.mkdirs()
                extractArchive(inputStream, restoreDir)
                inputStream.close()

                // a backup from before the groups only has the launcher part
                val inBackup = readMetaFromDir(restoreDir)?.groups ?: setOf(BackupGroup.Launcher)
                val chosen = if (groups == null) inBackup else groups intersect inBackup
                for (component in components) {
                    if (component.group in chosen) component.restore(restoreDir)
                }
            }
        }
        job.join()
    }

    private suspend fun readMetaFromDir(dir: File): BackupMetadata? {
        val file = File(dir, "meta")
        if (!file.exists()) return null
        return file.inputStream().use { BackupMetadata.fromInputStream(it) }
    }

    suspend fun readBackupMeta(uri: Uri): BackupMetadata? {
        return withContext(Dispatchers.IO) {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
            val zipStream = ZipInputStream(inputStream)
            var entry = zipStream.nextEntry
            while(entry != null) {
                if (entry.name == "meta") {
                    val metadata = BackupMetadata.fromInputStream(zipStream)
                    zipStream.close()
                    return@withContext metadata
                }

                zipStream.closeEntry()

                entry = zipStream.nextEntry
            }
            return@withContext null
        }
    }

    private suspend fun createArchive(dir: File, outputStream: OutputStream) = withContext(Dispatchers.IO){
        val zipStream = ZipOutputStream(outputStream)

        // components may write sub folders (notes/, calendar/), those go into the archive with their path
        val fileList = dir.walkTopDown().filter { it.isFile }.toList()

        for (file in fileList) {
            zipStream.putNextEntry(ZipEntry(file.relativeTo(dir).invariantSeparatorsPath))
            file.inputStream().use {
                it.copyTo(zipStream)
            }
            zipStream.closeEntry()
        }
        zipStream.close()
    }

    private suspend fun extractArchive(inputStream: InputStream, outDir: File) = withContext(Dispatchers.IO) {
        val zipStream = ZipInputStream(inputStream)
        var entry = zipStream.nextEntry
        while(entry != null) {
            val file = File(outDir, entry.name)
            // a crafted backup must not write outside the folder it is unpacked into
            val inside = file.canonicalPath.startsWith(outDir.canonicalPath + File.separator)
            if (inside && !entry.isDirectory) {
                file.parentFile?.mkdirs()
                file.outputStream().use {
                    zipStream.copyTo(it)
                }
            }
            zipStream.closeEntry()

            entry = zipStream.nextEntry
        }
    }

    fun checkCompatibility(meta: BackupMetadata): BackupCompatibility {
        val format = meta.format.split(".")
        val x = format.getOrNull(0)?.toIntOrNull() ?: return BackupCompatibility.Incompatible
        val y = format.getOrNull(1)?.toIntOrNull() ?: return BackupCompatibility.Incompatible
        if (x != BackupFormatMajor) return BackupCompatibility.Incompatible
        if (y != BackupFormatMinor) return BackupCompatibility.PartiallyCompatible
        return BackupCompatibility.Compatible
    }

    companion object {
        /**
         * Format changelog:
         * - 1.5: added `weight` to favorites
         * - 1.9: migrate from proto to json data store
         * - 1.10: backup groups (launcher, notes, calendar), listed in the meta file
         * - 1.11: backup group downloads (download list and settings of Telos Downloads)
         */

        private const val BackupFormatMajor = 1
        private const val BackupFormatMinor = 11
        internal const val BackupFormat = "$BackupFormatMajor.$BackupFormatMinor"
    }
}

enum class BackupCompatibility {
    /**
     * Fully compatible, can be fully restored
     */
    Compatible,

    /**
     * Incompatible, cannot be restored
     */
    Incompatible,

    /**
     * Compatible but has been created on a different version and parts of the backup use a different format
     * or were not supported / are not supported anymore so parts of the backup might not be restored.
     */
    PartiallyCompatible
}