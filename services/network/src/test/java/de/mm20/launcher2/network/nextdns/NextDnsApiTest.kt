package de.mm20.launcher2.network.nextdns

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class NextDnsApiTest {
    private val key = "0123456789abcdef0123456789abcdef01234567"
    private class Fake(private vararg val replies: NextDnsResponse) : NextDnsTransport {
        val requests = mutableListOf<NextDnsRequest>()
        override suspend fun exchange(request: NextDnsRequest): NextDnsResponse {
            requests += request
            return replies[requests.size - 1]
        }
    }
    @Test fun actualHttpRequestUsesOfficialOriginAndApiKeyHeaderOnly() {
        val input = NextDnsRequest("POST", "/profiles/abc123/allowlist", key, "{\"id\":\"example.org\",\"active\":true}")
        val request = buildNextDnsHttpRequest(input)
        assertEquals("https", request.url.scheme)
        assertEquals("api.nextdns.io", request.url.host)
        assertEquals("/profiles/abc123/allowlist", request.url.encodedPath)
        assertNull(request.url.query)
        assertEquals(key, request.header("X-Api-Key"))
        assertNull(request.header("Authorization"))
        assertEquals("application/json", request.header("Accept"))
        assertEquals("POST", request.method)
        assertFalse(input.toString().contains(key))
        assertFalse(NextDnsCredentials("abc123", key).toString().contains(key))
    }
    @Test fun parsesRealLogEnvelopeAndNestedDeviceAndCursor() = runBlocking {
        val fake = Fake(NextDnsResponse(200, """{"data":[{"timestamp":"2026-10-20T12:00:00Z","domain":"ads.example.org","status":"blocked","device":{"id":"phone1","name":"Phone"},"protocol":"DNS-over-HTTPS","reasons":[{"id":"blocklist:ads","name":"Ads"}]}],"meta":{"pagination":{"cursor":"a+b/c="}}}"""))
        val page = NextDnsApi(fake).logs("ABC123", key, "ads & trackers", "blocked", "previous+page/")
        assertEquals("Phone", page.items.single().deviceName)
        assertEquals(listOf("Ads"), page.items.single().reasons)
        assertEquals("a+b/c=", page.cursor)
        val request = fake.requests.single()
        assertEquals("GET", request.method)
        assertEquals("/profiles/abc123/logs?limit=100&sort=desc&search=ads+%26+trackers&status=blocked&cursor=previous%2Bpage%2F", request.path)
        assertEquals(key, request.apiKey)
        assertNull(request.body)
        assertFalse(request.toString().contains(key))
    }
    @Test fun handlesApplicationErrorsEvenOnHttp200WithoutExposingServerDetails() = runBlocking {
        val fake = Fake(NextDnsResponse(200, """{"errors":[{"code":"invalid","detail":"secret server diagnostic"}],"data":[]}"""))
        try { NextDnsApi(fake).rules("abc123", key, true); fail("Must fail") }
        catch (failure: NextDnsException) {
            assertEquals(NextDnsErrorKind.INVALID, failure.kind)
            assertFalse(failure.toString().contains("secret"))
        }
    }
    @Test fun mapsAuthenticationAndRateLimitsBeforeReadingNonJsonBodies() = runBlocking {
        for ((status, kind) in listOf(401 to NextDnsErrorKind.AUTH, 403 to NextDnsErrorKind.AUTH, 429 to NextDnsErrorKind.RATE_LIMIT)) {
            val fake = Fake(NextDnsResponse(status, "<html>private diagnostic</html>"))
            try { NextDnsApi(fake).profile("abc123", key); fail("Must fail") }
            catch (failure: NextDnsException) { assertEquals(kind, failure.kind) }
        }
    }
    @Test fun rulesUseDocumentedMethodsPathsAndBodies() = runBlocking {
        val fake = Fake(NextDnsResponse(200,"{\"data\":{}}"), NextDnsResponse(204,""), NextDnsResponse(204,""))
        val api = NextDnsApi(fake)
        api.putRule("abc123", key, true, " BÜCHER.example. ")
        api.setRuleActive("abc123", key, false, "ads.example.org", false)
        api.deleteRule("abc123", key, true, "ads.example.org")
        assertEquals(listOf("POST", "PATCH", "DELETE"), fake.requests.map { it.method })
        assertEquals(listOf("/profiles/abc123/allowlist", "/profiles/abc123/denylist/ads.example.org", "/profiles/abc123/allowlist/ads.example.org"), fake.requests.map { it.path })
        val body = Json.parseToJsonElement(fake.requests[0].body!!).jsonObject
        assertEquals("xn--bcher-kva.example", body["id"]!!.jsonPrimitive.content)
        assertEquals(true, body["active"]!!.jsonPrimitive.boolean)
        assertEquals(false, Json.parseToJsonElement(fake.requests[1].body!!).jsonObject["active"]!!.jsonPrimitive.boolean)
        assertNull(fake.requests[2].body)
    }
    @Test fun rulePaginationCollectsEveryPageAndRejectsRepeatedCursor() = runBlocking {
        val page = """{"data":[{"id":"first.example","active":true}],"meta":{"pagination":{"cursor":"next"}}}"""
        val fake = Fake(NextDnsResponse(200,page), NextDnsResponse(200,"""{"data":[{"id":"second.example","active":false}],"meta":{"pagination":{"cursor":null}}}"""))
        val rules = NextDnsApi(fake).rules("abc123",key,false)
        assertEquals(listOf("first.example", "second.example"), rules.map { it.id })
        assertTrue(fake.requests.last().path.endsWith("&cursor=next"))
        val looping = Fake(NextDnsResponse(200,page),NextDnsResponse(200,page))
        try { NextDnsApi(looping).rules("abc123",key,true); fail("Must fail") }
        catch (failure: NextDnsException) { assertEquals(NextDnsErrorKind.API,failure.kind) }
        assertEquals(2,looping.requests.size)
    }
    @Test fun malformedAndOversizedResponsesCannotBecomeEmptySuccess() = runBlocking {
        for (body in listOf("{}", "{\"data\":{}}", "not json", "{\"data\":[{\"domain\":\"x.example\"}]}", " ".repeat(1_048_577))) {
            val fake = Fake(NextDnsResponse(200,body))
            try { NextDnsApi(fake).logs("abc123",key,"",null,null); fail("Must fail") }
            catch (failure: NextDnsException) { assertEquals(NextDnsErrorKind.API,failure.kind) }
        }
    }
    @Test fun invalidInputsNeverReachTransportAndMutationsAreNotRetried() = runBlocking {
        val fake = Fake(NextDnsResponse(400,"{\"errors\":[{\"code\":\"invalid\"}]}"))
        val api = NextDnsApi(fake)
        try { api.putRule("abc123",key,true,"https://example.org/path"); fail("Must fail") }
        catch (failure: NextDnsException) { assertEquals(NextDnsErrorKind.INVALID,failure.kind) }
        assertTrue(fake.requests.isEmpty())
        try { api.putRule("abc123",key,true,"example.org"); fail("Must fail") }
        catch (failure: NextDnsException) { assertEquals(NextDnsErrorKind.INVALID,failure.kind) }
        assertEquals(1,fake.requests.size)
    }
}
