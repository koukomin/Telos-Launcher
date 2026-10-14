package de.mm20.launcher2.ui.files

import android.content.Context
import de.mm20.launcher2.helper.ResumableFileTransfer
import de.mm20.launcher2.helper.TransferEntry
import de.mm20.launcher2.helper.TransferPlan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Application-owned queue: closing a Files tab does not cancel a local transfer. */
class FilesTransferQueue private constructor(context: Context) {
    enum class Status { QUEUED, RUNNING, PAUSED, COMPLETE, FAILED, CANCELLED, ORIGINAL_RETAINED }
    data class Item(val id: String, val sources: List<String>, val destination: String, val move: Boolean,
        val status: Status, val completed: Long = 0, val total: Long = 0, val created: Long = System.currentTimeMillis())
    private val folder = File(context.noBackupFilesDir, "files-transfer-queue").apply { mkdirs() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val guard = Any()
    private val requestedPause = ConcurrentHashMap.newKeySet<String>()
    private val requestedCancel = ConcurrentHashMap.newKeySet<String>()
    private val _items = MutableStateFlow<List<Item>>(emptyList())
    private val _isRecovering = MutableStateFlow(true)
    val isRecovering = _isRecovering.asStateFlow()
    val hasPending: Boolean get() = _isRecovering.value || activeId != null || _items.value.any { it.status in setOf(Status.QUEUED, Status.RUNNING, Status.PAUSED, Status.FAILED) }
    val items = _items.asStateFlow()
    private var worker = false
    private var activeId: String? = null
    init {
        val existingFiles = java.nio.file.Files.newDirectoryStream(folder.toPath(), "*.json").use { paths ->
            paths.take(100).map { it.toFile() }.toList()
        }
        scope.launch {
            synchronized(guard) {
                val recoveredItems = existingFiles.mapNotNull { file ->
                    runCatching {
                        val json = read(file)
                        val item = decodeItem(json)
                        // Every interrupted operation requires an explicit user resume after restart.
                        val recovered = if (item.status in listOf(Status.RUNNING, Status.QUEUED)) item.copy(status = Status.PAUSED) else item
                        if (recovered != item) { json.put("status", recovered.status.name); write(file, json) }
                        recovered
                    }.getOrNull()
                }
                _items.value = (_items.value + recoveredItems).distinctBy { it.id }.sortedByDescending { it.created }
                trimHistory()
                _isRecovering.value = false
            }
        }
    }
    fun enqueue(sources: List<String>, destination: String, move: Boolean) {
        require(sources.isNotEmpty() && sources.size <= 1000)
        require(sources.all { it.startsWith("/") && it.length <= 8192 && '\u0000' !in it })
        require(destination.startsWith("/") && destination.length <= 8192 && '\u0000' !in destination)
        require(sources.sumOf { it.length.toLong() } <= 128 * 1024)
        val item = Item(UUID.randomUUID().toString(), sources, destination, move, Status.QUEUED)
        synchronized(guard) {
            require(_items.value.count { it.status in listOf(Status.QUEUED, Status.RUNNING, Status.PAUSED, Status.FAILED) } < 20)
            write(file(item.id), encodeItem(item))
            _items.value = listOf(item) + _items.value
            trimHistory()
        }
        start()
    }
    fun pause(id: String) { requestedPause += id; change(id, Status.PAUSED) }
    fun resume(id: String) {
        requestedPause -= id; requestedCancel -= id
        change(id, Status.QUEUED); start()
    }
    fun cancel(id: String) {
        requestedCancel += id; change(id, Status.CANCELLED)
        // Cleanup is performed by the worker after it has closed its stream.
        start()
    }
    fun clearHistory() = synchronized(guard) {
        val terminal = setOf(Status.COMPLETE, Status.CANCELLED, Status.ORIGINAL_RETAINED)
        val removed = _items.value.filter { it.status in terminal && it.id != activeId }
        removed.forEach { file(it.id).delete(); logFile(it.id).delete() }
        _items.value = _items.value.filterNot { entry -> removed.any { it.id == entry.id } }
    }
    private fun trimHistory() {
        val terminal = setOf(Status.COMPLETE, Status.CANCELLED, Status.ORIGINAL_RETAINED)
        val removed = _items.value.filter { it.status in terminal && it.id != activeId }.drop(50)
        removed.forEach { file(it.id).delete(); logFile(it.id).delete() }
        _items.value = _items.value.filterNot { item -> removed.any { it.id == item.id } }
    }
    private fun logFile(id: String) = File(folder, file(id).nameWithoutExtension + ".completed")
    private fun file(id: String): File { require(id.matches(Regex("[0-9a-f-]{36}"))); return File(folder, "$id.json") }
    private fun change(id: String, status: Status) = synchronized(guard) {
        val item = _items.value.firstOrNull { it.id == id } ?: return@synchronized
        if (item.status in setOf(Status.COMPLETE, Status.CANCELLED, Status.ORIGINAL_RETAINED)) return@synchronized
        val next = item.copy(status = status)
        val json = read(file(id)); json.put("status", status.name); write(file(id), json)
        _items.value = _items.value.map { if (it.id == id) next else it }
        trimHistory()
    }
    private fun start(): Unit = synchronized(guard) {
        if (worker) return@synchronized
        worker = true
        scope.launch {
            try {
                val cancelled = synchronized(guard) { _items.value.filter { it.status == Status.CANCELLED } }
                cancelled.forEach { stopped ->
                    runCatching { decodePlan(read(file(stopped.id)))?.let { ResumableFileTransfer.discardPartials(it) } }
                }
                while (true) {
                    val item = synchronized(guard) { _items.value.lastOrNull { it.status == Status.QUEUED } } ?: break
                    synchronized(guard) { activeId = item.id }
                    try { perform(item) } finally { synchronized(guard) { activeId = null } }
                }
            } finally {
                synchronized(guard) {
                    worker = false
                    if (_items.value.any { it.status == Status.QUEUED }) start()
                }
            }
        }
    }
    private fun perform(item: Item) {
        var plan: TransferPlan? = null
        fun checkpoint() { if (item.id in requestedPause || item.id in requestedCancel) throw ResumableFileTransfer.Paused() }
        try {
            checkpoint(); change(item.id, Status.RUNNING)
            plan = synchronized(guard) { decodePlan(read(file(item.id)))?.also { restoreCompleted(item.id, it) } }
            if (plan == null) {
                plan = ResumableFileTransfer.plan(item.sources.map(::File), File(item.destination), item.move, ::checkpoint)
                synchronized(guard) { val json = read(file(item.id)); encodePlan(json, plan!!); write(file(item.id), json) }
            }
            var lastUpdate = 0L
            var savedThrough = 0
            while (savedThrough < plan!!.entries.size && plan!!.entries[savedThrough].committed) savedThrough++
            ResumableFileTransfer.execute(plan!!, ::checkpoint, {
                // Append one small durable record per committed entry instead of rewriting a large manifest.
                java.io.FileOutputStream(logFile(item.id), true).use { output ->
                    while (savedThrough < plan!!.entries.size && plan!!.entries[savedThrough].committed) {
                        val entry = plan!!.entries[savedThrough]
                        output.write(("$savedThrough C ${entry.digest ?: "-"}\n").toByteArray(Charsets.US_ASCII))
                        savedThrough++
                    }
                    if (savedThrough < plan!!.entries.size) {
                        val prepared = plan!!.entries[savedThrough]
                        if (!prepared.directory && !prepared.committed && prepared.digest != null)
                            output.write(("$savedThrough P ${prepared.digest}\n").toByteArray(Charsets.US_ASCII))
                    }
                    output.fd.sync()
                }
            }) { completed, total ->
                val now = System.currentTimeMillis()
                if (now - lastUpdate >= 150 || completed == total) {
                    lastUpdate = now
                    synchronized(guard) { _items.value = _items.value.map { if (it.id == item.id) it.copy(completed = completed, total = total) else it } }
                }
            }
            change(item.id, Status.COMPLETE)
        } catch (_: ResumableFileTransfer.Paused) {
            if (item.id in requestedCancel) {
                plan?.let { ResumableFileTransfer.discardPartials(it) }
            } else change(item.id, Status.PAUSED)
        } catch (_: ResumableFileTransfer.OriginalRetained) {
            change(item.id, Status.ORIGINAL_RETAINED)
        } catch (_: Exception) {
            if (item.id !in requestedCancel) change(item.id, Status.FAILED)
        }
    }
    private fun restoreCompleted(id: String, plan: TransferPlan) {
        val journal = logFile(id)
        if (!journal.exists()) return
        require(journal.length() <= 12L * 1024 * 1024)
        journal.bufferedReader(Charsets.US_ASCII).useLines { lines ->
            var count = 0
            lines.forEach { line ->
                require(++count <= 150_000 && line.length <= 80)
                val parts = line.split(' ')
                require(parts.size == 3 && parts[1] in setOf("C", "P"))
                val index = parts[0].toInt(); require(index in plan.entries.indices)
                val entry = plan.entries[index]
                val hash = parts[2]
                require(if (entry.directory) hash == "-" else hash.matches(Regex("[0-9a-f]{64}")))
                entry.digest = if (hash == "-") null else hash; entry.committed = parts[1] == "C"
            }
        }
    }
    private fun encodeItem(item: Item) = JSONObject().put("version", 1).put("id", item.id)
        .put("sources", JSONArray(item.sources)).put("destination", item.destination).put("move", item.move)
        .put("status", item.status.name).put("created", item.created)
    private fun decodeItem(json: JSONObject): Item {
        require(json.getInt("version") == 1)
        val sources = json.getJSONArray("sources")
        require(sources.length() in 1..1000)
        val id = json.getString("id"); file(id)
        val paths = (0 until sources.length()).map { sources.getString(it) }
        require(paths.all { it.startsWith("/") && it.length <= 8192 && '\u0000' !in it } && paths.sumOf { it.length.toLong() } <= 128 * 1024)
        val destination = json.getString("destination")
        require(destination.startsWith("/") && destination.length <= 8192 && '\u0000' !in destination)
        return Item(id, paths, destination,
            json.getBoolean("move"), Status.valueOf(json.getString("status")), created = json.getLong("created"))
    }
    private fun encodePlan(json: JSONObject, plan: TransferPlan) {
        val entries = JSONArray()
        plan.entries.forEach { entry -> entries.put(JSONObject().put("source", entry.source).put("destination", entry.destination)
            .put("directory", entry.directory).put("size", entry.size).put("modified", entry.modified)
            .put("identity", entry.identity ?: JSONObject.NULL).put("committed", entry.committed).put("digest", entry.digest ?: JSONObject.NULL)) }
        json.put("planId", plan.id).put("entries", entries)
    }
    private fun decodePlan(json: JSONObject): TransferPlan? {
        if (!json.has("entries")) return null
        val entries = json.getJSONArray("entries"); require(entries.length() <= 50_000)
        return TransferPlan(json.getString("planId"), json.getString("destination"), json.getBoolean("move"),
            (0 until entries.length()).map { index -> val e = entries.getJSONObject(index)
                TransferEntry(e.getString("source"), e.getString("destination"), e.getBoolean("directory"), e.getLong("size"), e.getLong("modified"),
                    if (e.isNull("identity")) null else e.getString("identity"), e.getBoolean("committed"), if (e.isNull("digest")) null else e.getString("digest")) })
    }
    private fun read(file: File): JSONObject {
        require(file.length() <= 16L * 1024 * 1024)
        return JSONObject(file.readText())
    }
    private fun write(file: File, json: JSONObject) {
        val bytes = json.toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= 16 * 1024 * 1024)
        val temp = File(file.parentFile, file.name + ".tmp")
        java.io.FileOutputStream(temp).use { it.write(bytes); it.fd.sync() }
        java.nio.file.Files.move(temp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE)
    }
    companion object {
        @Volatile private var instance: FilesTransferQueue? = null
        fun get(context: Context): FilesTransferQueue = instance ?: synchronized(this) {
            instance ?: FilesTransferQueue(context.applicationContext).also { instance = it }
        }
    }
}
