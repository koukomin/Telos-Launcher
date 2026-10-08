package de.mm20.launcher2.downloads

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
private class StoreFile(val version: Int = 1, val tasks: List<DownloadTask> = emptyList())

/**
 * All tasks of Telos Downloads as one JSON file in the app's private storage (filesDir/downloads/tasks.json),
 * written atomically and at most every couple of seconds while progress changes. The file is the single
 * source of truth and plain enough to be added to the Telos backup (phase 2 or later).
 */
class DownloadStore(context: Context) {
    private val file = File(context.filesDir, "downloads").also { it.mkdirs() }.let { File(it, "tasks.json") }
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val saveRequests = Channel<Unit>(Channel.CONFLATED)

    private val _tasks = MutableStateFlow(load())
    val tasks: StateFlow<List<DownloadTask>> = _tasks

    init {
        scope.launch {
            for (r in saveRequests) {
                delay(1500)
                saveNow()
            }
        }
    }

    private fun load(): List<DownloadTask> = try {
        if (file.exists()) json.decodeFromString<StoreFile>(file.readText()).tasks else emptyList()
    } catch (e: Exception) {
        // keep the broken file for inspection instead of overwriting it with an empty list
        runCatching { file.copyTo(File(file.parentFile, "tasks.broken.json"), overwrite = true) }
        emptyList()
    }

    fun get(id: String): DownloadTask? = _tasks.value.firstOrNull { it.id == id }

    fun add(task: DownloadTask) {
        _tasks.update { it + task }
        requestSave()
    }

    /**
     * Changes the task with [id]; returns the new task or null if it does not exist (any more).
     * [save] false: the change is only live progress that the next regular save picks up.
     */
    fun update(id: String, save: Boolean = true, transform: (DownloadTask) -> DownloadTask): DownloadTask? {
        var result: DownloadTask? = null
        var changed = false
        _tasks.update { list ->
            val i = list.indexOfFirst { it.id == id }
            if (i < 0) {
                result = null
                changed = false
                list
            } else {
                val old = list[i]
                val n = transform(old)
                result = n
                changed = n != old
                if (!changed) list else list.toMutableList().also { it[i] = n }
            }
        }
        if (changed && save) requestSave()
        return result
    }

    fun remove(id: String) {
        _tasks.update { list -> list.filterNot { it.id == id } }
        requestSave()
    }

    fun removeWhere(predicate: (DownloadTask) -> Boolean) {
        _tasks.update { list -> list.filterNot(predicate) }
        requestSave()
    }

    private fun requestSave() {
        saveRequests.trySend(Unit)
    }

    /** Writes now; used when the service stops */
    @Synchronized
    fun saveNow() {
        try {
            val tmp = File(file.parentFile, "tasks.json.tmp")
            tmp.writeText(json.encodeToString(StoreFile(tasks = _tasks.value)))
            if (!tmp.renameTo(file)) {
                file.writeText(tmp.readText())
                tmp.delete()
            }
        } catch (_: Exception) {
        }
    }
}
