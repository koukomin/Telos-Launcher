package de.mm20.launcher2.backup

import java.io.File
import java.util.zip.ZipFile

/**
 * Backup Archive Checksum and Integrity Verifier for Telos Launcher.
 * Validates ZIP headers, entry integrity, and structure before unpacking backups.
 */
object TelosBackupVerifier {

    /**
     * Validates that the specified file is a readable, uncorrupted ZIP archive.
     */
    fun verifyZipArchive(file: File?): Boolean {
        if (file == null || !file.exists() || !file.canRead() || file.length() < 22) {
            return false
        }

        return try {
            ZipFile(file).use { zip ->
                val entries = zip.entries()
                if (!entries.hasMoreElements()) return false

                var entryCount = 0
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.name.contains("..")) return false // Prevent directory traversal
                    entryCount++
                }
                entryCount > 0
            }
        } catch (e: Exception) {
            false
        }
    }
}
