package de.mm20.launcher2.network.impl.dns

import com.celzero.firestack.intra.Intra
import com.celzero.firestack.intra.Tunnel
import de.mm20.launcher2.network.api.DnsKind
import de.mm20.launcher2.network.api.DnsServer
import de.mm20.launcher2.network.util.IpUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/** Resolves a domain through a DNS server and measures how long it takes. */
internal object DnsTester {
    private const val TIMEOUT_MS = 5000
    private const val TEST_TRANSPORT = "TelosTest"

    suspend fun test(server: DnsServer, tunnel: Tunnel?, domain: String = "example.com"): Result<Long> =
        withContext(Dispatchers.IO) {
            runCatching {
                val start = System.nanoTime()
                when (server.kind) {
                    DnsKind.System -> InetAddress.getByName(domain)
                    DnsKind.Plain, DnsKind.DnsProxy -> plain(server.url, domain)
                    DnsKind.Doh -> doh(server.url.trim(), domain)
                    DnsKind.Dot -> dot(server.url.trim(), domain)
                    DnsKind.DnsCrypt, DnsKind.Odoh -> viaEngine(server, tunnel, domain)
                }
                (System.nanoTime() - start) / 1_000_000L
            }
        }

    /** A minimal A query. */
    private fun query(domain: String): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x54, 0x31, 0x01, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))
        for (label in domain.trim('.').split('.')) {
            val b = label.toByteArray(Charsets.US_ASCII)
            out.write(b.size)
            out.write(b)
        }
        out.write(byteArrayOf(0x00, 0x00, 0x01, 0x00, 0x01))
        return out.toByteArray()
    }

    private fun check(response: ByteArray, len: Int = response.size) {
        if (len < 12) throw IllegalStateException("short answer")
        if (response[0] != 0x54.toByte() || response[1] != 0x31.toByte()) throw IllegalStateException("wrong id")
        val rcode = response[3].toInt() and 0x0f
        if (rcode != 0) throw IllegalStateException("rcode $rcode")
    }

    private fun plain(addrs: String, domain: String) {
        val q = query(domain)
        var last: Throwable? = null
        for (a in addrs.split(',').map { it.trim() }.filter { it.isNotEmpty() }) {
            try {
                val (host, port) = IpUtil.splitHostPort(a)
                val ip = IpUtil.parse(host) ?: continue
                DatagramSocket().use { s ->
                    s.soTimeout = TIMEOUT_MS
                    s.send(DatagramPacket(q, q.size, InetSocketAddress(ip, if (port > 0) port else 53)))
                    val buf = ByteArray(4096)
                    val p = DatagramPacket(buf, buf.size)
                    s.receive(p)
                    check(buf, p.length)
                }
                return
            } catch (e: Throwable) {
                last = e
            }
        }
        throw last ?: IllegalStateException("no address")
    }

    private fun doh(url: String, domain: String) {
        val q = query(domain)
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = TIMEOUT_MS
            c.readTimeout = TIMEOUT_MS
            c.requestMethod = "POST"
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/dns-message")
            c.setRequestProperty("Accept", "application/dns-message")
            c.outputStream.use { it.write(q) }
            if (c.responseCode != 200) throw IllegalStateException("HTTP ${c.responseCode}")
            check(c.inputStream.use { it.readBytes() })
        } finally {
            c.disconnect()
        }
    }

    private fun dot(url: String, domain: String) {
        val hostPort = url.removePrefix("tls://").substringBefore('/')
        val host = hostPort.substringBefore(':')
        val port = hostPort.substringAfter(':', "").toIntOrNull() ?: 853
        val raw = Socket()
        try {
            raw.connect(InetSocketAddress(host, port), TIMEOUT_MS)
            raw.soTimeout = TIMEOUT_MS
            val ssl = (SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(raw, host, port, true) as SSLSocket
            ssl.use { s ->
                s.startHandshake()
                if (!HttpsURLConnection.getDefaultHostnameVerifier().verify(host, s.session)) {
                    throw IllegalStateException("certificate does not match $host")
                }
                val q = query(domain)
                val out = s.outputStream
                out.write(byteArrayOf((q.size shr 8).toByte(), q.size.toByte()))
                out.write(q)
                out.flush()
                val input = DataInputStream(s.inputStream)
                val len = input.readUnsignedShort()
                val buf = ByteArray(len)
                input.readFully(buf)
                check(buf)
            }
        } finally {
            runCatching { raw.close() }
        }
    }

    /** DNSCrypt and ODoH are only available inside the Go engine. */
    private fun viaEngine(server: DnsServer, tunnel: Tunnel?, domain: String) {
        if (tunnel == null) throw IllegalStateException("Telos Network is not running")
        val id = TEST_TRANSPORT
        when (server.kind) {
            DnsKind.DnsCrypt -> {
                server.relay?.takeIf { it.isNotBlank() }?.let { Intra.addDNSCryptRelay(tunnel, it) }
                Intra.addDNSCryptTransport(tunnel, id, server.url.trim())
            }
            else -> Intra.addODoHTransport(tunnel, id, server.relay.orEmpty().trim(), server.url.trim(), "")
        }
        val m = tunnel.getResolver().get(id).measure(domain, 1, 1)
        if (m.success <= 0) throw IllegalStateException(m.errors.ifBlank { "no answer" })
    }
}
