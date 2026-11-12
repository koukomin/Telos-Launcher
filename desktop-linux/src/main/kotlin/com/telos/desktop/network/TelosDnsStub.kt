package com.telos.desktop.network

import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Tiny local DNS forwarder for VOS-2 host-list filtering.
 * Binds 127.0.0.1 UDP+TCP, answers NXDOMAIN for blocked names (allowlist wins),
 * and forwards everything else to an upstream resolver. No root required to listen.
 */
object TelosDnsStub {
    const val DEFAULT_PORT = 5353
    private const val RCODE_NXDOMAIN = 3
    private const val RCODE_FORMERR = 1
    private const val RCODE_SERVFAIL = 2

    data class Lists(val blocklist: List<String>, val allowlist: List<String>)

    private val running = AtomicBoolean(false)
    private val listsRef = AtomicReference(Lists(emptyList(), emptyList()))
    private val upstreamRef = AtomicReference(InetAddress.getByName("9.9.9.9"))
    @Volatile private var udpSocket: DatagramSocket? = null
    @Volatile private var tcpServer: ServerSocket? = null
    @Volatile private var udpThread: Thread? = null
    @Volatile private var tcpThread: Thread? = null
    @Volatile private var boundPort: Int? = null

    fun isRunning(): Boolean = running.get() && udpSocket?.isClosed == false

    fun listeningPort(): Int? = boundPort.takeIf { isRunning() }

    fun updateLists(blocklist: List<String>, allowlist: List<String>) {
        listsRef.set(Lists(blocklist, allowlist))
    }

    fun updateUpstream(address: InetAddress) {
        upstreamRef.set(address)
    }

    @Synchronized
    fun start(
        port: Int,
        blocklist: List<String>,
        allowlist: List<String>,
        upstream: InetAddress = resolveDefaultUpstream(),
    ): Boolean {
        stop()
        if (port !in 1..65535) return false
        updateLists(blocklist, allowlist)
        updateUpstream(upstream)
        return try {
            val udp = DatagramSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), port))
                soTimeout = 1000
            }
            val tcp = ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), port))
                soTimeout = 1000
            }
            udpSocket = udp
            tcpServer = tcp
            boundPort = port
            running.set(true)
            udpThread = Thread({ udpLoop(udp) }, "telos-dns-stub-udp").apply {
                isDaemon = true
                start()
            }
            tcpThread = Thread({ tcpLoop(tcp) }, "telos-dns-stub-tcp").apply {
                isDaemon = true
                start()
            }
            true
        } catch (_: Exception) {
            stop()
            false
        }
    }

    @Synchronized
    fun stop() {
        running.set(false)
        runCatching { udpSocket?.close() }
        runCatching { tcpServer?.close() }
        udpSocket = null
        tcpServer = null
        udpThread = null
        tcpThread = null
        boundPort = null
    }

    fun isQueryBlocked(qname: String, blocklist: List<String>, allowlist: List<String>): Boolean {
        val host = normalizeHost(qname) ?: return false
        if (matchesDomainList(host, allowlist)) return false
        return matchesDomainList(host, blocklist)
    }

    fun matchesDomainList(hostRaw: String, domains: List<String>): Boolean {
        val host = normalizeHost(hostRaw) ?: return false
        for (raw in domains) {
            val rule = normalizeHost(raw) ?: continue
            when {
                rule.startsWith("*.") -> {
                    val suffix = rule.removePrefix("*.")
                    if (suffix.isEmpty()) continue
                    if (host == suffix || host.endsWith(".$suffix")) return true
                }
                host == rule -> return true
                host.endsWith(".$rule") -> return true
            }
        }
        return false
    }

    fun normalizeHost(value: String): String? {
        var host = value.trim().lowercase().trimEnd('.')
        if (host.isEmpty()) return null
        if (host.startsWith("http://")) host = host.removePrefix("http://")
        if (host.startsWith("https://")) host = host.removePrefix("https://")
        host = host.substringBefore('/').substringBefore(':').trimEnd('.')
        return host.takeIf { it.isNotEmpty() }
    }

    fun extractQname(query: ByteArray): String? {
        if (query.size < 12) return null
        var i = 12
        val labels = ArrayList<String>()
        while (i < query.size) {
            val len = query[i].toInt() and 0xFF
            when {
                len == 0 -> { i++; break }
                len and 0xC0 == 0xC0 -> return null
                len > 63 -> return null
                else -> {
                    i++
                    if (i + len > query.size) return null
                    labels += String(query, i, len, Charsets.US_ASCII)
                    i += len
                }
            }
        }
        return labels.joinToString(".").lowercase().ifEmpty { "." }
    }

    fun buildNxDomain(query: ByteArray): ByteArray? = buildResponse(query, RCODE_NXDOMAIN)
    fun buildFormErr(query: ByteArray): ByteArray? = buildResponse(query, RCODE_FORMERR)
    fun buildServFail(query: ByteArray): ByteArray? = buildResponse(query, RCODE_SERVFAIL)

    fun resolveDefaultUpstream(): InetAddress {
        val fromPref = DesktopNetworkController.readDnsPreference().lines()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() && !it.startsWith("#") }
            ?.let { DesktopNetworkController.parseDnsLine(it) }
            ?.address
            ?.takeIf { it.matches(Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")) }
        if (fromPref != null) {
            runCatching { return InetAddress.getByName(fromPref) }
        }
        if (DesktopNetworkController.readNextDnsConfigId() != null) {
            runCatching { return InetAddress.getByName("45.90.28.0") }
        }
        val resolv = runCatching {
            java.io.File("/etc/resolv.conf").takeIf { it.isFile }?.readLines().orEmpty()
        }.getOrDefault(emptyList())
        for (line in resolv) {
            val trimmed = line.trim()
            if (!trimmed.startsWith("nameserver")) continue
            val host = trimmed.substringAfter("nameserver").trim().substringBefore(' ')
            if (host.startsWith("127.")) continue
            runCatching { return InetAddress.getByName(host) }
        }
        return InetAddress.getByName("9.9.9.9")
    }

    private fun buildResponse(query: ByteArray, rcode: Int): ByteArray? {
        if (query.size < 12) return null
        val out = query.copyOf(query.size.coerceAtLeast(12))
        out[2] = ((query[2].toInt() and 0x01) or 0x80).toByte()
        out[3] = ((query[3].toInt() and 0x70) or 0x80 or (rcode and 0x0F)).toByte()
        out[6] = 0; out[7] = 0; out[8] = 0; out[9] = 0; out[10] = 0; out[11] = 0
        val qEnd = questionEnd(query) ?: return out.copyOf(12)
        return out.copyOf(qEnd)
    }

    private fun questionEnd(query: ByteArray): Int? {
        if (query.size < 12) return null
        var i = 12
        while (i < query.size) {
            val len = query[i].toInt() and 0xFF
            when {
                len == 0 -> { i++; break }
                len and 0xC0 == 0xC0 -> { i += 2; break }
                else -> {
                    i += 1 + len
                    if (i > query.size) return null
                }
            }
        }
        if (i + 4 > query.size) return null
        return i + 4
    }

    private fun handleQuery(query: ByteArray): ByteArray {
        val qname = extractQname(query) ?: return buildFormErr(query) ?: query
        val lists = listsRef.get()
        if (isQueryBlocked(qname, lists.blocklist, lists.allowlist)) {
            return buildNxDomain(query) ?: query
        }
        return forward(query) ?: (buildServFail(query) ?: query)
    }

    private fun forward(query: ByteArray): ByteArray? = try {
        DatagramSocket().use { sock ->
            sock.soTimeout = 2500
            sock.send(DatagramPacket(query, query.size, upstreamRef.get(), 53))
            val buf = ByteArray(4096)
            val resp = DatagramPacket(buf, buf.size)
            sock.receive(resp)
            buf.copyOf(resp.length)
        }
    } catch (_: Exception) { null }

    private fun udpLoop(socket: DatagramSocket) {
        val buf = ByteArray(4096)
        while (running.get() && !socket.isClosed) {
            try {
                val req = DatagramPacket(buf, buf.size)
                socket.receive(req)
                val answer = handleQuery(buf.copyOf(req.length))
                socket.send(DatagramPacket(answer, answer.size, req.socketAddress))
            } catch (_: SocketTimeoutException) {
            } catch (_: Exception) {
                if (!running.get() || socket.isClosed) break
            }
        }
    }

    private fun tcpLoop(server: ServerSocket) {
        while (running.get() && !server.isClosed) {
            try {
                val client = server.accept()
                Thread({ handleTcpClient(client) }, "telos-dns-stub-tcp-client").apply {
                    isDaemon = true
                    start()
                }
            } catch (_: SocketTimeoutException) {
            } catch (_: Exception) {
                if (!running.get() || server.isClosed) break
            }
        }
    }

    private fun handleTcpClient(socket: Socket) {
        socket.use { client ->
            client.soTimeout = 3000
            val input = DataInputStream(client.getInputStream())
            val output = DataOutputStream(client.getOutputStream())
            val len = input.readUnsignedShort()
            if (len <= 0 || len > 4096) return
            val query = ByteArray(len)
            input.readFully(query)
            val answer = handleQuery(query)
            output.writeShort(answer.size)
            output.write(answer)
            output.flush()
        }
    }
}
