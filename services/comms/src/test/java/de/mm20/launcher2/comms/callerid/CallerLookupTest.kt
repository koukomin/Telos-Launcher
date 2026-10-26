package de.mm20.launcher2.comms.callerid

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/** Interceptors return synthetic pages locally: no telephone number leaves the test process. */
class CallerLookupTest {
    @Test fun optOutPreventsRequestsAndGreekAliasesReuseOneResult() = runBlocking {
        val enabled = MutableStateFlow(false)
        val requests = AtomicInteger()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests.incrementAndGet()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("<h1>Αριθμός τηλεφώνου: 2100000000</h1><h3>Test business</h3><p>Αυτός ο τίτλος γράφτηκε από τον χρήστη</p>".toResponseBody()).build()
        }.build()
        val lookup = CallerLookup(enabled, client)
        assertNull(lookup.lookup("2100000000"))
        assertEquals(0, requests.get())
        enabled.value = true
        assertEquals("Test business", lookup.lookup("+30 2100000000")?.suggestedName)
        assertEquals("Test business", lookup.lookup("00302100000000")?.suggestedName)
        assertEquals(1, requests.get())
        assertEquals(1, lookup.results.value.size)
        enabled.value = false
        assertNull(lookup.lookup("2100000000"))
        assertEquals(1, requests.get())
    }
    @Test fun redirectsCannotLeakNumberToAnotherHost() = runBlocking {
        val requests = AtomicInteger()
        val client = OkHttpClient.Builder().followRedirects(false).addInterceptor { chain ->
            requests.incrementAndGet()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(302).message("Found")
                .header("Location", "https://example.invalid/collect").body("".toResponseBody()).build()
        }.build()
        assertNull(CallerLookup(MutableStateFlow(true), client).lookup("2100000000"))
        assertEquals(1, requests.get())
    }
}
