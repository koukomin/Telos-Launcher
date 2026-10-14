package de.mm20.launcher2.comms.files

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.provider.MediaStore
import androidx.work.*
import de.mm20.launcher2.helper.FileTrash
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/** One trash policy for Files, Viewer and Media, with one private store per storage volume. */
class TelosTrash(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("telos_shared_trash", Context.MODE_PRIVATE)
    var retentionDays: Int
        get() = prefs.getInt("retentionDays", 30).coerceIn(1, 365)
        set(value) { require(value in 1..365); prefs.edit().putInt("retentionDays", value).apply() }
    data class Item(val store: FileTrash, val entry: FileTrash.Entry)
    private fun stores(): List<Pair<File, FileTrash>> = app.getExternalFilesDirs(null).filterNotNull().map { folder ->
        val volume = File(folder.path.substringBefore("/Android/"))
        volume to FileTrash(File(folder, "telos_trash"), volume)
    }
    fun supports(path: String): Boolean = !path.contains("://") && !path.startsWith("/system") &&
        stores().any { (volume, _) -> File(path).absolutePath.startsWith(volume.absolutePath.trimEnd('/') + "/") }
    suspend fun move(path: String): Item = mutex.withLock { withContext(Dispatchers.IO) {
        val source = File(path)
        val pair = stores().firstOrNull { (volume, _) -> source.absolutePath.startsWith(volume.absolutePath.trimEnd('/') + "/") }
            ?: throw java.io.IOException("Trash unavailable for this storage")
        val entry = pair.second.moveToTrash(source, retentionDays)
        scan(path)
        scheduleCleanup()
        Item(pair.second, entry)
    } }
    suspend fun entries(): List<Item> = mutex.withLock { withContext(Dispatchers.IO) {
        stores().flatMap { (_, store) -> store.entries().map { Item(store, it) } }.sortedByDescending { it.entry.deletedAt }
    } }
    suspend fun restore(item: Item): Boolean = mutex.withLock { withContext(Dispatchers.IO) {
        try { val restored = item.store.restore(item.entry.id); scan(restored.path); true }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { false }
    } }
    suspend fun deletePermanently(item: Item): Boolean = mutex.withLock { withContext(Dispatchers.IO) { item.store.deletePermanently(item.entry.id) } }
    suspend fun purgeExpired() = mutex.withLock { withContext(Dispatchers.IO) { stores().sumOf { it.second.purgeExpired() } } }
    suspend fun filePath(uri: Uri): String? = withContext(Dispatchers.IO) {
        if (uri.scheme == "file") return@withContext uri.path
        if (uri.scheme != "content" || uri.authority != MediaStore.AUTHORITY) return@withContext null
        runCatching { app.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } }.getOrNull()
    }
    private companion object { val mutex = Mutex() }
    data class SystemItem(val uri: Uri, val name: String, val original: String, val expiresAt: Long)
    suspend fun systemEntries(): List<SystemItem> = withContext(Dispatchers.IO) {
        if (android.os.Build.VERSION.SDK_INT < 30) return@withContext emptyList()
        val result = ArrayList<SystemItem>()
        val args = android.os.Bundle().apply {
            putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
            putString(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION, "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (1,2,3)")
        }
        runCatching {
            app.contentResolver.query(MediaStore.Files.getContentUri("external"), arrayOf(
                MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.RELATIVE_PATH, MediaStore.MediaColumns.DATE_EXPIRES,
                MediaStore.MediaColumns.VOLUME_NAME, MediaStore.Files.FileColumns.MEDIA_TYPE,
            ), args, null)?.use { cursor ->
                while (cursor.moveToNext() && result.size < 1000) {
                    val collection = when (cursor.getInt(5)) {
                        1 -> MediaStore.Images.Media.getContentUri(cursor.getString(4))
                        2 -> MediaStore.Audio.Media.getContentUri(cursor.getString(4))
                        3 -> MediaStore.Video.Media.getContentUri(cursor.getString(4))
                        else -> continue
                    }
                    result += SystemItem(android.content.ContentUris.withAppendedId(collection, cursor.getLong(0)),
                        cursor.getString(1).orEmpty(), cursor.getString(2).orEmpty(), cursor.getLong(3) * 1000)
                }
            }
        }
        result
    }
    private suspend fun scan(path: String) {
        kotlinx.coroutines.withTimeoutOrNull(5000) {
            kotlinx.coroutines.suspendCancellableCoroutine<Unit> { continuation ->
                MediaScannerConnection.scanFile(app, arrayOf(path), null) { _, _ ->
                    if (continuation.isActive) continuation.resumeWith(kotlin.Result.success(Unit))
                }
            }
        }
    }
    private fun scheduleCleanup() {
        WorkManager.getInstance(app).enqueueUniquePeriodicWork("telos-trash-expiry", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<TrashExpiryWorker>(1, TimeUnit.DAYS).build())
    }
}

class TrashExpiryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try { TelosTrash(applicationContext).purgeExpired(); Result.success() }
    catch (e: kotlinx.coroutines.CancellationException) { throw e }
    catch (_: Exception) { Result.retry() }
}
