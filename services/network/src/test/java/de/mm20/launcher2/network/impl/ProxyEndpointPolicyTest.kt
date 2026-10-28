package de.mm20.launcher2.network.impl

import de.mm20.launcher2.network.api.*
import org.junit.Assert.*
import org.junit.Test
import java.net.URI

class ProxyEndpointPolicyTest {
    @Test fun credentialsCannotChangeTheProxyOrigin() {
        val address = ProxyEndpointPolicy.address(ProxyKind.Socks5, "proxy.example", 1080, "user@other/", "a:b@c#d")
        val uri = URI(address)
        assertEquals("proxy.example", uri.host)
        assertEquals(1080, uri.port)
        assertEquals("user@other/:a:b@c#d", uri.userInfo)
        assertEquals("socks5", uri.scheme)
    }
    @Test fun invalidOriginsAndPortsAreRejected() {
        for ((host, port) in listOf("http://host/path" to 80, "host with spaces" to 1080, "host" to 0, "host" to 65536)) {
            assertTrue(runCatching { ProxyEndpointPolicy.address(ProxyKind.HttpConnect, host, port, "", "") }.isFailure)
        }
    }
    @Test fun absentDisabledOrUnregisteredProxyBlocksInsteadOfGoingDirect() {
        val endpoint = ProxyEndpoint(1, "test", ProxyKind.Socks5, "127.0.0.1", 1080, true)
        assertEquals(WgRoute.Block, ProxyEndpointPolicy.route(null, true, Protocol.Tcp))
        assertEquals(WgRoute.Block, ProxyEndpointPolicy.route(endpoint, false, Protocol.Tcp))
        assertEquals(WgRoute.Block, ProxyEndpointPolicy.route(endpoint.copy(enabled = false), true, Protocol.Tcp))
        assertEquals(WgRoute.Via(endpoint.proxyId), ProxyEndpointPolicy.route(endpoint, true, Protocol.Udp))
        assertEquals(WgRoute.Block, ProxyEndpointPolicy.route(endpoint.copy(kind = ProxyKind.HttpConnect), true, Protocol.Udp))
        assertEquals(WgRoute.Via(endpoint.proxyId), ProxyEndpointPolicy.route(endpoint.copy(kind = ProxyKind.HttpConnect), true, Protocol.Tcp))
    }
}
