package com.telos.desktop.session

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile

data class PortalPickRequest(
    val id: String,
    val title: String,
    val directory: Boolean,
)

object PortalRequestParser {
    fun parse(line: String): PortalPickRequest? {
        val s = line.trim()
        if (s.isEmpty() || s[0] != '{') return null
        val id = FreedesktopNotifyParser.jsonString(s, "id") ?: return null
        val title = FreedesktopNotifyParser.jsonString(s, "title") ?: "Open"
        val directory = s.contains("\"directory\": true") || s.contains("\"directory\":true")
        return PortalPickRequest(id, title, directory)
    }
}

object PortalFileChooser {
    var pending by mutableStateOf<PortalPickRequest?>(null)
        private set
    private val queue = ArrayDeque<PortalPickRequest>()

    fun complete(file: File) {
        val req = pending ?: return
        writeOut(req.id, 0, listOf("file://${file.absolutePath}"))
        pending = queue.removeFirstOrNull()
    }

    fun cancel() {
        val req = pending ?: return
        writeOut(req.id, 1, emptyList())
        pending = queue.removeFirstOrNull()
    }

    private fun writeOut(id: String, response: Int, uris: List<String>) {
        val dir = File(System.getenv("XDG_RUNTIME_DIR") ?: System.getProperty("java.io.tmpdir"))
        val out = File(dir, "telos-portal-out.jsonl")
        val uriJson = uris.joinToString(",") { "\"$it\"" }
        out.appendText("""{"id":"$id","response":$response,"uris":[$uriJson]}""" + "\n")
        out.setReadable(true, true)
        out.setWritable(true, true)
    }

    suspend fun pollLoop() = withContext(Dispatchers.IO) {
        val dir = File(System.getenv("XDG_RUNTIME_DIR") ?: System.getProperty("java.io.tmpdir"))
        val file = File(dir, "telos-portal-in.jsonl")
        file.parentFile?.mkdirs()
        if (!file.exists()) file.createNewFile()
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(0)
            while (currentCoroutineContext().isActive) {
                val line = raf.readLine()
                if (line == null) {
                    delay(300)
                    continue
                }
                val req = PortalRequestParser.parse(line) ?: continue
                withContext(Dispatchers.Main) {
                    if (pending == null) pending = req else queue.add(req)
                }
            }
        }
    }
}
