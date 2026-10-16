package de.mm20.launcher2.network.web3

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test

class Web3ResolverTest {
    @Test fun walletOutageDoesNotHideWebsite() = runBlocking {
        val resolver = Web3Resolver { url ->
            if (url.contains("ensideas")) error("offline")
            Json.parseToJsonElement("""{"Answer":[{"data":"dnslink=/ipfs/bafyexample"}]}""").jsonObject
        }
        val result = resolver.resolve("ALICE.eth", "")
        assertEquals("https://alice.eth.limo/", result.website)
        assertTrue(result.wallets.isEmpty())
        assertTrue(result.partialFailure)
    }
    @Test fun websiteOutageDoesNotHideWallet() = runBlocking {
        val resolver = Web3Resolver { url ->
            if (url.contains("eth.limo")) error("offline")
            Json.parseToJsonElement("""{"address":"0x1234567890123456789012345678901234567890"}""").jsonObject
        }
        val result = resolver.resolve("alice.eth", "")
        assertEquals("0x1234567890123456789012345678901234567890", result.wallets["ETH"])
        assertNull(result.website)
        assertTrue(result.partialFailure)
    }
    @Test fun unstoppableUsesOnlyConfiguredProxyAndDoesNotOpenUnsafeRedirect() = runBlocking {
        val urls = mutableListOf<String>()
        val resolver = Web3Resolver { url ->
            urls += url
            Json.parseToJsonElement("""{"records":{"crypto.ETH.address":"0x123","browser.redirect_url":"javascript:evil()"}}""").jsonObject
        }
        val result = resolver.resolve("alice.crypto", "https://proxy.example/resolve/domains/")
        assertEquals(listOf("https://proxy.example/resolve/domains/alice.crypto"), urls)
        assertNull(result.website)
        assertEquals("0x123", result.wallets["ETH"])
    }
    @Test fun invalidNamesNeverReachProvider() = runBlocking {
        var called = false
        val resolver = Web3Resolver { called = true; error("must not call") }
        try { resolver.resolve("alice.eth/../../secrets", "") } catch (_: IllegalArgumentException) { }
        assertFalse(called)
    }
}
