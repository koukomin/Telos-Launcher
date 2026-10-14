package de.mm20.launcher2.comms.files

import java.io.Closeable
import java.io.File
import java.net.*
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardOpenOption
import java.security.SecureRandom
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore

/** A deliberately read-only, visible-session HTTP share. No credentials or write commands. */
class FilesSharingServer(folder: File, address: InetAddress) : Closeable {
    private val root = folder.toPath().toAbsolutePath().normalize().also {
        require(!Files.isSymbolicLink(it) && Files.isDirectory(it, LinkOption.NOFOLLOW_LINKS))
    }.toRealPath()
    private val anchor: java.nio.file.SecureDirectoryStream<java.nio.file.Path> = Files.newDirectoryStream(root).let { stream ->
        @Suppress("UNCHECKED_CAST")
        (stream as? java.nio.file.SecureDirectoryStream<java.nio.file.Path>) ?: run { stream.close(); throw java.io.IOException("Secure sharing unavailable") }
    }
    private val token = ByteArray(32).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
    private val server = try {
        ServerSocket(0, 4, address.also { require(it is Inet4Address && it.isSiteLocalAddress) })
    } catch (e: Exception) { anchor.close(); throw e }
    private val workers = Executors.newFixedThreadPool(2)
    private val slots = Semaphore(2)
    private val clients = java.util.Collections.synchronizedSet(mutableSetOf<Socket>())
    @Volatile private var closed = false
    val url = "http://${address.hostAddress}:${server.localPort}/$token/"
    init {
        require(address is Inet4Address && address.isSiteLocalAddress) { "A private LAN IPv4 address is required" }
        Thread({
            while (!closed) {
                val client = try { server.accept() } catch (_: Exception) { break }
                if (!slots.tryAcquire()) { client.close(); continue }
                clients.add(client)
                try { workers.execute {
                    try { client.use { serve(it) } } catch (_: Exception) { }
                    finally { clients.remove(client); slots.release() }
                } } catch (_: Exception) { clients.remove(client); slots.release(); client.close() }
            }
        }, "Telos-File-Share").apply { isDaemon = true; start() }
    }
    private fun serve(socket: Socket) {
        socket.soTimeout = 5000
        val input = socket.getInputStream()
        val request = StringBuilder()
        while (request.length < 8192) {
            val ch = input.read(); if (ch < 0) return
            request.append(ch.toChar())
            if (request.endsWith("\r\n\r\n")) break
        }
        if (!request.endsWith("\r\n\r\n")) return
        val fields = request.toString().substringBefore("\r\n").split(' ')
        if (fields.size != 3 || fields[0] !in listOf("GET", "HEAD")) { respond(socket, 405, "Method not allowed".toByteArray()); return }
        val prefix = "/$token/"
        val path = fields[1].substringBefore('?')
        if (!path.startsWith(prefix)) { respond(socket, 404, "Not found".toByteArray()); return }
        val file = resolve(root.toFile(), path.removePrefix(prefix))
        if (file == null) { respond(socket, 404, "Not found".toByteArray()); return }
        if (Files.isDirectory(file.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            val body = buildString {
                append("<!doctype html><meta charset=utf-8><meta name=viewport content='width=device-width'><title>Telos Files</title><h1>Telos Files</h1><ul>")
                secureDirectory(file).use { children ->
                    var count = 0
                    for (child in children) {
                        if (++count > 1000) { append("<li>…</li>"); break }
                        if (Files.isSymbolicLink(file.toPath().resolve(child.fileName))) continue
                        val resolvedChild = file.toPath().resolve(child.fileName)
                        val relative = root.relativize(resolvedChild).joinToString("/") { URLEncoder.encode(it.toString(), "UTF-8").replace("+", "%20") }
                        append("<li><a href='/$token/$relative'>${escape(child.fileName.toString())}${if (Files.isDirectory(resolvedChild, LinkOption.NOFOLLOW_LINKS)) "/" else ""}</a></li>")
                    }
                }
                append("</ul>")
            }.toByteArray(Charsets.UTF_8)
            respond(socket, 200, body, "text/html; charset=utf-8", fields[0] == "HEAD")
        } else {
            // NOFOLLOW_LINKS closes the final-component symlink race; recheck ancestors immediately before opening.
            if (resolve(root.toFile(), path.removePrefix(prefix)) != file) return
            secureDirectory(file.parentFile).use { parent ->
            parent.newByteChannel(file.toPath().fileName, setOf(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)).use { channel ->
                val out = socket.getOutputStream()
                out.write(headers(200, channel.size(), "application/octet-stream").toByteArray())
                if (fields[0] == "GET") {
                    val buffer = java.nio.ByteBuffer.allocate(64 * 1024)
                    while (!closed) { buffer.clear(); val n = channel.read(buffer); if (n < 0) break; if (n > 0) out.write(buffer.array(), 0, n) }
                }
                out.flush()
            }
            }
        }
    }
    /** Secure directory-relative opens refuse ancestor replacement races; unsupported providers fail closed. */
    private fun secureDirectory(folder: File): java.nio.file.SecureDirectoryStream<java.nio.file.Path> {
        require(folder.toPath().toAbsolutePath().normalize().startsWith(root))
        var current = anchor.newDirectoryStream(java.nio.file.Paths.get("."), LinkOption.NOFOLLOW_LINKS)
        try {
            for (part in root.relativize(folder.toPath())) {
                if (part.toString().isEmpty()) continue
                require(part.toString() != ".." && part.toString() != ".")
                val next = current.newDirectoryStream(part, LinkOption.NOFOLLOW_LINKS)
                current.close(); current = next
            }
            return current
        } catch (e: Exception) { current.close(); throw e }
    }
    private fun headers(code: Int, length: Long, type: String) = "HTTP/1.1 $code ${if (code == 200) "OK" else "Error"}\r\nContent-Length: $length\r\nContent-Type: $type\r\nConnection: close\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nContent-Security-Policy: default-src 'none'; style-src 'unsafe-inline'\r\nReferrer-Policy: no-referrer\r\n\r\n"
    private fun respond(socket: Socket, code: Int, body: ByteArray, type: String = "text/plain; charset=utf-8", head: Boolean = false) {
        socket.getOutputStream().apply { write(headers(code, body.size.toLong(), type).toByteArray()); if (!head) write(body); flush() }
    }
    override fun close() {
        closed = true; runCatching { server.close() }
        synchronized(clients) { clients.forEach { runCatching { it.close() } }; clients.clear() }
        workers.shutdownNow()
        runCatching { anchor.close() }
    }
    companion object {
        fun lanAddresses(): List<InetAddress> = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            .filter { it.isUp && !it.isLoopback }.flatMap { it.inetAddresses.toList() }
            .filter { it is Inet4Address && it.isSiteLocalAddress && !it.isLoopbackAddress }
        internal fun resolve(root: File, encoded: String): File? = runCatching {
            val base = root.toPath().toRealPath()
            var path = base
            for (part in encoded.split('/').filter { it.isNotEmpty() }) {
                val name = URLDecoder.decode(part.replace("+", "%2B"), "UTF-8")
                require(name != "." && name != ".." && name.none { it == '/' || it == '\\' || it == '\u0000' })
                path = path.resolve(name)
                require(!Files.isSymbolicLink(path))
            }
            require(path.normalize().startsWith(base) && Files.exists(path, LinkOption.NOFOLLOW_LINKS))
            require(Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) || Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
            path.toFile()
        }.getOrNull()
        private fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")
    }
}
