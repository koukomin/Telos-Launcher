package de.mm20.launcher2.network.impl.dns

import de.mm20.launcher2.network.api.DnsKind
import de.mm20.launcher2.network.api.DnsServer
import java.net.URLEncoder
import java.util.Locale

/** Builds NextDNS servers from a configuration ID and an optional device name. */
object NextDns {
    private val BOOTSTRAP = listOf("45.90.28.0", "45.90.30.0", "2a07:a8c0::", "2a07:a8c1::")
    private val ID = Regex("^[0-9a-zA-Z]{4,12}$")

    /** True if [id] looks like a NextDNS configuration ID (letters and digits only). */
    fun isValidId(id: String): Boolean = ID.matches(id.trim())

    /** `https://dns.nextdns.io/<id>[/<device>]` */
    fun dohUrl(id: String, device: String = ""): String {
        val d = device.trim()
        val enc = if (d.isEmpty()) "" else "/" + URLEncoder.encode(d, "UTF-8").replace("+", "%20")
        return "https://dns.nextdns.io/${id.trim()}$enc"
    }

    /** `tls://[<device>-]<id>.dns.nextdns.io`; spaces of the device name become `--`. */
    fun dotUrl(id: String, device: String = ""): String {
        val d = device.trim().lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), "--")
            .replace(Regex("[^a-z0-9-]"), "")
        val prefix = if (d.isEmpty()) "" else "$d-"
        return "tls://$prefix${id.trim()}.dns.nextdns.io"
    }

    /** A custom (not yet stored) server for the DoH variant. */
    fun doh(id: String, device: String, name: String) = DnsServer(
        id = "", kind = DnsKind.Doh, name = name, url = dohUrl(id, device),
        bootstrapIps = BOOTSTRAP, provider = "NextDNS",
    )

    /** A custom (not yet stored) server for the DoT variant. */
    fun dot(id: String, device: String, name: String) = DnsServer(
        id = "", kind = DnsKind.Dot, name = name, url = dotUrl(id, device),
        bootstrapIps = BOOTSTRAP, provider = "NextDNS",
    )
}
