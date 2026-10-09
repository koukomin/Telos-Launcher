package de.mm20.launcher2.network.util

import android.util.AtomicFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * A value that lives in memory as a [StateFlow] and is written to one JSON file after changes
 * (debounced, atomically). A broken file is kept next to the original as `.broken` and replaced by
 * the default value, so a corrupt file never keeps the VPN from starting.
 */
internal class PersistedState<T : Any>(
    private val file: File,
    private val serializer: KSerializer<T>,
    private val default: () -> T,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val debounceMs: Long = 400,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }
    private val saveRequests = Channel<Unit>(Channel.CONFLATED)
    private val lock = Any()

    private val _state = MutableStateFlow(load())
    val state: StateFlow<T> = _state

    val value: T get() = _state.value

    init {
        scope.launch {
            for (request in saveRequests) {
                delay(debounceMs)
                saveNow()
            }
        }
    }

    private fun load(): T = try {
        if (file.exists()) json.decodeFromString(serializer, file.readText()) else default()
    } catch (e: Exception) {
        runCatching { file.copyTo(File(file.parentFile, file.name + ".broken"), overwrite = true) }
        default()
    }

    /** Atomically replaces the value and schedules a save. Returns the new value. */
    fun update(transform: (T) -> T): T {
        var result: T? = null
        _state.update { old -> transform(old).also { result = it } }
        saveRequests.trySend(Unit)
        return result!!
    }

    /** Writes the file now, on the calling thread. */
    fun saveNow() {
        synchronized(lock) {
            try {
                file.parentFile?.mkdirs()
                val atomic = AtomicFile(file)
                val out = atomic.startWrite()
                try {
                    out.write(json.encodeToString(serializer, _state.value).toByteArray(Charsets.UTF_8))
                    atomic.finishWrite(out)
                } catch (e: Exception) {
                    atomic.failWrite(out)
                }
            } catch (e: Exception) {
                // disk full or similar: keep running with the in-memory value
            }
        }
    }
}
