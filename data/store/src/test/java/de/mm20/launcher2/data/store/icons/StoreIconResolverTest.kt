package de.mm20.launcher2.data.store.icons

import de.mm20.launcher2.store.model.AppSource
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class StoreIconResolverTest {
    @Test fun repositoryArtworkIsCachedAndNeverReceivesCredentials() = runBlocking {
        var requests = 0
        val client = HttpClient(MockEngine { request ->
            requests++
            assertNull(request.headers["Authorization"])
            respond("<img alt='App icon' src='art/icon.png'>", HttpStatusCode.OK)
        })
        try {
            val resolver = StoreIconResolverImpl(client)
            val source = AppSource.GitHub("owner", "app")
            val first = resolver.candidates(source, "app.test")
            assertEquals("https://raw.githubusercontent.com/owner/app/HEAD/art/icon.png", first.first())
            assertEquals(first, resolver.candidates(source, "app.test"))
            assertEquals(1, requests)
        } finally { client.close() }
    }
    @Test fun fdroidUsesPublishedHashedIconRatherThanAssumingTheApkName() = runBlocking {
        val client = HttpClient(MockEngine {
            respond("<img class='package-icon' src='/repo/icons-640/app.hash.png'>", HttpStatusCode.OK)
        })
        try { assertEquals("https://f-droid.org/repo/icons-640/app.hash.png",
            StoreIconResolverImpl(client).candidates(AppSource.FDroid("app.test"), "app.test").first()) }
        finally { client.close() }
    }
    @Test fun missingReadmeKeepsConventionalArtworkCandidates() = runBlocking {
        val client = HttpClient(MockEngine { respond("", HttpStatusCode.NotFound) })
        try { assertTrue(StoreIconResolverImpl(client).candidates(AppSource.GitHub("owner", "app"), "app.test")
            .any { it.endsWith("fastlane/metadata/android/en-US/images/icon.png") }) }
        finally { client.close() }
    }
}
