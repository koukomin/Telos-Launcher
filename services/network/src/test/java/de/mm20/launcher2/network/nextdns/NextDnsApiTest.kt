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
    @Test fun loadsAndPatchesOnlyKnownProfileSettingsPages() = runBlocking {
        val fake = Fake(
            NextDnsResponse(200, """{"data":{"aiThreatDetection":true,"threatIntelligenceFeeds":false}}"""),
            NextDnsResponse(204, ""),
        )
        val api = NextDnsApi(fake)
        val settings = api.settings("ABC123", key, NextDnsSettingsPage.SECURITY)
        assertEquals(NextDnsSettingsPage.SECURITY, settings.page)
        assertEquals(true, settings.values["aiThreatDetection"]!!.jsonPrimitive.boolean)

        val patch = buildJsonObject { put("aiThreatDetection", false) }
        api.patchSettings("abc123", key, NextDnsSettingsPage.SECURITY, patch)
        assertEquals(listOf("GET", "PATCH"), fake.requests.map { it.method })
        assertEquals(
            listOf("/profiles/abc123/security", "/profiles/abc123/security"),
            fake.requests.map { it.path },
        )
        assertEquals("""{"aiThreatDetection":false}""", fake.requests.last().body)
    }
    @Test fun emptyOrOversizedSettingsPatchesNeverReachTransport() = runBlocking {
        val fake = Fake()
        val api = NextDnsApi(fake)
        for (values in listOf(buildJsonObject {}, buildJsonObject { put("value", "x".repeat(65_537)) })) {
            try {
                api.patchSettings("abc123", key, NextDnsSettingsPage.GENERAL, values)
                fail("Must fail")
            } catch (failure: NextDnsException) {
                assertEquals(NextDnsErrorKind.INVALID, failure.kind)
            }
        }
        assertTrue(fake.requests.isEmpty())
    }
    @Test fun settingsPatchesPreserveNestedApiShape() {
        val values = Json.parseToJsonElement(
            """{"logs":{"enabled":true,"drop":{"ip":true}},"performance":{"ecs":false}}""",
        ).jsonObject

        assertEquals(
            listOf(
                NextDnsBooleanSetting(listOf("logs", "enabled"), true),
                NextDnsBooleanSetting(listOf("logs", "drop", "ip"), true),
                NextDnsBooleanSetting(listOf("performance", "ecs"), false),
            ),
            NextDnsSettingsValues.booleans(values),
        )
        assertEquals(
            """{"logs":{"drop":{"ip":false}}}""",
            NextDnsSettingsValues.patch(listOf("logs", "drop", "ip"), false).toString(),
        )
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
    @Test fun analyticsUsesAllowListedMetricPeriodAndDeviceFilters() = runBlocking {
        val fake = Fake(
            NextDnsResponse(200, """{"data":[{"queries":[100,200,300]}]}"""),
            NextDnsResponse(200, """{"data":[{"domain":"ads.example.org","queries":12}]}"""),
        )
        val api = NextDnsApi(fake)
        val traffic = api.analytics(
            "abc123",
            key,
            NextDnsAnalyticsMetric.TRAFFIC,
            NextDnsAnalyticsPeriod.DAYS_7,
        )
        val blocked = api.analytics(
            "abc123",
            key,
            NextDnsAnalyticsMetric.BLOCKED_DOMAINS,
            NextDnsAnalyticsPeriod.HOURS_24,
            "device/id +1",
        )
        assertEquals(600L, traffic.totalQueries)
        assertEquals(listOf(100L, 200L, 300L), traffic.series)
        assertEquals("ads.example.org", blocked.rows.single().label)
        assertEquals("12", blocked.rows.single().value)
        assertEquals(
            "/profiles/abc123/analytics/status;series?from=-7d&alignment=start&timezone=" +
                java.net.URLEncoder.encode(java.time.ZoneId.systemDefault().id, "UTF-8"),
            fake.requests[0].path,
        )
        assertTrue(fake.requests[1].path.startsWith("/profiles/abc123/analytics/domains?from=-24h&alignment=start&timezone="))
        assertTrue(fake.requests[1].path.contains("&device=device%2Fid+%2B1&status=blocked&limit=6"))
        assertTrue(fake.requests.all { it.method == "GET" && it.body == null && it.apiKey == key })
    }
    @Test fun analyticsRejectsInvalidDeviceAndMalformedOrOversizedData() = runBlocking {
        val fake = Fake(
            NextDnsResponse(200, """{"data":{"unexpected":true}}"""),
            NextDnsResponse(200, """{"data":[{"queries":[1,2]}]}"""),
        )
        val api = NextDnsApi(fake)
        try {
            api.analytics("abc123", key, NextDnsAnalyticsMetric.TRAFFIC, NextDnsAnalyticsPeriod.HOURS_24, "bad\nid")
            fail("Must fail")
        } catch (failure: NextDnsException) {
            assertEquals(NextDnsErrorKind.INVALID, failure.kind)
        }
        assertTrue(fake.requests.isEmpty())
        try {
            api.analytics("abc123", key, NextDnsAnalyticsMetric.TRAFFIC, NextDnsAnalyticsPeriod.HOURS_24)
            fail("Must reject unsupported object shape")
        } catch (failure: NextDnsException) {
            assertEquals(NextDnsErrorKind.API, failure.kind)
        }
        val snapshot = api.analytics("abc123", key, NextDnsAnalyticsMetric.TRAFFIC, NextDnsAnalyticsPeriod.HOURS_24)
        assertEquals(3L, snapshot.totalQueries)
        assertEquals(2, fake.requests.size)
    }
    @Test fun analyticsDeviceResponseIsBoundedAndRequiresStableIdentifiers() = runBlocking {
        val fake = Fake(
            NextDnsResponse(200, """{"data":[{"id":"phone-1","name":"Phone"}]}"""),
            NextDnsResponse(200, """{"data":[{"name":"No ID"}]}"""),
        )
        val api = NextDnsApi(fake)
        assertEquals(listOf(NextDnsAnalyticsDevice("phone-1", "Phone")), api.analyticsDevices("abc123", key))
        try {
            api.analyticsDevices("abc123", key)
            fail("Must reject missing device ID")
        } catch (failure: NextDnsException) {
            assertEquals(NextDnsErrorKind.API, failure.kind)
        }
        assertEquals(
            listOf(
                "/profiles/abc123/analytics/devices?from=-3M&limit=200",
                "/profiles/abc123/analytics/devices?from=-3M&limit=200",
            ),
            fake.requests.map { it.path },
        )
    }
    @Test fun analyticsParsesPercentagesAndNamedDestinationsWithoutLosingQueryTotals() {
        val encryption = NextDnsAnalyticsParser.snapshot(
            NextDnsAnalyticsMetric.ENCRYPTION,
            Json.parseToJsonElement(
                """{"data":[{"encrypted":true,"queries":75},{"encrypted":false,"queries":25}]}""",
            ).jsonObject,
            null,
        )
        assertEquals(100L, encryption.totalQueries)
        assertEquals(listOf("encrypted", "not encrypted"), encryption.rows.map { it.label })
        assertEquals(listOf("75 (75.0%)", "25 (25.0%)"), encryption.rows.map { it.value })

        val destinations = NextDnsAnalyticsParser.snapshot(
            NextDnsAnalyticsMetric.DESTINATIONS,
            Json.parseToJsonElement(
                """{"data":[{"company":"google","queries":90},{"company":"others","queries":10}]}""",
            ).jsonObject,
            null,
        )
        assertEquals(100L, destinations.totalQueries)
        assertEquals(listOf("google", "others"), destinations.rows.map { it.label })
        assertEquals(listOf("90.0%", "10.0%"), destinations.rows.map { it.value })
    }
    @Test fun analyticsRejectsSeriesWithExcessivePointCount() {
        val values = (0..500).joinToString(",")
        val data = Json.parseToJsonElement("""{"data":[{"status":"blocked","queries":[$values]}]}""").jsonObject
        try {
            NextDnsAnalyticsParser.snapshot(NextDnsAnalyticsMetric.TRAFFIC, data, null)
            fail("Must reject oversized series")
        } catch (failure: NextDnsException) {
            assertEquals(NextDnsErrorKind.API, failure.kind)
        }
    }
    @Test fun rewriteApiValidatesNamesAndTargetsAndUsesDocumentedMutations() = runBlocking {
        val fake = Fake(
            NextDnsResponse(200, """{"data":[{"id":"0123abcd","name":"home.example.org","type":"A","content":"192.0.2.10"}]}"""),
            NextDnsResponse(201, """{"data":{"id":"abcd1234","name":"service.example.org","type":"CNAME","content":"target.example.net"}}"""),
            NextDnsResponse(204, ""),
        )
        val api = NextDnsApi(fake)
        val existing = api.rewrites("abc123", key)
        assertEquals(listOf(NextDnsRewrite("0123abcd", "home.example.org", "A", "192.0.2.10")), existing)
        val created = api.createRewrite("abc123", key, " Service.Example.org. ", "Target.Example.net.")
        assertEquals(NextDnsRewrite("abcd1234", "service.example.org", "CNAME", "target.example.net"), created)
        api.deleteRewrite("abc123", key, created.id)

        assertEquals(
            listOf(
                "GET" to "/profiles/abc123/rewrites",
                "POST" to "/profiles/abc123/rewrites",
                "DELETE" to "/profiles/abc123/rewrites/abcd1234",
            ),
            fake.requests.map { it.method to it.path },
        )
        assertEquals(
            """{"name":"service.example.org","content":"target.example.net"}""",
            fake.requests[1].body,
        )
        assertNull(fake.requests[0].body)
        assertNull(fake.requests[2].body)
    }
    @Test fun rewriteValidationRejectsUrlsWildcardsAndInvalidAddressesBeforeNetwork() = runBlocking {
        val fake = Fake()
        val api = NextDnsApi(fake)
        for (input in listOf(
            suspend { api.createRewrite("abc123", key, "192.0.2.10", "192.0.2.11"); Unit },
            suspend { api.createRewrite("abc123", key, "*.example.org", "192.0.2.11"); Unit },
            suspend { api.createRewrite("abc123", key, "valid.example.org", "https://example.org"); Unit },
            suspend { api.deleteRewrite("abc123", key, "../secret"); },
        )) {
            try {
                input()
                fail("Must fail before transport")
            } catch (failure: NextDnsException) {
                assertEquals(NextDnsErrorKind.INVALID, failure.kind)
            }
        }
        assertEquals("2001:db8::1", NextDnsRewriteValidation.content("2001:db8::1"))
        assertTrue(fake.requests.isEmpty())
    }
    @Test fun analyticsPaginationUsesValidatedCursorAndReturnsNextCursor() = runBlocking {
        val fake = Fake(
            NextDnsResponse(
                200,
                """{"data":[{"domain":"first.example","queries":10}],"meta":{"pagination":{"cursor":"next+/="}}}""",
            ),
            NextDnsResponse(
                200,
                """{"data":[{"domain":"second.example","queries":9}],"meta":{"pagination":{"cursor":null}}}""",
            ),
        )
        val api = NextDnsApi(fake)
        val first = api.analytics(
            "abc123",
            key,
            NextDnsAnalyticsMetric.RESOLVED_DOMAINS,
            NextDnsAnalyticsPeriod.DAYS_30,
        )
        val second = api.analytics(
            "abc123",
            key,
            NextDnsAnalyticsMetric.RESOLVED_DOMAINS,
            NextDnsAnalyticsPeriod.DAYS_30,
            cursor = first.nextCursor,
        )
        assertEquals("next+/=", first.nextCursor)
        assertEquals("first.example", first.rows.single().label)
        assertEquals("second.example", second.rows.single().label)
        assertTrue(fake.requests[0].path.endsWith("&status=default%2Callowed&limit=6"))
        assertTrue(fake.requests[1].path.contains("&status=default%2Callowed&limit=100&cursor=next%2B%2F%3D"))
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
