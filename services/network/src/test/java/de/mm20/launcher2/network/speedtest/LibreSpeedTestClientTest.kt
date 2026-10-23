package de.mm20.launcher2.network.speedtest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import okio.Buffer

class LibreSpeedTestClientTest {
    @Test
    fun customServerBuildsLibreSpeedEndpointsAndNormalizesTrailingSlash() {
        val server = LibreSpeedTestClient.customServer("https://speed.example/backend/")

        assertEquals("https://speed.example/backend/empty.php", server?.pingUrl.toString())
        assertEquals("https://speed.example/backend/garbage.php", server?.downloadUrl.toString())
        assertEquals(server?.pingUrl, server?.uploadUrl)
    }

    @Test
    fun customServerRejectsNonHttpsAndQueryParameters() {
        assertNull(LibreSpeedTestClient.customServer("http://speed.example/backend/"))
        assertNull(LibreSpeedTestClient.customServer("https://speed.example/backend/?token=x"))
    }

    @Test
    fun customServerRejectsOversizedUrls() {
        assertNull(LibreSpeedTestClient.customServer("https://speed.example/${"a".repeat(2_048)}"))
    }

    @Test
    fun publicServerParserKeepsOnlyCompleteHttpsServers() {
        val servers = LibreSpeedTestClient.parsePublicServers(
            """
                [
                  {
                    "name": "Community server",
                    "pingURL": "https://speed.example/backend/empty.php",
                    "dlURL": "https://speed.example/backend/garbage.php",
                    "ulURL": "https://speed.example/backend/empty.php"
                  },
                  {
                    "name": "Insecure server",
                    "pingURL": "http://speed.example/empty.php",
                    "dlURL": "http://speed.example/garbage.php",
                    "ulURL": "http://speed.example/empty.php"
                  },
                  {
                    "name": "Incomplete server",
                    "pingURL": "https://other.example/empty.php"
                  }
                ]
            """.trimIndent(),
        )

        assertEquals(listOf("Community server"), servers.map { it.name })
    }

    @Test
    fun publicServerNamesAreBoundedForDisplay() {
        val servers = LibreSpeedTestClient.parsePublicServers(
            """
                [{
                  "name": "${"S".repeat(121)}",
                  "pingURL": "https://speed.example/backend/empty.php",
                  "dlURL": "https://speed.example/backend/garbage.php",
                  "ulURL": "https://speed.example/backend/empty.php"
                }]
            """.trimIndent(),
        )

        assertEquals(120, servers.single().name.length)
    }

    @Test
    fun speedCalculationConvertsBytesAndElapsedTimeToMegabitsPerSecond() {
        assertEquals(8.0, LibreSpeedTestClient.megabitsPerSecond(1_000_000, 1_000), 0.0)
        assertEquals(0.0, LibreSpeedTestClient.megabitsPerSecond(0, 1_000), 0.0)
        assertEquals(0.0, LibreSpeedTestClient.megabitsPerSecond(1_000_000, 0), 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun downloadReaderRejectsResponsesThatExceedItsConfiguredLimit() {
        val source = Buffer().write(ByteArray(51))

        LibreSpeedTestClient.readBoundedBytes(source, maxBytes = 50)
    }

    @Test
    fun downloadReaderAcceptsResponsesWithinItsConfiguredLimit() {
        val source = Buffer().write(ByteArray(50))

        assertEquals(50L, LibreSpeedTestClient.readBoundedBytes(source, maxBytes = 50L))
    }
}
