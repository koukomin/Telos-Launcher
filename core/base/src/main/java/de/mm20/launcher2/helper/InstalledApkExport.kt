package de.mm20.launcher2.helper

import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** A single APK stays installable; split packages retain every APK in an explicit ZIP bundle. */
object InstalledApkExport {
    fun write(files: List<File>, output: OutputStream, checkActive: () -> Unit = {}) {
        require(files.isNotEmpty() && files.size <= 200 && files.distinct().size == files.size)
        require(files.all { it.isFile && it.canRead() })
        val buffer = ByteArray(64 * 1024)
        fun copy(file: File, target: OutputStream) {
            file.inputStream().use { input ->
                var n = input.read(buffer)
                while (n >= 0) { checkActive(); target.write(buffer, 0, n); n = input.read(buffer) }
            }
        }
        if (files.size == 1) { copy(files.first(), output); output.flush() }
        else ZipOutputStream(output).use { zip ->
            files.forEachIndexed { index, file ->
                checkActive()
                zip.putNextEntry(ZipEntry(if (index == 0) "base.apk" else "split-${index.toString().padStart(3, '0')}.apk"))
                copy(file, zip); zip.closeEntry()
            }
        }
    }
}
