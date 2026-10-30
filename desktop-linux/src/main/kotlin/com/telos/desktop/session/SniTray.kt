package com.telos.desktop.session

import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File

data class SniItem(val id: String, val title: String, val service: String)

object SniTrayParser {
    fun parseItems(line: String): List<SniItem> {
        val s = line.trim()
        if (!s.startsWith("{") || !s.contains("\"items\"")) return emptyList()
        val out = ArrayList<SniItem>()
        var idx = 0
        while (true) {
            val idKey = s.indexOf("\"id\"", idx)
            if (idKey < 0) break
            val id = FreedesktopNotifyParser.jsonString(s.substring(idKey), "id") ?: break
            val titleStart = s.indexOf("\"title\"", idKey)
            val title = if (titleStart >= 0) FreedesktopNotifyParser.jsonString(s.substring(titleStart), "title").orEmpty() else id
            val svcStart = s.indexOf("\"service\"", idKey)
            val service = if (svcStart >= 0) FreedesktopNotifyParser.jsonString(s.substring(svcStart), "service").orEmpty() else id
            out += SniItem(id, title.ifEmpty { id }, service)
            idx = idKey + 4
        }
        return out
    }
}

object SniTray {
    val items = mutableStateListOf<SniItem>()

    fun activate(item: SniItem) {
        val dir = File(System.getenv("XDG_RUNTIME_DIR") ?: System.getProperty("java.io.tmpdir"))
        File(dir, "telos-sni-cmd.jsonl").appendText("""{"cmd":"activate","service":"${item.service}"}""" + "\n")
    }

    suspend fun pollLoop() = withContext(Dispatchers.IO) {
        val dir = File(System.getenv("XDG_RUNTIME_DIR") ?: System.getProperty("java.io.tmpdir"))
        val file = File(dir, "telos-sni.jsonl")
        var last = ""
        while (currentCoroutineContext().isActive) {
            val line = runCatching { file.takeIf { it.isFile }?.readText()?.trim().orEmpty() }.getOrDefault("")
            if (line.isNotEmpty() && line != last) {
                last = line
                val parsed = SniTrayParser.parseItems(line)
                withContext(Dispatchers.Main) {
                    items.clear()
                    items.addAll(parsed)
                }
            }
            delay(500)
        }
    }
}
