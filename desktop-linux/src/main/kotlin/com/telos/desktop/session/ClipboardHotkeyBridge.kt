package com.telos.desktop.session

import com.telos.desktop.overlay.TelosClipboardManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.charset.StandardCharsets

/**
 * Accepts Super+V (labwc W-v) toggles over `$XDG_RUNTIME_DIR/telos-clipboard.sock`.
 * Compose Meta+V and the panel clip glyph also open the same Helix popup.
 */
object ClipboardHotkeyBridge {
    const val SOCK_NAME = "telos-clipboard.sock"

    fun sockFile(): File =
        File(System.getenv("XDG_RUNTIME_DIR") ?: System.getProperty("java.io.tmpdir"), SOCK_NAME)

    fun toggle() {
        TelosClipboardManager.popup = !TelosClipboardManager.popup
    }

    suspend fun listenLoop() = withContext(Dispatchers.IO) {
        val sock = sockFile()
        while (currentCoroutineContext().isActive) {
            runCatching { if (sock.exists()) sock.delete() }
            try {
                ServerSocketChannel.open(StandardProtocolFamily.UNIX).use { server ->
                    server.bind(UnixDomainSocketAddress.of(sock.toPath()))
                    runCatching { sock.setReadable(true, false); sock.setWritable(true, false) }
                    while (currentCoroutineContext().isActive) {
                        val client: SocketChannel = server.accept()
                        client.use { ch ->
                            val buf = ByteBuffer.allocate(256)
                            val n = ch.read(buf)
                            if (n > 0) {
                                buf.flip()
                                val msg = StandardCharsets.UTF_8.decode(buf).toString().trim()
                                if (msg.isEmpty() || msg.equals("toggle", ignoreCase = true) ||
                                    msg.contains("clipboard", ignoreCase = true)
                                ) {
                                    toggle()
                                }
                            } else {
                                toggle()
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                delay(800)
            } finally {
                runCatching { if (sock.exists()) sock.delete() }
            }
        }
    }
}
