package com.telos.desktop.session

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.telos.desktop.overlay.SessionPowerActions
import com.telos.desktop.overlay.TelosClipboardManager
import com.telos.desktop.overlay.TelosNotificationCenter
import com.telos.desktop.ui.DesktopChrome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import java.nio.charset.StandardCharsets

object LayerShellParser {
    fun eventId(line: String): String? {
        val s = line.trim()
        if (s.isEmpty() || s[0] != '{') return null
        val event = FreedesktopNotifyParser.jsonString(s, "event") ?: return null
        if (event == "ready") return "ready"
        if (event == "click") return FreedesktopNotifyParser.jsonString(s, "id")
        return event
    }
}

object LayerShellBridge {
    var connected by mutableStateOf(false)
        private set
    var extraHandler: ((String) -> Unit)? = null

    fun dispatch(id: String) {
        when (id) {
            "ready" -> connected = true
            "kickoff" -> DesktopChrome.toggleAppGrid()
            "overview" -> DesktopChrome.toggleOverview()
            "control" -> DesktopChrome.toggleControlCenter()
            "notify" -> TelosNotificationCenter.open.value = !TelosNotificationCenter.open.value
            "clipboard" -> TelosClipboardManager.popup = !TelosClipboardManager.popup
            "lock" -> SessionPowerActions.lock()
            else -> extraHandler?.invoke(id)
        }
    }

    suspend fun listenLoop() = withContext(Dispatchers.IO) {
        val sock = File(System.getenv("XDG_RUNTIME_DIR") ?: System.getProperty("java.io.tmpdir"), WaylandShell.LAYER_SOCK)
        while (currentCoroutineContext().isActive) {
            if (!sock.exists()) {
                delay(500)
                continue
            }
            try {
                SocketChannel.open(StandardProtocolFamily.UNIX).use { ch ->
                    ch.connect(UnixDomainSocketAddress.of(sock.toPath()))
                    val buf = ByteBuffer.allocate(4096)
                    val acc = StringBuilder()
                    while (currentCoroutineContext().isActive && ch.isConnected) {
                        buf.clear()
                        val n = ch.read(buf)
                        if (n < 0) break
                        if (n == 0) {
                            delay(50)
                            continue
                        }
                        buf.flip()
                        acc.append(StandardCharsets.UTF_8.decode(buf))
                        while (true) {
                            val text = acc.toString()
                            val nl = text.indexOf('\n')
                            if (nl < 0) break
                            val line = text.substring(0, nl)
                            acc.delete(0, nl + 1)
                            LayerShellParser.eventId(line)?.let { dispatch(it) }
                        }
                    }
                }
            } catch (_: Exception) {
                connected = false
                delay(800)
            }
        }
        connected = false
    }
}
