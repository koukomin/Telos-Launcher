package de.mm20.launcher2.ui.camera

import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.net.Inet4Address
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/** Explicit, foreground-only LAN MJPEG. The unguessable URL is the viewer credential. */
internal class CameraWebcam(private val context: Context, private val frame: () -> Bitmap?) : Closeable {
    private val workers = Executors.newFixedThreadPool(3)
    private val permits = Semaphore(2)
    private val clients = ConcurrentHashMap.newKeySet<Socket>()
    private val main = Handler(Looper.getMainLooper())
    private val token = Base64.encodeToString(ByteArray(24).also { SecureRandom().nextBytes(it) }, Base64.NO_WRAP or Base64.URL_SAFE)
    @Volatile private var server: ServerSocket? = null
    @Volatile private var closed = false

    @Synchronized fun start(): String {
        check(!closed && server == null)
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val address = manager.allNetworks.firstNotNullOfOrNull { network ->
            val capabilities = manager.getNetworkCapabilities(network) ?: return@firstNotNullOfOrNull null
            if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) && !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return@firstNotNullOfOrNull null
            manager.getLinkProperties(network)?.linkAddresses?.map { it.address }
                ?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress && !it.isAnyLocalAddress }
        } ?: error("No LAN address")
        val socket = ServerSocket(0, 2, address).apply { soTimeout = 1000 }
        server = socket
        workers.execute {
            while (!closed) {
                val client = try { socket.accept() } catch (_: java.net.SocketTimeoutException) { continue } catch (_: Exception) { break }
                if (!permits.tryAcquire()) { client.close(); continue }
                clients += client
                workers.execute {
                    try { serve(client) } catch (_: Exception) {} finally {
                        clients -= client
                        runCatching { client.close() }
                        permits.release()
                    }
                }
            }
        }
        return "http://${address.hostAddress}:${socket.localPort}/$token/"
    }

    private fun serve(socket: Socket) {
        socket.soTimeout = 3000
        val input = socket.getInputStream()
        val request = StringBuilder()
        while (request.length < 4096) {
            val next = input.read()
            if (next < 0) return
            request.append(next.toChar())
            if (request.endsWith("\r\n\r\n")) break
        }
        val output = socket.getOutputStream()
        if (request.length >= 4096 || request.lineSequence().firstOrNull() != "GET /$token/ HTTP/1.1") {
            output.write("HTTP/1.1 404 Not Found\r\nConnection: close\r\nContent-Length: 0\r\n\r\n".toByteArray())
            return
        }
        output.write("HTTP/1.1 200 OK\r\nContent-Type: multipart/x-mixed-replace; boundary=telosframe\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nConnection: close\r\n\r\n".toByteArray())
        while (!closed && !socket.isClosed) {
            val pending = CompletableFuture<Bitmap?>()
            val snapshot = Runnable {
                if (!pending.isDone) {
                    val bitmap = runCatching { frame() }.getOrNull()
                    if (!pending.complete(bitmap)) bitmap?.recycle()
                }
            }
            main.post(snapshot)
            val original = try { pending.get(2, TimeUnit.SECONDS) } catch (_: Exception) {
                main.removeCallbacks(snapshot)
                pending.complete(null)
                // A frame can finish concurrently with the timeout. Take ownership exactly once.
                pending.getNow(null)
            }
            if (original != null) {
                var scaled: Bitmap? = null
                try {
                    val ratio = 480f / maxOf(original.width, original.height).coerceAtLeast(1)
                    scaled = if (ratio < 1f) Bitmap.createScaledBitmap(original, (original.width * ratio).toInt().coerceAtLeast(1), (original.height * ratio).toInt().coerceAtLeast(1), true) else original
                    val bytes = ByteArrayOutputStream().apply { checkNotNull(scaled).compress(Bitmap.CompressFormat.JPEG, 65, this) }.toByteArray()
                    output.write("--telosframe\r\nContent-Type: image/jpeg\r\nContent-Length: ${bytes.size}\r\n\r\n".toByteArray())
                    output.write(bytes); output.write("\r\n".toByteArray()); output.flush()
                } finally {
                    if (scaled !== original) scaled?.recycle()
                    original.recycle()
                }
            }
            Thread.sleep(250)
        }
    }

    @Synchronized override fun close() {
        closed = true
        runCatching { server?.close() }; server = null
        clients.forEach { runCatching { it.close() } }
        clients.clear()
        workers.shutdownNow()
    }
}
