package de.mm20.launcher2.freeze

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.AtomicFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

/** UAD-NG descriptions, GPL-3.0; bundled fallback and validated, conditional HTTPS updates. */
data class BloatDescription(val description: String, val recommendation: String)
enum class BloatUpdateStatus { Idle, Updating, Current, Failed }

internal fun canFreezeSystemApp(recommendation: String?, loadFailed: Boolean, expertApproved: Boolean): Boolean =
    !loadFailed && when (recommendation?.lowercase(java.util.Locale.ROOT)) {
        "unsafe" -> false
        "expert" -> expertApproved
        else -> true // Unclassified is shown as unknown, never advertised as safe.
    }

internal fun parseBloatDescriptions(text: String): Map<String, BloatDescription> {
    val rows = Json.parseToJsonElement(text).jsonObject
    require(rows.size in 1000..20000)
    val result = rows.mapValues { (pkg, element) ->
        require(pkg.length <= 255 && pkg.matches(PACKAGE_NAME))
        val row = element.jsonObject
        val description = row["description"]?.jsonPrimitive?.content.orEmpty()
        val recommendation = row["removal"]?.jsonPrimitive?.content.orEmpty()
        require(description.length <= 64_000 && recommendation.length <= 100)
        BloatDescription(description, recommendation)
    }
    require(result.values.any { it.recommendation.equals("Unsafe", true) })
    return result
}
private val PACKAGE_NAME = Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)*")

class BloatDescriptions(private val context: Context) {
    private val mutex = Mutex()
    private val refreshMutex = Mutex()
    private var loaded = false
    private var failed = false
    private val _data = MutableStateFlow<Map<String, BloatDescription>>(emptyMap())
    val data = _data.asStateFlow()
    private val _status = MutableStateFlow(BloatUpdateStatus.Idle)
    val status = _status.asStateFlow()
    private val cache get() = AtomicFile(File(context.filesDir, "freeze-uad.json"))
    private val etagFile get() = AtomicFile(File(context.filesDir, "freeze-uad.etag"))

    suspend fun entries(): Map<String, BloatDescription> = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!loaded) {
                val saved = runCatching { cache.openRead().bufferedReader().use { parseBloatDescriptions(it.readText()) } }.getOrNull()
                val bundled = saved ?: runCatching {
                    context.assets.open("uad_lists.json.gz").use { input ->
                        GZIPInputStream(input).bufferedReader().use { parseBloatDescriptions(it.readText()) }
                    }
                }.getOrNull()
                failed = bundled == null
                _data.value = bundled.orEmpty()
                loaded = true
            }
            _data.value
        }
    }

    /** One check per hub entry. ETag avoids downloading an unchanged database. Never clears good data. */
    suspend fun refresh() = withContext(Dispatchers.IO) {
        refreshMutex.withLock {
            entries()
            _status.value = BloatUpdateStatus.Updating
            var connection: HttpURLConnection? = null
            try {
                connection = URL(UPDATE_URL).openConnection() as HttpURLConnection
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                connection.instanceFollowRedirects = false
                val etag = if (cache.baseFile.exists()) runCatching {
                    etagFile.openRead().bufferedReader().use { it.readText() }.take(512)
                }.getOrNull() else null
                if (!etag.isNullOrBlank()) connection.setRequestProperty("If-None-Match", etag)
                if (connection.responseCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
                    require(!failed && _data.value.isNotEmpty())
                    _status.value = BloatUpdateStatus.Current
                    return@withLock
                }
                require(connection.responseCode == HttpURLConnection.HTTP_OK)
                require(connection.contentLengthLong <= MAX_DOWNLOAD_BYTES)
                val bytes = connection.inputStream.use { input ->
                    val out = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    var count = input.read(buffer)
                    while (count != -1) {
                        require(out.size() + count <= MAX_DOWNLOAD_BYTES)
                        out.write(buffer, 0, count)
                        count = input.read(buffer)
                    }
                    out.toByteArray()
                }
                val parsed = parseBloatDescriptions(bytes.toString(Charsets.UTF_8))
                writeAtomic(cache, bytes)
                val newEtag = connection.getHeaderField("ETag").orEmpty().take(512)
                writeAtomic(etagFile, newEtag.toByteArray())
                mutex.withLock { _data.value = parsed; failed = false; loaded = true }
                _status.value = BloatUpdateStatus.Current
            } catch (error: CancellationException) { _status.value = BloatUpdateStatus.Idle; throw error }
            catch (error: Exception) {
                _status.value = BloatUpdateStatus.Failed
                android.util.Log.w("BloatDescriptions", "Update failed; retaining last valid descriptions", error)
            } finally { connection?.disconnect() }
        }
    }

    suspend fun allowsFreeze(pkg: String, expertApproved: Boolean): Boolean = withContext(Dispatchers.IO) {
        val info = runCatching { context.packageManager.getApplicationInfo(pkg,
            PackageManager.MATCH_DISABLED_COMPONENTS or PackageManager.MATCH_UNINSTALLED_PACKAGES) }.getOrNull()
            ?: return@withContext false
        if (info.flags and ApplicationInfo.FLAG_SYSTEM == 0) return@withContext true
        val data = entries()
        canFreezeSystemApp(data[pkg]?.recommendation, failed, expertApproved)
    }
    private fun writeAtomic(file: AtomicFile, bytes: ByteArray) {
        val stream = file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream) }
        catch (error: Exception) { file.failWrite(stream); throw error }
    }
    companion object {
        const val UPDATE_URL = "https://raw.githubusercontent.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/main/resources/assets/uad_lists.json"
        private const val MAX_DOWNLOAD_BYTES = 8 * 1024 * 1024
    }
}
