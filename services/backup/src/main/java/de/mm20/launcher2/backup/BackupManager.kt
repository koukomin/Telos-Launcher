package de.mm20.launcher2.backup

import android.content.Context
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.*
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class BackupManager(
    private val context: Context,
    private val components: List<Backupable>,
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /**
     * Create a backup
     * @param groups the parts to put into the backup, all of them by default
     */
    suspend fun backup(
        uri: Uri,
        groups: Set<BackupGroup> = BackupGroup.entries.toSet(),
        password: String? = null,
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
            val backupDir = File(context.cacheDir, "backup")
            try {
                if (backupDir.exists()) {
                    backupDir.deleteRecursively()
                }
                backupDir.mkdirs()

                val metaFile = File(backupDir, "meta")
                meta.writeToFile(metaFile)

                // a component that fails fails the whole backup: a backup that silently lacks a part is worse
                for (component in components) {
                    if (component.group in groups) component.backup(backupDir)
                }

                // opened only now, so that a failing component does not leave an empty file behind
                // "wt": truncate, or an overwritten longer file keeps its old tail and is a corrupt zip
                var outputStream = context.contentResolver.openOutputStream(uri, "wt")
                    ?: throw java.io.IOException("Cannot open $uri for writing")
                
                if (!password.isNullOrBlank()) {
                    val salt = ByteArray(32).also { SecureRandom().nextBytes(it) }
                    val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
                    outputStream.write("TLBQ".toByteArray(Charsets.UTF_8))
                    outputStream.write(salt)
                    outputStream.write(iv)
                    
                    val key = deriveKey(password, salt)
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
                    outputStream = CipherOutputStream(outputStream, cipher)
                }
                
                outputStream.use { createArchive(backupDir, it) }
            } finally {
                backupDir.deleteRecursively()
            }
        }
    }

    /**
     * Restores a backup
     * @param groups the parts to restore, all that the backup contains by default. A part that the
     * backup does not contain is never touched.
     * @return the number of components that could not be restored (0 if everything went well). The
     * other components are restored regardless.
     * @throws java.io.IOException if the file cannot be opened or unpacked
     */
    suspend fun restore(
        uri: Uri,
        groups: Set<BackupGroup>? = null,
        password: String? = null,
    ): Int {
        // async + await (instead of launch): a failure reaches the caller rather than crashing the
        // app through the uncaught exception handler
        val job = scope.async {
            withContext(Dispatchers.IO) {
                var failed = 0
                val restoreDir = File(context.cacheDir, "restore")
                try {
                var inputStream = context.contentResolver.openInputStream(uri)
                    ?: throw java.io.IOException("Cannot open $uri for reading")
                
                val magic = ByteArray(4)
                inputStream.read(magic)
                if (magic.toString(Charsets.UTF_8) == "TLBQ") {
                    if (password.isNullOrBlank()) throw java.io.IOException("Password required to decrypt backup")
                    val salt = ByteArray(32).also { inputStream.read(it) }
                    val iv = ByteArray(12).also { inputStream.read(it) }
                    
                    val key = deriveKey(password, salt)
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
                    inputStream = CipherInputStream(inputStream, cipher)
                } else {
                    inputStream.close()
                    inputStream = context.contentResolver.openInputStream(uri)
                        ?: throw java.io.IOException("Cannot open $uri for reading")
                }
                
                if (restoreDir.exists()) {
                    restoreDir.deleteRecursively()
                }
                restoreDir.mkdirs()
                inputStream.use { extractArchive(it, restoreDir) }

                // a backup from before the groups only has the launcher part
                val inBackup = readMetaFromDir(restoreDir)?.groups ?: setOf(BackupGroup.Launcher)
                val chosen = if (groups == null) inBackup else groups intersect inBackup
                for (component in components) {
                    if (component.group !in chosen) continue
                    try {
                        component.restore(restoreDir)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // one broken part must not keep the others from being restored
                        android.util.Log.e("MM20", "Cannot restore ${component::class.java.simpleName}", e)
                        failed++
                    }
                }
                failed
                } finally {
                    restoreDir.deleteRecursively()
                }
            }
        }
        return job.await()
    }

    private suspend fun readMetaFromDir(dir: File): BackupMetadata? {
        val file = File(dir, "meta")
        if (!file.exists()) return null
        return file.inputStream().use { BackupMetadata.fromInputStream(it) }
    }

    suspend fun readBackupMeta(uri: Uri, password: String? = null): BackupMetadata? {
        return withContext(Dispatchers.IO) {
            var inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
            try {
                val magic = ByteArray(4)
                inputStream.read(magic)
                if (magic.toString(Charsets.UTF_8) == "TLBQ") {
                    if (password.isNullOrBlank()) throw java.io.IOException("Password required")
                    val salt = ByteArray(32).also { inputStream.read(it) }
                    val iv = ByteArray(12).also { inputStream.read(it) }
                    
                    val key = deriveKey(password, salt)
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
                    inputStream = CipherInputStream(inputStream, cipher)
                } else {
                    inputStream.close()
                    inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
                }
                
                ZipInputStream(inputStream).use { zipStream ->
                    var entry = zipStream.nextEntry
                    while (entry != null) {
                        if (entry.name == "meta") {
                            return@withContext BackupMetadata.fromInputStream(zipStream)
                        }
                        zipStream.closeEntry()
                        entry = zipStream.nextEntry
                    }
                    null
                }
            } catch (e: Exception) {
                // not a zip file at all, or truncated, or wrong password
                null
            }
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

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        // Post-quantum resistant iteration count and 256-bit key
        val spec = PBEKeySpec(password.toCharArray(), salt, 250_000, 256)
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
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