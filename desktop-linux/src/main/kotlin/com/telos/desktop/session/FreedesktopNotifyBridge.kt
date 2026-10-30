package com.telos.desktop.session

import com.telos.desktop.overlay.TelosNotificationCenter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile

object FreedesktopNotifyParser {
    fun parseLine(line: String): Pair<String, String>? {
        val s = line.trim()
        if (s.isEmpty() || s[0] != '{') return null
        val summary = jsonString(s, "summary") ?: return null
        val body = jsonString(s, "body").orEmpty()
        if (summary.isEmpty() && body.isEmpty()) return null
        return summary.ifEmpty { "Notification" } to body
    }

    internal fun jsonString(obj: String, key: String): String? {
        val needle = "\"$key\""
        val k = obj.indexOf(needle)
        if (k < 0) return null
        val colon = obj.indexOf(':', k + needle.length)
        if (colon < 0) return null
        var i = colon + 1
        while (i < obj.length && obj[i].isWhitespace()) i++
        if (i >= obj.length || obj[i] != '"') return ""
        i++
        val out = StringBuilder()
        while (i < obj.length) {
            val c = obj[i]
            if (c == '\\' && i + 1 < obj.length) {
                out.append(obj[i + 1])
                i += 2
                continue
            }
            if (c == '"') break
            out.append(c)
            i++
        }
        return out.toString()
    }
}

object FreedesktopNotifyBridge {
    suspend fun pollLoop() = withContext(Dispatchers.IO) {
        val dir = File(System.getenv("XDG_RUNTIME_DIR") ?: System.getProperty("java.io.tmpdir"))
        val file = File(dir, WaylandShell.NOTIFY_FIFO)
        file.parentFile?.mkdirs()
        if (!file.exists()) file.createNewFile()
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(0)
            while (currentCoroutineContext().isActive) {
                val line = raf.readLine()
                if (line == null) {
                    delay(400)
                    continue
                }
                val hit = FreedesktopNotifyParser.parseLine(line) ?: continue
                TelosNotificationCenter.post(hit.first, hit.second)
            }
        }
    }
}
