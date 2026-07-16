package de.mm20.launcher2.files.providers

import android.content.Context
import android.provider.MediaStore
import androidx.core.database.getStringOrNull
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.preferences.search.FileSearchSettings
import de.mm20.launcher2.preferences.search.FileTypeFilters
import de.mm20.launcher2.search.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

internal class LocalFileProvider(
    private val context: Context,
    private val permissionsManager: PermissionsManager,
    private val settings: FileSearchSettings,
): FileProvider {
    override suspend fun search(query: String, allowNetwork: Boolean): List<File> = withContext(Dispatchers.IO) {
        if (!permissionsManager.checkPermissionOnce(PermissionGroup.ExternalStorage)) {
            return@withContext emptyList()
        }
        if (query.length < 2 || query.isBlank()) return@withContext emptyList()

        val typeFilters = settings.typeFilters.first()
        val excludedFolders = settings.excludedFolders.first()

        val results = mutableListOf<LocalFile>()
        val uri = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.MIME_TYPE
        )
        val selection = "${MediaStore.Files.FileColumns.TITLE} LIKE ?"
        val selArgs = if (query.length > 3) arrayOf("%$query%") else arrayOf("$query%")
        val sort = "${MediaStore.Files.FileColumns.DISPLAY_NAME} COLLATE NOCASE ASC"


        val cursor = try {
            context.contentResolver.query(uri, projection, selection, selArgs, sort)
        } catch (e: IllegalArgumentException) {
            CrashReporter.logException(e)
            null
        } ?: return@withContext results
        var scannedRows = 0
        while (cursor.moveToNext()) {
            // Type/folder filters are applied per row, so cap the scan instead of the query.
            if (results.size >= 10 || scannedRows >= 500) {
                break
            }
            scannedRows++
            val path = cursor.getString(3)
            if (isExcluded(path, excludedFolders)) continue
            if (!java.io.File(path).exists()) continue
            val directory = java.io.File(path).isDirectory
            val mimeType = (cursor.getStringOrNull(4).takeIf { it != "application/octet-stream" }
                ?: if (directory) "resource/folder" else LocalFile.getMimetypeByFileExtension(
                    path.substringAfterLast(
                        '.'
                    )
                ))
            if (!directory && !typeFilters.allows(mimeType)) continue
            val file = LocalFile(
                path = path,
                mimeType = mimeType,
                size = cursor.getLong(2),
                isDirectory = directory,
                id = cursor.getLong(1),
                metaData = LocalFile.getMetaData(context, mimeType, path)
            )
            results.add(file)
        }
        cursor.close()
        return@withContext results
    }

    private fun isExcluded(path: String, excludedFolders: Set<String>): Boolean {
        return excludedFolders.any { folder ->
            path == folder || path.startsWith("$folder/")
        }
    }

    companion object {
        private val documentMimePrefixes = listOf(
            "text/",
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument",
            "application/vnd.ms-",
            "application/vnd.oasis.opendocument",
            "application/epub+zip",
            "application/rtf",
        )

        internal fun FileTypeFilters.allows(mimeType: String): Boolean {
            if (allEnabled) return true
            return when {
                mimeType.startsWith("image/") -> images
                mimeType.startsWith("video/") -> videos
                mimeType.startsWith("audio/") -> music
                documentMimePrefixes.any { mimeType.startsWith(it) } -> documents
                else -> other
            }
        }
    }
}
