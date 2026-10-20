package de.mm20.launcher2.comms.files

import java.io.Closeable
import java.io.File
import java.io.IOException
import java.net.*
import java.nio.ByteBuffer
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributeView
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore

/** Explicit foreground FTP session. Authenticated, passive only, rooted through directory handles. */
class FilesFtpServer(folder: File, private val address: InetAddress, val allowUploads: Boolean = false,
    private val maxUploadBytes: Long = 2L * 1024 * 1024 * 1024) : Closeable {
    init { require(maxUploadBytes in 1..(2L * 1024 * 1024 * 1024)) }
    private val root = folder.toPath().toAbsolutePath().normalize().also {
        require(!Files.isSymbolicLink(it) && Files.isDirectory(it, LinkOption.NOFOLLOW_LINKS))
    }.toRealPath()
    private val anchor = Files.newDirectoryStream(root).let {
        @Suppress("UNCHECKED_CAST")
        (it as? SecureDirectoryStream<Path>) ?: run { it.close(); throw IOException("Secure sharing unavailable") }
    }
    private val control = try {
        require(address is Inet4Address && (address.isSiteLocalAddress || address.isLoopbackAddress))
        ServerSocket(0, 4, address)
    } catch (e: Exception) { anchor.close(); throw e }
    private val sockets = java.util.Collections.synchronizedSet(mutableSetOf<Closeable>())
    private val slots = Semaphore(4)
    private val workers = Executors.newFixedThreadPool(4)
    @Volatile private var closed = false
    val isOpen: Boolean get() = !closed
    val username = "telos"
    val password = UUID.randomUUID().toString().replace("-", "")
    val port: Int get() = control.localPort
    val url: String get() = "ftp://${address.hostAddress}:$port/"
    init {
        Thread({ while (!closed) {
            val socket = try { control.accept() } catch (_: IOException) { break }
            if (!slots.tryAcquire()) { socket.close(); continue }
            sockets.add(socket)
            try { workers.execute {
                try { socket.use { session(it) } } catch (_: Exception) { }
                finally { sockets.remove(socket); slots.release() }
            } } catch (_: Exception) { sockets.remove(socket); slots.release(); socket.close() }
        } }, "Telos-FTP").apply { isDaemon = true; start() }
    }
    private fun directory(parts: List<String>): SecureDirectoryStream<Path> {
        var current = anchor.newDirectoryStream(Paths.get("."), LinkOption.NOFOLLOW_LINKS)
        try {
            for (part in parts) {
                val next = current.newDirectoryStream(Paths.get(part), LinkOption.NOFOLLOW_LINKS)
                current.close(); current = next
            }
            return current
        } catch (e: Exception) { current.close(); throw e }
    }
    private fun path(cwd: List<String>, raw: String): List<String> {
        require(raw.length <= 2048 && raw.none { it == '\\' || it.isISOControl() })
        val result = if (raw.startsWith('/')) mutableListOf() else cwd.toMutableList()
        raw.split('/').forEach { when (it) {
            "", "." -> {}
            ".." -> { require(result.isNotEmpty()); result.removeAt(result.lastIndex) }
            else -> { require(it.length <= 255); result += it }
        } }
        require(result.size <= 64)
        return result
    }
    private fun session(socket: Socket) {
        socket.soTimeout = 120_000
        val input = socket.getInputStream()
        val out = socket.getOutputStream()
        fun reply(code: Int, text: String) { out.write("$code $text\r\n".toByteArray(Charsets.UTF_8)); out.flush() }
        var loggedIn = false
        var user = false
        var failures = 0
        var cwd = emptyList<String>()
        var passive: ServerSocket? = null
        var restart = 0L
        fun closePassive() { passive?.let { sockets.remove(it); runCatching { it.close() } }; passive = null }
        fun data(block: (Socket) -> Unit) {
            val listener = passive ?: run { reply(425, "Use EPSV or PASV first"); return }
            passive = null
            try {
                reply(150, "Opening data connection")
                val channel = listener.accept()
                if (channel.inetAddress != socket.inetAddress) { channel.close(); throw IOException("Data peer mismatch") }
                sockets.add(channel)
                try { channel.use { it.soTimeout = 30_000; block(it) } } finally { sockets.remove(channel) }
                reply(226, "Transfer complete")
            } finally { sockets.remove(listener); listener.close() }
        }
        reply(220, "Telos FTP")
        try { while (!closed) {
            val line = StringBuilder()
            while (line.length <= 4096) {
                val ch = input.read(); if (ch < 0) return
                if (ch == 10) break
                if (ch != 13) line.append(ch.toChar())
            }
            if (line.length > 4096) { reply(500, "Command too long"); return }
            val decoded = String(line.toString().map { it.code.toByte() }.toByteArray(), Charsets.UTF_8)
            val command = decoded.substringBefore(' ').uppercase(Locale.ROOT)
            val arg = decoded.substringAfter(' ', "")
            try {
                when {
                    command == "QUIT" -> { reply(221, "Goodbye"); return }
                    command == "USER" -> { loggedIn = false; closePassive(); user = arg == username; reply(331, "Password required") }
                    command == "PASS" -> {
                        loggedIn = user && MessageDigest.isEqual(arg.toByteArray(), password.toByteArray())
                        reply(if (loggedIn) 230 else 530, if (loggedIn) "Logged in" else "Login failed")
                        if (!loggedIn && ++failures >= 5) return
                    }
                    command == "FEAT" -> { out.write("211-Features\r\n UTF8\r\n EPSV\r\n PASV\r\n SIZE\r\n REST STREAM\r\n211 End\r\n".toByteArray()); out.flush() }
                    !loggedIn -> reply(530, "Login first")
                    command == "NOOP" || command == "OPTS" -> reply(200, "OK")
                    command == "SYST" -> reply(215, "UNIX Type: L8")
                    command == "TYPE" -> reply(if (arg == "I" || arg == "A") 200 else 504, "Type")
                    command == "PWD" || command == "XPWD" -> reply(257, "\"/${cwd.joinToString("/").replace("\"", "\"\"")}\"")
                    command == "CWD" || command == "CDUP" -> {
                        val next = if (command == "CDUP" && cwd.isEmpty()) emptyList() else path(cwd, if (command == "CDUP") ".." else arg)
                        directory(next).close(); cwd = next; reply(250, "Directory changed")
                    }
                    command == "EPSV" || command == "PASV" -> {
                        closePassive()
                        val listener = ServerSocket(0, 1, address).apply { soTimeout = 15_000 }
                        passive = listener; sockets.add(listener)
                        if (command == "EPSV") reply(229, "Entering Extended Passive Mode (|||${listener.localPort}|)")
                        else reply(227, "Entering Passive Mode (${address.hostAddress!!.replace('.', ',')},${listener.localPort / 256},${listener.localPort % 256})")
                    }
                    command == "LIST" || command == "NLST" || command == "MLSD" -> {
                        val parts = path(cwd, if (arg.startsWith('-')) "" else arg)
                        directory(parts).use { dir -> data { connection ->
                            val output = connection.getOutputStream().bufferedWriter(Charsets.UTF_8)
                            var count = 0
                            for (entry in dir) {
                                if (++count > 10_000) throw IOException("Directory limit")
                                val name = entry.fileName.toString()
                                if (name.any { it.isISOControl() }) continue
                                val attrs = dir.getFileAttributeView(entry.fileName, BasicFileAttributeView::class.java, LinkOption.NOFOLLOW_LINKS).readAttributes()
                                if (!attrs.isDirectory && !attrs.isRegularFile) continue
                                val row = when (command) {
                                    "NLST" -> name
                                    "MLSD" -> "type=${if (attrs.isDirectory) "dir" else "file"};size=${attrs.size()}; $name"
                                    else -> "${if (attrs.isDirectory) "d" else "-"}r-xr-xr-x 1 telos telos ${attrs.size()} Jan 01 00:00 $name"
                                }
                                output.write(row); output.write("\r\n")
                            }
                            output.flush()
                        } }
                    }
                    command == "SIZE" || command == "RETR" || command == "STOR" -> {
                        val parts = path(cwd, arg); require(parts.isNotEmpty())
                        directory(parts.dropLast(1)).use { dir ->
                            val name = Paths.get(parts.last())
                            if (command == "SIZE") {
                                val attr = dir.getFileAttributeView(name, BasicFileAttributeView::class.java, LinkOption.NOFOLLOW_LINKS).readAttributes()
                                require(attr.isRegularFile); reply(213, attr.size().toString())
                            } else if (command == "RETR") {
                                val offset = restart; restart = 0
                                dir.newByteChannel(name, setOf(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)).use { file ->
                                    require(dir.getFileAttributeView(name, BasicFileAttributeView::class.java, LinkOption.NOFOLLOW_LINKS).readAttributes().isRegularFile)
                                    require(offset <= file.size()); file.position(offset)
                                    data { connection ->
                                        val buffer = ByteBuffer.allocate(64 * 1024)
                                        while (!closed) { buffer.clear(); val n = file.read(buffer); if (n < 0) break; if (n > 0) connection.getOutputStream().write(buffer.array(), 0, n) }
                                    }
                                }
                            } else {
                                require(allowUploads && restart == 0L)
                                try {
                                    dir.getFileAttributeView(name, BasicFileAttributeView::class.java, LinkOption.NOFOLLOW_LINKS).readAttributes()
                                    throw FileAlreadyExistsException(name.toString())
                                } catch (_: NoSuchFileException) { }
                                data { connection ->
                                    var complete = false
                                    dir.newByteChannel(name, setOf(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)).use { file ->
                                        try {
                                            val buffer = ByteArray(64 * 1024); var total = 0L
                                            var n = connection.getInputStream().read(buffer)
                                            while (n >= 0) {
                                                total += n; require(total <= maxUploadBytes)
                                                val bytes = ByteBuffer.wrap(buffer, 0, n); while (bytes.hasRemaining()) file.write(bytes)
                                                n = connection.getInputStream().read(buffer)
                                            }
                                            complete = true
                                        } finally { if (!complete) runCatching { dir.deleteFile(name) } }
                                    }
                                }
                            }
                        }
                    }
                    command == "REST" -> { restart = arg.toLongOrNull()?.takeIf { it >= 0 } ?: throw IOException(); reply(350, "Restart offset accepted") }
                    else -> reply(502, "Command not supported")
                }
            } catch (_: Exception) { closePassive(); restart = 0; reply(550, "Operation unavailable") }
        } } finally { closePassive() }
    }
    override fun close() {
        closed = true; runCatching { control.close() }
        synchronized(sockets) { sockets.forEach { runCatching { it.close() } }; sockets.clear() }
        workers.shutdownNow(); runCatching { anchor.close() }
    }
}
