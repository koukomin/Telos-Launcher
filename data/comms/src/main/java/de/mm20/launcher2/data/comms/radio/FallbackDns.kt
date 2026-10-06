package de.mm20.launcher2.data.comms.radio

import okhttp3.Dns
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.net.UnknownHostException

/**
 * Uses the system resolver first and, when a name cannot be resolved (blocked or broken DNS on the
 * phone or network), asks a DNS-over-HTTPS service for the address. The service names themselves
 * are resolved by the system.
 */
object FallbackDns : Dns {

    private val resolvers = listOf(
        "https://cloudflare-dns.com/dns-query?name=%s&type=A",
        "https://dns.google/resolve?name=%s&type=A",
    )
    private val cache = HashMap<String, List<InetAddress>>()

    override fun lookup(hostname: String): List<InetAddress> {
        try {
            return Dns.SYSTEM.lookup(hostname)
        } catch (e: UnknownHostException) {
            synchronized(cache) { cache[hostname] }?.let { return it }
            for (template in resolvers) {
                val found = runCatching { query(template.format(hostname), hostname) }.getOrNull()
                if (!found.isNullOrEmpty()) {
                    synchronized(cache) { cache[hostname] = found }
                    return found
                }
            }
            throw e
        }
    }

    private fun query(url: String, hostname: String): List<InetAddress> {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 5000
        c.readTimeout = 5000
        c.setRequestProperty("Accept", "application/dns-json")
        try {
            val body = c.inputStream.bufferedReader().use { it.readText() }
            val answers = JSONObject(body).optJSONArray("Answer") ?: return emptyList()
            val out = ArrayList<InetAddress>()
            for (i in 0 until answers.length()) {
                val a = answers.getJSONObject(i)
                if (a.optInt("type") != 1) continue // A records only
                val parts = a.optString("data").split('.').mapNotNull { it.toIntOrNull() }
                if (parts.size == 4) out += InetAddress.getByAddress(hostname, ByteArray(4) { parts[it].toByte() })
            }
            return out
        } finally {
            c.disconnect()
        }
    }
}
