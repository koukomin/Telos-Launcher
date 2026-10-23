package de.mm20.launcher2.network.scanner

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.util.concurrent.atomic.AtomicInteger
import javax.net.SocketFactory

data class DiscoveredHost(
    val ip: String,
    val hostname: String,
    val latencyMs: Long,
    val openPorts: List<Int>,
    val macAddress: String? = null,
    val vendor: String? = null,
    val isGateway: Boolean = false,
)

data class PingPacketResult(
    val sequence: Int,
    val latencyMs: Long?,
    val success: Boolean,
)

data class PingResult(
    val target: String,
    val resolvedIp: String?,
    val packetsSent: Int,
    val packetsReceived: Int,
    val minLatencyMs: Long?,
    val avgLatencyMs: Long?,
    val maxLatencyMs: Long?,
    val packets: List<PingPacketResult>,
)

data class OpenPort(
    val port: Int,
    val name: String,
)

class NetworkScannerClient {

    companion object {
        val COMMON_PORTS = listOf(
            80 to "HTTP",
            443 to "HTTPS",
            22 to "SSH",
            53 to "DNS",
            8080 to "HTTP-ALT",
            8443 to "HTTPS-ALT",
            445 to "SMB",
            21 to "FTP",
            23 to "TELNET",
            1883 to "MQTT",
            3000 to "WEB-APP",
            8000 to "HTTP-DEV",
        )

        fun isLanAddress(target: String): Boolean {
            val ip = target.trim()
            if (ip == "localhost" || ip == "127.0.0.1" || ip.endsWith(".local")) return true
            if (ip.startsWith("192.168.") || ip.startsWith("10.")) return true
            if (ip.startsWith("172.")) {
                val second = ip.split(".").getOrNull(1)?.toIntOrNull() ?: 0
                if (second in 16..31) return true
            }
            return false
        }

        fun resolveVendor(mac: String?): String? {
            if (mac.isNullOrBlank()) return null
            val clean = mac.replace(":", "").replace("-", "").uppercase()
            if (clean.length < 6) return null
            val prefix = clean.substring(0, 6)
            return when {
                prefix.startsWith("001122") || prefix.startsWith("00000C") -> "Cisco"
                prefix.startsWith("001A11") || prefix.startsWith("F01898") || prefix.startsWith("ACBC32") || prefix.startsWith("3C0630") -> "Apple"
                prefix.startsWith("2811A5") || prefix.startsWith("002637") || prefix.startsWith("34145F") || prefix.startsWith("CCB0DA") -> "Samsung"
                prefix.startsWith("D83C99") || prefix.startsWith("704A0E") || prefix.startsWith("3C5AB4") || prefix.startsWith("105A17") -> "Google"
                prefix.startsWith("640980") || prefix.startsWith("286C07") || prefix.startsWith("64CC2E") || prefix.startsWith("185936") -> "Xiaomi"
                prefix.startsWith("50C7BF") || prefix.startsWith("002719") || prefix.startsWith("C46E1F") || prefix.startsWith("B09575") -> "TP-Link"
                prefix.startsWith("B827EB") || prefix.startsWith("DCA632") || prefix.startsWith("E45F01") -> "Raspberry Pi"
                prefix.startsWith("001E67") || prefix.startsWith("001500") -> "Intel"
                prefix.startsWith("ECFABC") || prefix.startsWith("CC7B35") || prefix.startsWith("708BCD") -> "Espressif (IoT)"
                else -> null
            }
        }
    }

    /**
     * Resolves the local IP address and subnet base (e.g. "192.168.1.").
     */
    fun getLocalSubnetPrefix(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (iface in interfaces) {
                if (iface.isLoopback || !iface.isUp) continue
                val addrs = iface.inetAddresses
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        val host = addr.hostAddress ?: continue
                        if (isLanAddress(host)) {
                            val parts = host.split(".")
                            if (parts.size == 4) {
                                return "${parts[0]}.${parts[1]}.${parts[2]}."
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return "192.168.1."
    }

    /**
     * Reads the system ARP neighbor table via `ip neigh` / `/proc/net/arp` to obtain real MAC addresses.
     */
    fun readArpTable(): Map<String, String> {
        val map = HashMap<String, String>()
        try {
            val process = Runtime.getRuntime().exec("ip neigh")
            process.inputStream.bufferedReader().useLines { lines ->
                for (line in lines) {
                    val parts = line.split("\\s+".toRegex())
                    if (parts.size >= 5) {
                        val ip = parts[0]
                        val macIdx = parts.indexOf("lladdr")
                        if (macIdx >= 0 && macIdx + 1 < parts.size) {
                            map[ip] = parts[macIdx + 1]
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        if (map.isEmpty()) {
            try {
                java.io.File("/proc/net/arp").useLines { lines ->
                    for (line in lines.drop(1)) {
                        val parts = line.trim().split("\\s+".toRegex())
                        if (parts.size >= 4) {
                            val ip = parts[0]
                            val mac = parts[3]
                            if (mac != "00:00:00:00:00:00") {
                                map[ip] = mac
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        return map
    }

    /**
     * Scans the subnet for active hosts and open ports.
     */
    suspend fun scanSubnet(
        onProgress: (scanned: Int, total: Int, found: Int) -> Unit = { _, _, _ -> },
        onHostFound: (DiscoveredHost) -> Unit = {},
        bypassVpn: Boolean = true,
    ): List<DiscoveredHost> = withContext(Dispatchers.IO) {
        val prefix = getLocalSubnetPrefix() ?: "192.168.1."
        val foundHosts = java.util.Collections.synchronizedList(ArrayList<DiscoveredHost>())
        val total = 254
        val scannedCounter = AtomicInteger(0)

        val socketFactory = SocketFactory.getDefault()
        val arpTable = readArpTable()

        coroutineScope {
            (1..total).chunked(25).forEach { batch ->
                val jobs = batch.map { i ->
                    async {
                        val ip = "$prefix$i"
                        val host = probeHost(ip, socketFactory, bypassVpn, arpTable[ip])
                        val sc = scannedCounter.incrementAndGet()
                        if (host != null) {
                            foundHosts.add(host)
                            onHostFound(host)
                        }
                        onProgress(sc, total, foundHosts.size)
                    }
                }
                jobs.awaitAll()
            }
        }
        foundHosts.sortedBy { host ->
            host.ip.substringAfterLast('.').toIntOrNull() ?: 0
        }
    }

    private fun probeHost(ip: String, factory: SocketFactory, bypassVpn: Boolean, mac: String? = null): DiscoveredHost? {
        val start = System.currentTimeMillis()
        var reachable = false
        var latency = 0L
        val openPorts = ArrayList<Int>()

        // Check common ports
        val quickPorts = listOf(80, 443, 22, 53, 445, 8080)
        for (port in quickPorts) {
            try {
                val socket = factory.createSocket()
                socket.connect(InetSocketAddress(ip, port), 250)
                socket.close()
                reachable = true
                openPorts.add(port)
                latency = (System.currentTimeMillis() - start).coerceAtLeast(1)
                break
            } catch (_: Exception) {}
        }

        if (!reachable) {
            try {
                val addr = InetAddress.getByName(ip)
                if (addr.isReachable(300)) {
                    reachable = true
                    latency = (System.currentTimeMillis() - start).coerceAtLeast(1)
                }
            } catch (_: Exception) {}
        }

        if (!reachable && mac == null) return null

        val hostname = try {
            val name = InetAddress.getByName(ip).canonicalHostName
            if (name == ip) "" else name
        } catch (_: Exception) { "" }

        return DiscoveredHost(
            ip = ip,
            hostname = hostname,
            latencyMs = latency,
            openPorts = openPorts,
            macAddress = mac,
            vendor = resolveVendor(mac),
            isGateway = ip.endsWith(".1"),
        )
    }

    /**
     * Performs a ping test against a target host or domain.
     */
    suspend fun ping(
        target: String,
        count: Int = 4,
        bypassVpn: Boolean = true,
    ): PingResult = withContext(Dispatchers.IO) {
        val cleanTarget = target.trim()
        val packets = ArrayList<PingPacketResult>()
        val isLan = isLanAddress(cleanTarget)
        val factory = SocketFactory.getDefault()

        var resolvedIp: String? = null
        try {
            resolvedIp = InetAddress.getByName(cleanTarget).hostAddress
        } catch (_: Exception) {}

        for (seq in 1..count) {
            val start = System.currentTimeMillis()
            var success = false
            var timeMs: Long? = null

            try {
                val destIp = resolvedIp ?: cleanTarget
                val socket = factory.createSocket()
                val port = if (isLan) 80 else 443
                socket.connect(InetSocketAddress(destIp, port), 1000)
                socket.close()
                timeMs = (System.currentTimeMillis() - start).coerceAtLeast(1)
                success = true
            } catch (_: Exception) {
                try {
                    val addr = InetAddress.getByName(resolvedIp ?: cleanTarget)
                    if (addr.isReachable(1000)) {
                        timeMs = (System.currentTimeMillis() - start).coerceAtLeast(1)
                        success = true
                    }
                } catch (_: Exception) {}
            }

            packets.add(PingPacketResult(sequence = seq, latencyMs = timeMs, success = success))
            if (seq < count) delay(200)
        }

        val successful = packets.filter { it.success && it.latencyMs != null }
        val latencies = successful.mapNotNull { it.latencyMs }

        PingResult(
            target = cleanTarget,
            resolvedIp = resolvedIp,
            packetsSent = count,
            packetsReceived = successful.size,
            minLatencyMs = latencies.minOrNull(),
            avgLatencyMs = if (latencies.isNotEmpty()) latencies.average().toLong() else null,
            maxLatencyMs = latencies.maxOrNull(),
            packets = packets,
        )
    }

    /**
     * Performs a port scan on a specific target IP/host.
     */
    suspend fun scanPorts(
        target: String,
        ports: List<Pair<Int, String>> = COMMON_PORTS,
        bypassVpn: Boolean = true,
    ): List<OpenPort> = withContext(Dispatchers.IO) {
        val factory = SocketFactory.getDefault()
        val destIp = try { InetAddress.getByName(target).hostAddress ?: target } catch (_: Exception) { target }

        coroutineScope {
            ports.map { (port, name) ->
                async {
                    try {
                        val socket = factory.createSocket()
                        socket.connect(InetSocketAddress(destIp, port), 400)
                        socket.close()
                        OpenPort(port, name)
                    } catch (_: Exception) {
                        null
                    }
                }
            }.awaitAll().filterNotNull()
        }
    }
}
