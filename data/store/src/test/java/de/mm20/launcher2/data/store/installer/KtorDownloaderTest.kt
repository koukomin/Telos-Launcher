package de.mm20.launcher2.data.store.installer

import de.mm20.launcher2.store.installer.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList

class KtorDownloaderTest {
    private class Fixture(val size: Long, val failFirst: Boolean = false, val ignoreRange: Boolean = false, val slow: Boolean = false) : AutoCloseable {
        val dir = Files.createTempDirectory("telos-apk-test").toFile()
        val requests = CopyOnWriteArrayList<Long>()
        val breakConnection = CompletableDeferred<Unit>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val client = HttpClient(MockEngine { request ->
            val requested = request.headers["Range"]?.removePrefix("bytes=")?.removeSuffix("-")?.toLong() ?: 0
            requests += requested
            val start = if (ignoreRange) 0 else requested
            val first = requests.size == 1
            val body = ByteChannel()
            scope.launch {
                try {
                    val buffer = ByteArray(64 * 1024) { 42 }
                    var position = start
                    while (position < size) {
                        if (slow || failFirst && first) delay(120)
                        if (failFirst && first && position >= 384 * 1024) {
                            breakConnection.await()
                            throw IOException("Connection lost")
                        }
                        val count = minOf(buffer.size.toLong(), size - position).toInt()
                        body.writeFully(buffer, 0, count)
                        body.flush()
                        position += count
                    }
                    body.close()
                } catch (e: Exception) { body.cancel(e) }
            }
            respond(body, if (start > 0) HttpStatusCode.PartialContent else HttpStatusCode.OK,
                headersOf(*listOf(
                    "Content-Length" to listOf((size - start).toString()),
                    "ETag" to listOf("\"test-v1\""),
                ).let { headers -> if (start > 0) headers + ("Content-Range" to listOf("bytes $start-${size - 1}/$size")) else headers }.toTypedArray()))
        })
        val downloader = KtorDownloader(dir, client)
        override fun close() { scope.cancel(); client.close(); dir.deleteRecursively() }
    }

    @Test fun streamsAnApkLargerThanTheTestHeap() = runBlocking {
        // The test JVM has a 128 MiB heap. Saving this response as one byte array would fail.
        Fixture(166_283_744).use { f ->
            val result = withTimeout(60_000) { f.downloader.download("https://example.test/app.apk", "large.apk", "large") }
            assertTrue(result is DownloadResult.Success)
            assertEquals(f.size, (result as DownloadResult.Success).file.length())
            assertEquals(StoreDownloadStatus.Complete, f.downloader.progress.value["large"]?.status)
        }
    }

    @Test fun pauseClosesTheTransferAndResumeUsesThePartialFile() = runBlocking {
        Fixture(1024L * 1024, slow = true).use { f ->
            val job = async { f.downloader.download("https://example.test/app.apk", "pause.apk", "pause") }
            withTimeout(10_000) { f.downloader.progress.first { (it["pause"]?.bytes ?: 0) > 0 } }
            f.downloader.pause("pause")
            withTimeout(10_000) { f.downloader.progress.first { it["pause"]?.status == StoreDownloadStatus.Paused } }
            delay(300)
            assertFalse(job.isCompleted)
            f.downloader.resume("pause")
            val result = withTimeout(10_000) { job.await() } as DownloadResult.Success
            assertEquals(f.size, result.file.length())
            assertTrue("Expected a Range request; requests=${f.requests}", f.requests.any { it > 0 })
            assertTrue("Unexpected bytes in resumed APK", result.file.readBytes().all { it == 42.toByte() })
        }
    }

    @Test fun interruptedNetworkAutomaticallyResumesWithoutDuplicatingBytes() = runBlocking {
        Fixture(1024L * 1024, failFirst = true).use { f ->
            val job = async { f.downloader.download("https://example.test/app.apk", "retry.apk", "retry") }
            withTimeout(10_000) { f.downloader.progress.first { (it["retry"]?.bytes ?: 0) > 0 } }
            f.breakConnection.complete(Unit)
            val result = withTimeout(15_000) { job.await() } as DownloadResult.Success
            assertEquals(f.size, result.file.length())
            assertTrue("Expected a Range request; requests=${f.requests}", f.requests.any { it > 0 })
            assertTrue("Unexpected bytes in resumed APK", result.file.readBytes().all { it == 42.toByte() })
        }
    }

    @Test fun aServerWithoutRangeSupportRestartsSafely() = runBlocking {
        Fixture(1024L * 1024, failFirst = true, ignoreRange = true).use { f ->
            val job = async { f.downloader.download("https://example.test/app.apk", "restart.apk", "restart") }
            withTimeout(10_000) { f.downloader.progress.first { (it["restart"]?.bytes ?: 0) > 0 } }
            f.breakConnection.complete(Unit)
            val result = withTimeout(15_000) { job.await() } as DownloadResult.Success
            assertEquals(f.size, result.file.length())
            assertTrue("Expected a Range request; requests=${f.requests}", f.requests.any { it > 0 })
            assertTrue("Unexpected bytes in resumed APK", result.file.readBytes().all { it == 42.toByte() })
        }
    }
}
