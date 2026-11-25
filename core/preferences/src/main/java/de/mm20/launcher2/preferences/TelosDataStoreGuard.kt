package de.mm20.launcher2.preferences

import java.io.File
import java.io.FileOutputStream

/**
 * Atomic File Guard for Telos Launcher DataStores.
 * Executes atomic writes via temporary `.tmp` files to prevent data corruption during power loss or force-kill.
 */
object TelosDataStoreGuard {

    /**
     * Atomically writes data to [targetFile] via a temporary `.tmp` copy.
     */
    fun atomicWriteBytes(targetFile: File, data: ByteArray): Boolean {
        val parent = targetFile.parentFile ?: return false
        if (!parent.exists()) parent.mkdirs()

        val tmpFile = File(parent, "${targetFile.name}.tmp")
        return try {
            FileOutputStream(tmpFile).use { fos ->
                fos.write(data)
                fos.flush()
                fos.fd.sync()
            }
            if (tmpFile.exists()) {
                tmpFile.renameTo(targetFile)
            } else {
                false
            }
        } catch (e: Exception) {
            if (tmpFile.exists()) tmpFile.delete()
            false
        }
    }
}
