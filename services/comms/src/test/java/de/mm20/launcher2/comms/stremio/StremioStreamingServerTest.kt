package de.mm20.launcher2.comms.stremio

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class StremioStreamingServerTest {
    @Test fun validatesOriginWithoutCredentialsOrUnexpectedPaths() {
        assertEquals("http://127.0.0.1:11470/", StremioStreamingServer.normalizeAddress(" http://127.0.0.1:11470 "))
        for (value in listOf("file:///tmp/data", "http://user:pass@host/", "https://host/?token=secret", "https://host/#x", "http://host/subdir", "http://host:0")) assertNull(value, StremioStreamingServer.normalizeAddress(value))
    }
    @Test fun officialTorrentRouteKeepsIndexTrackerHintsAndFileFilters() {
        val stream = StreamItem(addonName = "test", infoHash = "AB".repeat(20), fileIdx = 3,
            sources = listOf("tracker:udp://tracker.test:80/announce"), fileMustInclude = listOf("S01 E02"))
        assertEquals("http://127.0.0.1:11470/" + "ab".repeat(20) + "/3?tr=tracker%3Audp%3A%2F%2Ftracker.test%3A80%2Fannounce&f=S01%20E02", StremioStreamingServer.streamingUrl(StremioStreamingServer.DEFAULT_ADDRESS, stream))
        assertTrue(StremioStreamingServer.streamingUrl(StremioStreamingServer.DEFAULT_ADDRESS, stream.copy(fileIdx = null))!!.contains("/-1?"))
        assertEquals("http://127.0.0.1:11470/" + "00".repeat(20) + "/-1", StremioStreamingServer.streamingUrl(StremioStreamingServer.DEFAULT_ADDRESS, stream.copy(infoHash = "A".repeat(32), fileIdx = null, sources = emptyList(), fileMustInclude = emptyList())))
        assertNull(StremioStreamingServer.streamingUrl(StremioStreamingServer.DEFAULT_ADDRESS, stream.copy(infoHash = "../settings")))
    }
    @Test fun appliesAndVerifiesSharedCacheWithoutResettingTorrentSettings() = runBlocking {
        val current = JSONObject().put("cacheRoot", "/existing/stremio-cache").put("cacheSize", 1024)
            .put("serverVersion", "4.21.0").put("btMaxConnections", 55).put("btDownloadSpeedHardLimit", 2048)
        var written: JSONObject? = null
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/settings") { exchange ->
            val reply = if (exchange.requestMethod == "POST") {
                written = JSONObject(exchange.requestBody.bufferedReader().use { it.readText() })
                current.put("cacheSize", written!!.getLong("cacheSize"))
                written!!.optString("cacheRoot").takeIf { it.isNotEmpty() }?.let { current.put("cacheRoot", it) }
                "{\"success\":true}"
            } else JSONObject().put("values", current).toString()
            val data = reply.toByteArray()
            exchange.sendResponseHeaders(200, data.size.toLong()); exchange.responseBody.use { it.write(data) }
        }
        server.start()
        try {
            val address = "http://127.0.0.1:${server.address.port}/"
            assertEquals("/existing/stremio-cache", StremioStreamingServer.settings(address).cacheRoot)
            val result = StremioStreamingServer.configure(address, 5, "/shared/cache")
            assertEquals(5L * 1024 * 1024 * 1024, result.cacheBytes)
            assertEquals("/shared/cache", result.cacheRoot)
            assertEquals(55, written!!.getInt("btMaxConnections"))
            assertEquals(2048, written!!.getInt("btDownloadSpeedHardLimit"))
        } finally { server.stop(0) }
    }
}
