package de.mm20.launcher2.ui.files.remote

import android.content.Context
import android.net.ConnectivityManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.files.FsEntry
import de.mm20.launcher2.ui.files.formatSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

private val previewExts = setOf(
    "jpg", "jpeg", "png", "webp", "gif", "heic",
    "pdf", "odt", "ods", "odp", "docx", "xlsx", "pptx", "epub",
)

/**
 * Whether a file of a cloud or network storage may be downloaded to draw its preview.
 * [size] is the size reported by the storage (negative when unknown), [ext] the lower case extension.
 */
internal fun shouldPreview(enabled: Boolean, size: Long, limit: Long, metered: Boolean, wifiOnly: Boolean, ext: String): Boolean {
    if (!enabled) return false
    if (size <= 0L || limit <= 0L || size > limit) return false
    if (wifiOnly && metered) return false
    return ext.lowercase() in previewExts
}

/** Settings and cache of the previews for files on cloud and network storages */
internal object RemotePreviews {
    val sizeSteps = longArrayOf(256L * 1024, 512L * 1024, 1024L * 1024, 2L * 1024 * 1024, 5L * 1024 * 1024, 10L * 1024 * 1024)
    const val DEFAULT_LIMIT = 2L * 1024 * 1024
    private const val CACHE_CAP = 100L * 1024 * 1024
    private val gate = Semaphore(2)

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("telos_remote_previews", Context.MODE_PRIVATE)

    fun enabled(context: Context) = prefs(context).getBoolean("enabled", false)
    fun setEnabled(context: Context, v: Boolean) = prefs(context).edit().putBoolean("enabled", v).apply()
    fun limit(context: Context) = prefs(context).getLong("max_bytes", DEFAULT_LIMIT).let { l -> if (l in sizeSteps) l else DEFAULT_LIMIT }
    fun setLimit(context: Context, v: Long) = prefs(context).edit().putLong("max_bytes", v).apply()
    fun wifiOnly(context: Context) = prefs(context).getBoolean("wifi_only", true)
    fun setWifiOnly(context: Context, v: Boolean) = prefs(context).edit().putBoolean("wifi_only", v).apply()

    fun isMetered(context: Context): Boolean = runCatching {
        (context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).isActiveNetworkMetered
    }.getOrDefault(true)

    private fun dir(context: Context) = File(context.applicationContext.cacheDir, "remote_previews")

    fun cacheSize(context: Context): Long = dir(context).listFiles()?.sumOf { it.length() } ?: 0L

    fun clear(context: Context) {
        dir(context).listFiles()?.forEach { runCatching { it.delete() } }
    }

    private fun nameOf(e: FsEntry): String {
        val raw = "${RemotePath.idOf(e.path)}|${RemotePath.innerOf(e.path)}|${e.size}|${e.modified}"
        val hash = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }
        return "$hash.${e.extension}"
    }

    private fun trim(dir: File) {
        val files = dir.listFiles() ?: return
        var total = files.sumOf { it.length() }
        if (total <= CACHE_CAP) return
        for (f in files.sortedBy { it.lastModified() }) {
            if (total <= CACHE_CAP * 8 / 10) break
            total -= f.length()
            runCatching { f.delete() }
        }
    }

    /**
     * The local copy of a remote file for drawing its preview, or null when no preview is allowed or the
     * download failed. Cancelling the calling coroutine stops the download.
     */
    suspend fun localFor(context: Context, e: FsEntry): File? = withContext(Dispatchers.IO) {
        if (e.isDir || !RemotePath.isRemote(e.path)) return@withContext null
        val app = context.applicationContext
        val limit = limit(app)
        // the metered check only matters when something has to be downloaded
        if (!shouldPreview(enabled(app), e.size, limit, metered = false, wifiOnly = false, ext = e.extension)) return@withContext null
        val dir = dir(app).also { it.mkdirs() }
        val target = File(dir, nameOf(e))
        if (target.isFile && target.length() > 0) {
            runCatching { target.setLastModified(System.currentTimeMillis()) }
            return@withContext target
        }
        if (!shouldPreview(true, e.size, limit, isMetered(app), wifiOnly(app), e.extension)) return@withContext null
        gate.withPermit {
            ensureActive()
            val tmp = File(dir, target.name + ".part")
            try {
                RemoteRegistry.client(app, RemotePath.idOf(e.path)).openRead(RemotePath.innerOf(e.path)).use { input ->
                    tmp.outputStream().use { out ->
                        val buffer = ByteArray(32 * 1024)
                        var total = 0L
                        while (true) {
                            ensureActive()
                            val n = input.read(buffer)
                            if (n < 0) break
                            total += n
                            if (total > limit) return@withContext null
                            out.write(buffer, 0, n)
                        }
                    }
                }
                if (!tmp.renameTo(target)) return@withContext null
                trim(dir)
                target
            } catch (t: kotlin.coroutines.cancellation.CancellationException) {
                throw t
            } catch (t: Throwable) {
                null
            } finally {
                if (tmp.exists()) runCatching { tmp.delete() }
            }
        }
    }
}

/** Switches of the previews for cloud and network storages, shown on the Cloud and network storage screen */
@Composable
internal fun RemotePreviewSettings() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(RemotePreviews.enabled(context)) }
    var wifi by remember { mutableStateOf(RemotePreviews.wifiOnly(context)) }
    var index by remember { mutableFloatStateOf(RemotePreviews.sizeSteps.indexOf(RemotePreviews.limit(context)).coerceAtLeast(0).toFloat()) }
    var cache by remember { mutableLongStateOf(RemotePreviews.cacheSize(context)) }
    val limit = RemotePreviews.sizeSteps[index.toInt().coerceIn(0, RemotePreviews.sizeSteps.lastIndex)]

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.au23_rp_title), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Switch(checked = enabled, onCheckedChange = { enabled = it; RemotePreviews.setEnabled(context, it) })
        }
        Text(stringResource(R.string.au23_rp_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (enabled) {
            Text(stringResource(R.string.au23_rp_max_size, formatSize(limit)))
            Slider(
                value = index, onValueChange = { index = it.toInt().toFloat() },
                onValueChangeFinished = { RemotePreviews.setLimit(context, limit) },
                valueRange = 0f..(RemotePreviews.sizeSteps.size - 1).toFloat(), steps = RemotePreviews.sizeSteps.size - 2,
            )
            Text(stringResource(R.string.au23_rp_max_size_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.au23_rp_wifi_only))
                    Text(stringResource(R.string.au23_rp_wifi_only_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = wifi, onCheckedChange = { wifi = it; RemotePreviews.setWifiOnly(context, it) })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.au23_rp_cache_size, formatSize(cache)), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = {
                scope.launch {
                    withContext(Dispatchers.IO) { RemotePreviews.clear(context) }
                    cache = withContext(Dispatchers.IO) { RemotePreviews.cacheSize(context) }
                }
            }) { Text(stringResource(R.string.au23_rp_clear)) }
        }
    }
}
