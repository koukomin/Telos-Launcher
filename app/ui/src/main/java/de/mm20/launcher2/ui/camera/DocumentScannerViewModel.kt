package de.mm20.launcher2.ui.camera

import android.app.Application
import android.net.Uri
import android.util.AtomicFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

internal class DocumentScannerViewModel(application: Application, state: SavedStateHandle) : AndroidViewModel(application) {
    private val app = application.applicationContext
    private val session = state.get<String>("scan-session")?.takeIf { runCatching { UUID.fromString(it) }.isSuccess }
        ?: UUID.randomUUID().toString().also { state["scan-session"] = it }
    private val storageMutex = Mutex()
    private fun directory() = File(app.cacheDir, "document-scanner/$session")
    private val _pages = MutableStateFlow<List<ScanPage>>(emptyList())
    val pages = _pages.asStateFlow()
    private val _busy = MutableStateFlow(true)
    val busy = _busy.asStateFlow()
    private val _error = MutableStateFlow(false)
    val error = _error.asStateFlow()
    private val _progress = MutableStateFlow(0)
    val progress = _progress.asStateFlow()
    private val _export = MutableStateFlow<File?>(null)
    val export = _export.asStateFlow()
    private val _saved = MutableStateFlow(0)
    val saved = _saved.asStateFlow()
    private val _languages = MutableStateFlow(setOf("eng"))
    val languages = _languages.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val loaded = withContext(Dispatchers.IO) {
                    val dir = directory()
                    val otherSessions = dir.parentFile?.listFiles()?.filter { it.isDirectory && it.name != session }?.sortedByDescending { it.lastModified() }.orEmpty()
                    var retainedBytes = 0L
                    val oldest = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
                    for (previous in otherSessions) {
                        val bytes = previous.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                        if (previous.lastModified() < oldest || retainedBytes + bytes > 256L * 1024 * 1024) previous.deleteRecursively()
                        else retainedBytes += bytes
                    }
                    check(dir.isDirectory || dir.mkdirs())
                    val manifest = AtomicFile(File(dir, "pages.json"))
                    if (!manifest.baseFile.exists()) return@withContext emptyList()
                    check(manifest.baseFile.length() < 64 * 1024)
                    val entries = JSONArray(manifest.readFully().toString(Charsets.UTF_8))
                    require(entries.length() <= DocumentScanEngine.MAX_PAGES)
                    (0 until entries.length()).mapNotNull { i ->
                        val row = entries.getJSONObject(i)
                        val name = row.getString("file")
                        if (name != File(name).name) return@mapNotNull null
                        val file = File(dir, name).takeIf { it.isFile } ?: return@mapNotNull null
                        ScanPage(row.getString("id"), file, row.optInt("turns").mod(4), row.optDouble("x").toFloat().coerceIn(0f,.4f), row.optDouble("y").toFloat().coerceIn(0f,.4f), row.optBoolean("gray", true))
                    }
                }
                _pages.value = loaded
            } catch (_: Exception) { _error.value = true }
            finally { _busy.value = false }
        }
    }

    fun dismissError() { _error.value = false }
    fun selectLanguages(value: Set<String>) {
        if (!_busy.value && value.size <= 3 && value.all { code -> CameraOcrModels.languages.any { it.code == code } }) _languages.value = value
    }

    private suspend fun persist(pages: List<ScanPage>) = withContext(Dispatchers.IO) {
        storageMutex.withLock {
            val file = AtomicFile(File(directory(), "pages.json"))
            val data = JSONArray().also { array -> pages.forEach { p ->
                array.put(JSONObject().put("id", p.id).put("file", p.file.name).put("turns", p.turns).put("x", p.cropX.toDouble()).put("y", p.cropY.toDouble()).put("gray", p.grayscale))
            } }.toString().toByteArray()
            val output = file.startWrite()
            try { output.write(data); file.finishWrite(output) } catch (error: Throwable) { file.failWrite(output); throw error }
        }
    }

    private fun work(block: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true; _error.value = false
        viewModelScope.launch {
            try { block() }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { _error.value = true }
            finally { _busy.value = false }
        }
    }

    fun addImages(uris: List<Uri>) = work {
        val added = ArrayList<ScanPage>()
        try {
            for (uri in uris.take(DocumentScanEngine.MAX_PAGES - _pages.value.size)) added += DocumentScanEngine.import(app, uri, directory())
            val updated = _pages.value + added
            persist(updated); _pages.value = updated
        } catch (error: Throwable) { withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { added.forEach { it.file.delete() } }; throw error }
    }

    fun prepareCapture(onReady: (File) -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                check(_pages.value.size < DocumentScanEngine.MAX_PAGES)
                val file = withContext(Dispatchers.IO) { File.createTempFile("capture-", ".jpg", directory()) }
                onReady(file)
            } catch (cancelled: kotlinx.coroutines.CancellationException) { _busy.value = false; throw cancelled }
            catch (_: Exception) { _busy.value = false; _error.value = true }
        }
    }

    fun acceptCapture(file: File) {
        _busy.value = false
        work {
            try {
                val page = DocumentScanEngine.import(app, Uri.fromFile(file), directory())
                val updated = _pages.value + page
                try { persist(updated); _pages.value = updated }
                catch (error: Throwable) { withContext(Dispatchers.IO) { page.file.delete() }; throw error }
            } finally { withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { file.delete() } }
        }
    }

    fun failedCapture(file: File) {
        _busy.value = false
        viewModelScope.launch(Dispatchers.IO) { file.delete() }
        _error.value = true
    }

    fun edit(page: ScanPage) = work {
        val updated = _pages.value.map { if (it.id == page.id) page else it }
        persist(updated); _pages.value = updated
    }
    fun move(id: String, delta: Int) = work {
        val updated = _pages.value.toMutableList()
        val at = updated.indexOfFirst { it.id == id }
        if (at >= 0 && at + delta in updated.indices) { updated.add(at + delta, updated.removeAt(at)); persist(updated); _pages.value = updated }
    }
    fun remove(id: String) = work {
        val removed = _pages.value.firstOrNull { it.id == id } ?: return@work
        val updated = _pages.value.filterNot { it.id == id }
        persist(updated); _pages.value = updated
        withContext(Dispatchers.IO) { removed.file.delete() }
    }
    fun createPdf() = work {
        _progress.value = 0
        val previous = _export.value
        withContext(Dispatchers.IO) { previous?.delete() }
        _export.value = DocumentScanEngine.export(app, _pages.value, _languages.value, directory()) { _progress.value = it }
    }
    fun saveExport(uri: Uri?) {
        val output = _export.value ?: return
        _export.value = null
        work {
            try {
                if (uri != null) withContext(Dispatchers.IO) {
                    try { app.contentResolver.openOutputStream(uri, "wt")?.use { destination -> output.inputStream().use { it.copyTo(destination, 64 * 1024) } } ?: error("No output stream") }
                    catch (error: Throwable) { runCatching { android.provider.DocumentsContract.deleteDocument(app.contentResolver, uri) }; throw error }
                }
                if (uri != null) _saved.value++
            } finally { withContext(Dispatchers.IO) { output.delete() } }
        }
    }
}
