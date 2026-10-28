package de.mm20.launcher2.network.impl

import de.mm20.launcher2.network.api.*
import java.net.IDN
import java.net.URI
import java.net.URLEncoder

internal object ProxyEndpointPolicy {
    fun address(kind: ProxyKind, host: String, port: Int, username: String, password: String): String {
        require(port in 1..65535 && username.length <= 512 && password.length <= 512)
        require(username.isNotEmpty() || password.isEmpty())
        require(host.isNotBlank() && host.length <= 253 && host.none { it.isWhitespace() || it.isISOControl() })
        val plain = host.removePrefix("[").removeSuffix("]")
        val normalized = if (':' in plain) plain else IDN.toASCII(plain)
        val scheme = if (kind == ProxyKind.Socks5) "socks5" else "http"
        val origin = URI(scheme, null, normalized, port, null, null, null)
        if (username.isEmpty()) return origin.toASCIIString()
        fun encode(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
        return URI("$scheme://${encode(username)}:${encode(password)}@${origin.rawAuthority}").toASCIIString()
    }
    fun route(endpoint: ProxyEndpoint?, registered: Boolean, protocol: Protocol): WgRoute = when {
        endpoint == null || !endpoint.enabled || !registered -> WgRoute.Block
        endpoint.kind == ProxyKind.HttpConnect && protocol != Protocol.Tcp -> WgRoute.Block
        else -> WgRoute.Via(endpoint.proxyId)
    }
}
