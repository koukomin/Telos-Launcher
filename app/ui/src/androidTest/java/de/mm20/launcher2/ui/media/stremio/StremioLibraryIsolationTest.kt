package de.mm20.launcher2.ui.media.stremio

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import de.mm20.launcher2.comms.stremio.StremioLibrary
import de.mm20.launcher2.comms.stremio.WatchProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class StremioLibraryIsolationTest {
    private class ScopeContext(base: Context) : ContextWrapper(base) {
        private val prefix = "stremio-test-${UUID.randomUUID()}"
        val directory = File(base.cacheDir, prefix).apply { mkdirs() }
        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File = directory
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = super.getSharedPreferences("$prefix-$name", mode)
    }

    @Test fun accountSwitchDoesNotUploadOrDisplayAnonymousOrAnotherUsersLibrary() = runBlocking {
        val context = ScopeContext(ApplicationProvider.getApplicationContext())
        try {
            val library = StremioLibrary(context)
            library.setSaved("movie", "anonymous-film", "Anonymous", null, true)
            library.activateAccount("a@example.test", "session-a")
            assertTrue(library.library.value.isEmpty())
            library.setSaved("movie", "account-a-film", "Account A", null, true)
            library.activateAccount("b@example.test", "session-b")
            assertTrue(library.library.value.isEmpty())
            library.setSaved("movie", "account-b-film", "Account B", null, true)
            library.activateAccount("A@example.test", "new-session-a")
            assertEquals(listOf("account-a-film"), library.library.value.map { it.id })
            library.activateAccount(null)
            assertEquals(listOf("anonymous-film"), library.library.value.map { it.id })
        } finally { withContext(Dispatchers.IO) { context.directory.deleteRecursively() } }
    }

    @Test fun stalePlayerCannotWriteAcrossLoginAndEpisodeQueueSurvivesReopening() = runBlocking {
        val context = ScopeContext(ApplicationProvider.getApplicationContext())
        try {
            val library = StremioLibrary(context)
            library.activateAccount("a@example.test", "session-a")
            val p = WatchProgress("series", "tt1234567", "Show", null, "tt1234567:1:1", "One", 1000, null,
                positionMs = 9500, durationMs = 10000, timeWatchedMs = 9000, completed = true,
                videoIds = listOf("tt1234567:1:1", "tt1234567:1:2"))
            assertTrue(library.markWatched(p, "session-a"))
            assertTrue(library.markWatched(p.copy(videoId = "tt1234567:1:2", watchedAt = 2000), "session-a"))
            assertEquals(2, library.pendingProgress.value.size)
            library.activateAccount("b@example.test", "session-b")
            assertFalse(library.markWatched(p.copy(watchedAt = 3000), "session-a"))
            assertTrue(library.continueWatching.value.isEmpty())
            assertTrue(library.pendingProgress.value.isEmpty())
            val reopened = StremioLibrary(context)
            reopened.activateAccount("a@example.test", "session-a2")
            assertEquals(2, reopened.pendingProgress.value.size)
            assertFalse(reopened.markWatched(p.copy(watchedAt = 4000), "session-a"))
            // Acknowledging the older update must not remove a newer coalesced position.
            assertTrue(reopened.markWatched(p.copy(watchedAt = 5000), "session-a2"))
            reopened.acknowledge(p)
            assertEquals(2, reopened.pendingProgress.value.size)
        } finally { withContext(Dispatchers.IO) { context.directory.deleteRecursively() } }
    }

    @Test fun failedAtomicWriteKeepsThePreviousVisibleState() = runBlocking {
        val context = ScopeContext(ApplicationProvider.getApplicationContext())
        try {
            val library = StremioLibrary(context)
            library.setSaved("movie", "kept", "Kept", null, true)
            withContext(Dispatchers.IO) { check(context.directory.setWritable(false, false)) }
            val failed = runCatching { library.setSaved("movie", "failed", "Failed", null, true) }.isFailure
            assertTrue(failed)
            assertEquals(listOf("kept"), library.library.value.map { it.id })
        } finally { withContext(Dispatchers.IO) { context.directory.setWritable(true, true); context.directory.deleteRecursively() } }
    }
}
