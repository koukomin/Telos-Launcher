package de.mm20.launcher2.applock

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import de.mm20.launcher2.preferences.applock.AppLockSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class IntruderPhoto(
    val uri: Uri,
    val displayName: String,
    val lastModified: Long,
)

/**
 * Storage backend for intruder photos: either app-private internal storage (default, never
 * gallery-visible regardless of the visibility setting) or a user-picked SAF folder (which can
 * optionally be made gallery-visible via a .nomedia marker). All file/document I/O for the
 * feature goes through here so [IntruderPhotoManager] doesn't need to know which backend is
 * active.
 */
internal class IntruderPhotoStorage(
    private val context: Context,
    private val appLockSettings: AppLockSettings,
) {
    suspend fun write(bytes: ByteArray): Uri? = withContext(Dispatchers.IO) {
        val treeUri = customTreeUri()
        val name = "${SimpleDateFormat(FILE_NAME_PATTERN, Locale.US).format(Date())}.jpg"
        if (treeUri != null) writeToTree(treeUri, name, bytes) else writeToInternal(name, bytes)
    }

    suspend fun list(): List<IntruderPhoto> = withContext(Dispatchers.IO) {
        val treeUri = customTreeUri()
        val photos = if (treeUri != null) listTree(treeUri) else listInternal()
        photos.filter { it.displayName != NOMEDIA_NAME }
    }

    suspend fun delete(photo: IntruderPhoto) = withContext(Dispatchers.IO) {
        if (customTreeUri() != null) {
            try {
                DocumentsContract.deleteDocument(context.contentResolver, photo.uri)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to delete intruder photo", e)
            }
        } else {
            photo.uri.path?.let { File(it).delete() }
        }
        Unit
    }

    /** Human-readable current storage location, for display in settings. */
    suspend fun displayPath(): String = withContext(Dispatchers.IO) {
        val treeUri = customTreeUri()
        if (treeUri != null) {
            documentIdDisplayPath(treeUri) ?: treeUri.toString()
        } else {
            internalDir().absolutePath
        }
    }

    /**
     * Switches the storage location. Releases the persisted permission on the previous custom
     * folder (if any) and takes a new persistable read/write grant on [treeUri] (the caller is
     * expected to have already done this via the OpenDocumentTree result, but it's re-taken here
     * defensively). Existing photos are intentionally left where they are - this only changes
     * where *future* captures go; silently moving files across storage backends on every settings
     * change would be surprising.
     *
     * Returns the new display path directly rather than making the caller re-read it back from
     * [displayPath] immediately afterward - same fire-and-forget-write hazard as
     * [applyVisibility], see its doc.
     */
    suspend fun setCustomFolder(treeUri: Uri?): String = withContext(Dispatchers.IO) {
        val previous = customTreeUri()
        if (previous != null && previous != treeUri) {
            try {
                context.contentResolver.releasePersistableUriPermission(previous, URI_FLAGS)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to release uri permission for previous intruder photo folder", e)
            }
        }
        appLockSettings.setIntruderPhotoStorageUri(treeUri?.toString())
        if (treeUri != null) {
            applyVisibilityLocked(treeUri, appLockSettings.intruderPhotoVisibleInGallery.first())
            documentIdDisplayPath(treeUri) ?: treeUri.toString()
        } else {
            internalDir().absolutePath
        }
    }

    /**
     * Re-applies the given gallery-visibility preference to the active custom folder by adding
     * or removing a .nomedia marker file - a no-op when using the default internal storage,
     * which is never gallery-visible regardless of this marker.
     *
     * Takes [visible] as a parameter rather than reading [AppLockSettings.intruderPhotoVisibleInGallery]
     * itself: [de.mm20.launcher2.preferences.LauncherDataStore.update] is fire-and-forget, so a
     * caller that just wrote the new value and immediately re-read it back from the flow could
     * still observe the stale one.
     */
    suspend fun applyVisibility(visible: Boolean) = withContext(Dispatchers.IO) {
        val treeUri = customTreeUri() ?: return@withContext
        applyVisibilityLocked(treeUri, visible)
    }

    private fun applyVisibilityLocked(treeUri: Uri, visible: Boolean) {
        val existing = findChild(treeUri, NOMEDIA_NAME)
        if (visible) {
            if (existing != null) {
                try {
                    DocumentsContract.deleteDocument(context.contentResolver, existing)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to remove .nomedia marker", e)
                }
            }
        } else if (existing == null) {
            createDocument(treeUri, NOMEDIA_NAME, "application/octet-stream")
        }
    }

    private suspend fun customTreeUri(): Uri? =
        appLockSettings.intruderPhotoStorageUri.first()?.let {
            try {
                Uri.parse(it)
            } catch (e: Exception) {
                null
            }
        }

    private fun internalDir() = File(context.filesDir, "intruder_photos")

    private fun writeToInternal(name: String, bytes: ByteArray): Uri? {
        return try {
            val dir = internalDir()
            dir.mkdirs()
            val file = File(dir, name)
            file.writeBytes(bytes)
            Uri.fromFile(file)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write intruder photo to internal storage", e)
            null
        }
    }

    private fun listInternal(): List<IntruderPhoto> =
        internalDir().listFiles()
            ?.filter { it.isFile }
            ?.map { IntruderPhoto(Uri.fromFile(it), it.name, it.lastModified()) }
            ?.sortedByDescending { it.lastModified }
            ?: emptyList()

    private fun writeToTree(treeUri: Uri, name: String, bytes: ByteArray): Uri? {
        val uri = createDocument(treeUri, name, "image/jpeg") ?: return null
        return try {
            context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            uri
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write intruder photo to custom folder", e)
            null
        }
    }

    private fun listTree(treeUri: Uri): List<IntruderPhoto> {
        val childrenUri = childDocumentsUri(treeUri)
        val results = mutableListOf<IntruderPhoto>()
        try {
            context.contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                ),
                null, null, null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val docId = cursor.getString(0)
                    val name = cursor.getString(1) ?: continue
                    val modified = cursor.getLong(2)
                    val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                    results.add(IntruderPhoto(docUri, name, modified))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to list intruder photos in custom folder", e)
        }
        return results.sortedByDescending { it.lastModified }
    }

    private fun findChild(treeUri: Uri, name: String): Uri? {
        return try {
            context.contentResolver.query(
                childDocumentsUri(treeUri),
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                ),
                null, null, null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    if (cursor.getString(1) == name) {
                        return DocumentsContract.buildDocumentUriUsingTree(
                            treeUri,
                            cursor.getString(0),
                        )
                    }
                }
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun createDocument(treeUri: Uri, name: String, mimeType: String): Uri? {
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )
        return try {
            DocumentsContract.createDocument(context.contentResolver, parentUri, mimeType, name)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create document in custom intruder photo folder", e)
            null
        }
    }

    private fun childDocumentsUri(treeUri: Uri) =
        DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )

    /** Best-effort human-readable rendering of a tree document id, typically shaped like
     * "primary:Pictures/MyFolder" (internal storage) or "1234-5678:MyFolder" (removable media). */
    private fun documentIdDisplayPath(treeUri: Uri): String? {
        return try {
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            val storage = docId.substringBefore(':', "")
            val path = docId.substringAfter(':', docId)
            val storageLabel = if (storage.isEmpty() || storage == "primary") {
                "Internal storage"
            } else {
                "Storage $storage"
            }
            if (path.isEmpty()) storageLabel else "$storageLabel/$path"
        } catch (e: Exception) {
            null
        }
    }

    private companion object {
        private const val TAG = "IntruderPhotoStorage"
        private const val NOMEDIA_NAME = ".nomedia"
        private const val URI_FLAGS =
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        // SimpleDateFormat is not thread safe, so each write builds its own
        private const val FILE_NAME_PATTERN = "yyyy-MM-dd_HH-mm-ss-SSS"
    }
}
