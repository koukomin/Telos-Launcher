package de.mm20.launcher2.network.impl

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * Pure Java/Android socket-based local network sharing server ("Telos Drop").
 * Serves a lightweight web interface on port 8080 allowing any device
 * on the same local Wi-Fi network to download shared files and copy text.
 */
object TelosDropServer {

    private const val TAG = "TelosDropServer"
    private const val DEFAULT_PORT = 8080

    data class ServerState(
        val isRunning: Boolean = false,
        val localIp: String? = null,
        val port: Int = DEFAULT_PORT,
        val sharedFileName: String? = null,
    ) {
        val serverUrl: String?
            get() = if (isRunning && localIp != null) "http://$localIp:$port" else null
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverSocket: ServerSocket? = null
    private var sharedFileTarget: File? = null
    private var sharedTextTarget: String? = null

    private val _state = MutableStateFlow(ServerState())
    val state: StateFlow<ServerState> = _state.asStateFlow()

    fun start(context: Context, fileToShare: File? = null, textToShare: String? = null) {
        if (_state.value.isRunning) {
            stop()
        }

        val appContext = context.applicationContext
        val ip = getLocalWifiIpAddress(appContext) ?: "127.0.0.1"
        sharedFileTarget = fileToShare
        sharedTextTarget = textToShare

        scope.launch {
            runCatching {
                val server = ServerSocket(DEFAULT_PORT)
                serverSocket = server
                _state.value = ServerState(
                    isRunning = true,
                    localIp = ip,
                    port = DEFAULT_PORT,
                    sharedFileName = fileToShare?.name
                )
                Log.i(TAG, "Telos Drop Server started on http://$ip:$DEFAULT_PORT")

                while (!server.isClosed) {
                    val clientSocket = server.accept()
                    scope.launch { handleClient(clientSocket) }
                }
            }.onFailure { e ->
                if (serverSocket?.isClosed != true) {
                    Log.e(TAG, "Telos Drop Server stopped with exception", e)
                }
                _state.value = ServerState(isRunning = false)
            }
        }
    }

    fun stop() {
        runCatching {
            serverSocket?.close()
        }
        serverSocket = null
        sharedFileTarget = null
        sharedTextTarget = null
        _state.value = ServerState(isRunning = false)
        Log.i(TAG, "Telos Drop Server stopped")
    }

    private fun handleClient(socket: Socket) {
        runCatching {
            socket.use { client ->
                val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                val out = client.getOutputStream()

                val requestLine = reader.readLine() ?: return
                val isDownload = requestLine.contains("GET /download")

                val file = sharedFileTarget
                val text = sharedTextTarget

                if (isDownload && file != null && file.exists()) {
                    val header = "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: application/octet-stream\r\n" +
                            "Content-Disposition: attachment; filename=\"${file.name}\"\r\n" +
                            "Content-Length: ${file.length()}\r\n" +
                            "Connection: close\r\n\r\n"
                    out.write(header.toByteArray(Charsets.UTF_8))
                    FileInputStream(file).use { input ->
                        input.copyTo(out)
                    }
                } else {
                    val html = """
                        <!DOCTYPE html>
                        <html>
                        <head>
                            <meta charset="utf-8">
                            <meta name="viewport" content="width=device-width, initial-scale=1">
                            <title>Telos Drop</title>
                            <style>
                                body { font-family: system-ui, sans-serif; background: #121318; color: #e2e2e9; padding: 2rem; max-width: 600px; margin: 0 auto; }
                                .card { background: #1f2027; border-radius: 16px; padding: 1.5rem; margin-top: 1rem; border: 1px solid #2e303a; }
                                .btn { display: inline-block; background: #3b82f6; color: white; padding: 0.75rem 1.5rem; border-radius: 8px; text-decoration: none; font-weight: bold; margin-top: 1rem; }
                                textarea { width: 100%; height: 120px; background: #121318; color: #e2e2e9; border: 1px solid #2e303a; border-radius: 8px; padding: 0.5rem; }
                            </style>
                        </head>
                        <body>
                            <h2>Telos Local Drop</h2>
                            ${if (file != null) """
                                <div class="card">
                                    <h3>Shared File</h3>
                                    <p><strong>${file.name}</strong> (${file.length() / 1024} KB)</p>
                                    <a href="/download" class="btn">Download File</a>
                                </div>
                            """ else ""}
                            ${if (text != null) """
                                <div class="card">
                                    <h3>Shared Text</h3>
                                    <textarea readonly>${text.replace("<", "&lt;").replace(">", "&gt;")}</textarea>
                                </div>
                            """ else ""}
                        </body>
                        </html>
                    """.trimIndent()

                    val bytes = html.toByteArray(Charsets.UTF_8)
                    val header = "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: text/html; charset=utf-8\r\n" +
                            "Content-Length: ${bytes.size}\r\n" +
                            "Connection: close\r\n\r\n"
                    out.write(header.toByteArray(Charsets.UTF_8))
                    out.write(bytes)
                }
                out.flush()
            }
        }.onFailure { e ->
            Log.e(TAG, "Error serving client request", e)
        }
    }

    @Suppress("DEPRECATION")
    private fun getLocalWifiIpAddress(context: Context): String? {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null
        val ipInt = wifiManager.connectionInfo.ipAddress
        if (ipInt == 0) return null
        return runCatching {
            InetAddress.getByAddress(
                byteArrayOf(
                    (ipInt and 0xff).toByte(),
                    (ipInt shr 8 and 0xff).toByte(),
                    (ipInt shr 16 and 0xff).toByte(),
                    (ipInt shr 24 and 0xff).toByte()
                )
            ).hostAddress
        }.getOrNull()
    }
}
