package com.telos.desktop.session

import com.telos.desktop.overlay.SessionPower
import com.telos.desktop.overlay.SessionPowerActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile

object IdleLockParser {
    fun isLock(line: String): Boolean {
        val s = line.trim()
        return s.contains("\"event\":\"lock\"") || s == "lock"
    }
}

object IdleLockBridge {
    suspend fun pollLoop() = withContext(Dispatchers.IO) {
        val dir = File(System.getenv("XDG_RUNTIME_DIR") ?: System.getProperty("java.io.tmpdir"))
        val file = File(dir, "telos-lock.jsonl")
        file.parentFile?.mkdirs()
        if (!file.exists()) file.createNewFile()
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(file.length().coerceAtLeast(0L))
            while (currentCoroutineContext().isActive) {
                val line = raf.readLine()
                if (line == null) {
                    delay(400)
                    continue
                }
                if (IdleLockParser.isLock(line)) {
                    withContext(Dispatchers.Main) {
                        if (!SessionPower.locked.value) SessionPowerActions.lock()
                    }
                }
            }
        }
    }
}
