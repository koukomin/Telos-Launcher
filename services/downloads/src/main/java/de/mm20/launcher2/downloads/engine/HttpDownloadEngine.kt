package de.mm20.launcher2.downloads.engine

import de.mm20.launcher2.downloads.DownloadException
import de.mm20.launcher2.downloads.DownloadSettingsValues
import de.mm20.launcher2.downloads.DownloadState
import de.mm20.launcher2.downloads.DownloadTask
import de.mm20.launcher2.downloads.DownloadType
import de.mm20.launcher2.downloads.ErrorKind
import de.mm20.launcher2.downloads.ProxyType
import de.mm20.launcher2.downloads.SegmentState
import de.mm20.launcher2.downloads.logic.Checksums
import de.mm20.launcher2.downloads.logic.FileNames
import de.mm20.launcher2.downloads.logic.HttpRanges
import de.mm20.launcher2.downloads.logic.MimeTypes
import de.mm20.launcher2.downloads.logic.RetryPolicy
import de.mm20.launcher2.downloads.logic.SegmentPlanner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.ConnectionPool
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * HTTP and HTTPS downloads on OkHttp: several connections that each fetch a byte range (Range
 * requests), idle connections take over half of the biggest remaining range, resume from the saved
 * ranges after a restart or a lost network, and a check that the file on the server is still the same
 * (ETag, Last-Modified, size; If-Range on every ranged request).
 *
 * Written independently for Telos; the design ideas (segmented download, resume) are the common ones
 * of download managers, see docs/dev-notes/telos-downloads-design.md.
 */
class HttpDownloadEngine : DownloadEngine {

    override fun supports(task: DownloadTask): Boolean =
        task.type == DownloadType.Http && (task.url.startsWith("http://", true) || task.url.startsWith("https://", true))

    override suspend fun execute(task: DownloadTask, session: EngineSession) = withContext(Dispatchers.IO) {
        var restarted = false
        while (true) {
            try {
                run(session, forceRestart = restarted)
                return@withContext
            } catch (e: ResourceChanged) {
                if (restarted) throw DownloadException(ErrorKind.Validation, "The file on the server keeps changing", false)
                restarted = true
            }
        }
    }

    /** The server answered a ranged request with something else than the file we started: start again */
    private class ResourceChanged : Exception()

    private class Probe(
        val finalUrl: HttpUrl,
        val total: Long,
        val ranges: Boolean,
        val etag: String?,
        val lastModified: String?,
        val mime: String?,
        val contentDisposition: String?,
    )

    private class Seg(val index: Int, val start: Long, end: Long, downloaded: Long) {
        @Volatile
        var end: Long = end
        val downloaded = AtomicLong(downloaded)

        @Volatile
        var claimed = false
        val isDone: Boolean get() = end >= 0 && downloaded.get() >= end - start + 1
        fun state() = SegmentState(index, start, end, downloaded.get())
    }

    /** The ranges of the file and the hand-out of work to connections, including splitting a range for an idle connection */
    private class Plan(initial: List<SegmentState>) {
        private val segs = initial.map { Seg(it.index, it.start, it.end, it.downloaded) }.toMutableList()
        private var nextIndex = (initial.maxOfOrNull { it.index } ?: -1) + 1

        @Synchronized
        fun snapshot(): List<SegmentState> = segs.map { it.state() }

        @Synchronized
        fun claim(): Seg? {
            segs.firstOrNull { !it.claimed && !it.isDone }?.let { it.claimed = true; return it }
            val victim = segs.filter { it.claimed && !it.isDone && it.end >= 0 }
                .maxByOrNull { it.end - (it.start + it.downloaded.get()) } ?: return null
            val cut = SegmentPlanner.splitPoint(victim.start + victim.downloaded.get(), victim.end) ?: return null
            val fresh = Seg(nextIndex++, cut, victim.end, 0).also { it.claimed = true }
            victim.end = cut - 1
            segs.add(fresh)
            return fresh
        }
    }

    private class Cancellable {
        @Volatile
        var call: Call? = null
    }

    /** Runs blocking network code; when the coroutine is cancelled the current call is cancelled so that it stops reading */
    private suspend fun <T> cancellable(body: suspend (Cancellable) -> T): T = coroutineScope {
        val c = Cancellable()
        val watcher = launch {
            try {
                awaitCancellation()
            } finally {
                c.call?.cancel()
            }
        }
        try {
            body(c)
        } finally {
            watcher.cancel()
        }
    }

    private class Ctx(
        val client: OkHttpClient,
        val task: DownloadTask,
        val settings: DownloadSettingsValues,
        val url: HttpUrl,
        val ranges: Boolean,
        val etag: String?,
        val lastModified: String?,
        val total: Long,
        val sink: de.mm20.launcher2.downloads.DownloadSink,
        val plan: Plan,
        val taskLimiter: RateLimiter,
        val globalLimiter: RateLimiter,
        val singleConnection: Boolean,
    )

    private fun buildClient(s: DownloadSettingsValues): OkHttpClient {
        val b = OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            // HTTP/2 would carry all connections of a download over one TCP connection
            .protocols(listOf(Protocol.HTTP_1_1))
            .connectionPool(ConnectionPool(32, 1, TimeUnit.MINUTES))
        if (s.proxyType != ProxyType.None && s.proxyHost.isNotBlank() && s.proxyPort in 1..65535) {
            val type = if (s.proxyType == ProxyType.Socks) Proxy.Type.SOCKS else Proxy.Type.HTTP
            b.proxy(Proxy(type, InetSocketAddress.createUnresolved(s.proxyHost.trim(), s.proxyPort)))
        }
        return b.build()
    }

    /** Request headers; [sameOrigin] false (after a redirect to another host) drops cookies and credentials */
    private fun headersFor(task: DownloadTask, s: DownloadSettingsValues, sameOrigin: Boolean): Headers {
        val b = Headers.Builder()
        b.set("User-Agent", task.userAgent?.takeIf { it.isNotBlank() } ?: s.effectiveUserAgent)
        b.set("Accept-Encoding", "identity")
        b.set("Accept", "*/*")
        if (!task.referer.isNullOrBlank()) b.set("Referer", task.referer)
        if (sameOrigin && !task.cookies.isNullOrBlank()) b.set("Cookie", task.cookies)
        for ((k, v) in task.headers) {
            val name = k.trim()
            if (name.isEmpty() || name.equals("Range", true) || name.equals("Accept-Encoding", true) || name.contains(Regex("[\\s:]"))) continue
            if (!sameOrigin && (name.equals("Authorization", true) || name.equals("Cookie", true))) continue
            if (v.any { it == '\r' || it == '\n' }) continue
            b.set(name, v)
        }
        return b.build()
    }

    /** Follows up to 10 redirects by hand so that credentials do not leave the host. The caller closes the response. */
    private fun fetch(
        c: Cancellable, client: OkHttpClient, task: DownloadTask, s: DownloadSettingsValues,
        startUrl: HttpUrl, range: String?, ifRange: String?,
    ): Pair<Response, HttpUrl> {
        var url = startUrl
        repeat(11) {
            val sameOrigin = url.host == startUrl.host
            val rb = Request.Builder().url(url).headers(headersFor(task, s, sameOrigin))
            if (range != null) {
                rb.header("Range", range)
                if (ifRange != null) rb.header("If-Range", ifRange)
            }
            val call = client.newCall(rb.build())
            c.call = call
            val resp = call.execute()
            if (resp.code in intArrayOf(301, 302, 303, 307, 308)) {
                val loc = resp.header("Location")
                resp.close()
                val next = loc?.let { url.resolve(it) }
                    ?: throw DownloadException(ErrorKind.Http, "Bad redirect", false)
                url = next
            } else {
                return resp to url
            }
        }
        throw DownloadException(ErrorKind.Http, "Too many redirects", false)
    }

    private fun httpError(code: Int, message: String?): DownloadException =
        DownloadException(ErrorKind.Http, "HTTP $code${if (message.isNullOrBlank()) "" else " $message"}", RetryPolicy.isRetryableStatus(code))

    private suspend fun probe(client: OkHttpClient, task: DownloadTask, s: DownloadSettingsValues, address: String): Probe {
        val start = address.toHttpUrlOrNull()
            ?: throw DownloadException(ErrorKind.Validation, "Not a valid address: $address", false)
        return cancellable { c ->
            try {
                val (resp, finalUrl) = fetch(c, client, task, s, start, "bytes=0-0", null)
                resp.use { r ->
                    val etag = r.header("ETag")
                    val modified = r.header("Last-Modified")
                    val mime = r.header("Content-Type")
                    val cd = r.header("Content-Disposition")
                    when (r.code) {
                        206 -> {
                            val total = HttpRanges.totalFromContentRange(r.header("Content-Range")) ?: -1
                            Probe(finalUrl, total, total > 0, etag, modified, mime, cd)
                        }
                        200 -> {
                            val len = r.header("Content-Length")?.toLongOrNull() ?: -1
                            Probe(finalUrl, len, false, etag, modified, mime, cd)
                        }
                        416 -> Probe(finalUrl, 0, false, etag, modified, mime, cd)
                        else -> throw httpError(r.code, r.message)
                    }
                }
            } catch (e: IOException) {
                currentCoroutineContext().ensureActive()
                throw DownloadException(ErrorKind.Network, e.message ?: "Network error", true, e)
            }
        }
    }

    private suspend fun run(session: EngineSession, forceRestart: Boolean) {
        val settings = session.settings
        val client = buildClient(settings)
        var t = session.update { it.copy(state = DownloadState.Connecting, error = null, errorKind = ErrorKind.None, speedBps = 0) }

        var probe: Probe? = null
        var lastError: DownloadException? = null
        for (address in listOf(t.url) + t.mirrors) {
            try {
                probe = probe(client, t, settings, address)
                break
            } catch (e: DownloadException) {
                lastError = e
            }
        }
        val p = probe ?: throw lastError!!

        val hadProgress = t.segments.any { it.downloaded > 0 }
        var restart = forceRestart
        if (hadProgress && (!p.ranges || HttpRanges.changed(t.totalBytes, p.total, t.etag, p.etag, t.lastModified, p.lastModified))) restart = true
        if (hadProgress && !restart) {
            val uri = t.fileUri
            val written = t.segments.filter { it.downloaded > 0 }.maxOf { it.start + it.downloaded }
            if (uri == null || session.files.length(uri) < written) restart = true
        }
        if (!SegmentPlanner.covers(t.segments, p.total) && t.segments.isNotEmpty()) restart = true

        val name = t.name.ifBlank { FileNames.resolve(p.contentDisposition, p.finalUrl.toString(), p.mime) }
        val mime = p.mime?.substringBefore(';')?.trim()?.takeIf { it.isNotEmpty() && it != "application/octet-stream" }
            ?: MimeTypes.forName(name)
        val category = t.category ?: MimeTypes.categoryOf(name, mime)

        var fileUri = t.fileUri
        var finalName = name
        if (fileUri == null || !session.files.exists(fileUri)) {
            if (fileUri != null) restart = true
            val folder = t.treeUri ?: settings.defaultFolder.ifBlank { null }
            val created = session.files.create(folder, name, mime)
            fileUri = created.uri
            finalName = created.name
        }

        val connections = if (p.ranges) SegmentPlanner.clampConnections(if (t.connections > 0) t.connections else settings.connections) else 1
        val segments: List<SegmentState> = if (restart || t.segments.isEmpty()) SegmentPlanner.plan(p.total, if (p.ranges) connections else 1) else t.segments
        t = session.update {
            it.copy(
                name = finalName, category = category, totalBytes = p.total, etag = p.etag, lastModified = p.lastModified,
                acceptRanges = p.ranges, resolvedUrl = p.finalUrl.toString(), mimeType = mime, fileUri = fileUri,
                segments = segments, downloadedBytes = SegmentPlanner.downloadedTotal(segments),
                state = DownloadState.Downloading,
            )
        }

        if (p.total == 0L) {
            session.files.finish(fileUri)
            session.update { it.copy(segments = emptyList(), downloadedBytes = 0) }
            return
        }

        val sink = session.files.openSink(fileUri)
        val plan = Plan(segments)
        try {
            if (restart) sink.truncate(0)
            val taskLimiter = RateLimiter().also { it.bytesPerSecond = t.speedLimitBps }
            val ctx = Ctx(
                client, t, settings, p.finalUrl, p.ranges, p.etag, p.lastModified, p.total, sink, plan,
                taskLimiter, session.globalLimiter, singleConnection = segments.size == 1,
            )
            download(ctx, session, connections)
        } finally {
            withContext(NonCancellable) {
                runCatching { sink.sync() }
                val snap = plan.snapshot()
                val done = SegmentPlanner.downloadedTotal(snap)
                session.progress(done, 0, snap)
                session.update { it.copy(segments = snap, downloadedBytes = done, speedBps = 0) }
                runCatching { sink.close() }
            }
        }

        // an open ended download (no size) is as big as what we got
        var final = session.update { it }
        if (final.totalBytes <= 0) {
            val got = SegmentPlanner.downloadedTotal(final.segments)
            final = session.update { it.copy(totalBytes = got, downloadedBytes = got) }
        }

        verifyChecksum(final, session)
        session.files.finish(fileUri)
        session.update { it.copy(downloadedBytes = it.totalBytes, speedBps = 0) }
    }

    private suspend fun verifyChecksum(task: DownloadTask, session: EngineSession) {
        val expected = Checksums.parse(task.checksum) ?: return
        val uri = task.fileUri ?: return
        session.update { it.copy(state = DownloadState.Verifying) }
        val actual = withContext(Dispatchers.IO) {
            val input = session.files.openInput(uri)
                ?: throw DownloadException(ErrorKind.Storage, "Can not read the file to verify it", false)
            input.use { Checksums.compute(expected.algorithm, it) }
        }
        if (actual != expected.hex) {
            throw DownloadException(ErrorKind.Checksum, "Checksum differs (${expected.algorithm}: expected ${expected.hex}, got $actual)", false)
        }
    }

    private suspend fun download(ctx: Ctx, session: EngineSession, connections: Int) = coroutineScope {
        val reporter = launch {
            var lastBytes = SegmentPlanner.downloadedTotal(ctx.plan.snapshot())
            var lastTime = System.nanoTime()
            var speed = 0.0
            var lastPersist = lastTime
            while (true) {
                delay(500)
                val snap = ctx.plan.snapshot()
                val done = SegmentPlanner.downloadedTotal(snap)
                val now = System.nanoTime()
                val inst = (done - lastBytes) * 1e9 / (now - lastTime).coerceAtLeast(1)
                speed = if (speed == 0.0) inst else speed * 0.7 + inst * 0.3
                lastBytes = done
                lastTime = now
                session.progress(done, speed.toLong(), snap)
                if (now - lastPersist > 3_000_000_000L) {
                    lastPersist = now
                    session.update { it.copy(segments = snap, downloadedBytes = done) }
                }
            }
        }
        val workers = (0 until connections).map {
            launch {
                while (true) {
                    ensureActive()
                    val seg = ctx.plan.claim() ?: break
                    runSegment(ctx, seg)
                }
            }
        }
        workers.joinAll()
        reporter.cancel()
    }

    private suspend fun runSegment(ctx: Ctx, seg: Seg) {
        var failures = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            val pos = seg.start + seg.downloaded.get()
            if (seg.end >= 0 && pos > seg.end) return
            try {
                cancellable { c -> readRange(c, ctx, seg, pos) }
                if (seg.end < 0 || seg.start + seg.downloaded.get() > seg.end) return
                // the connection ended early without an error: try again from where we are
                throw IOException("Connection closed early")
            } catch (e: CancellationException) {
                throw e
            } catch (e: DownloadException) {
                throw e
            } catch (e: ResourceChanged) {
                throw e
            } catch (e: IOException) {
                currentCoroutineContext().ensureActive()
                if (++failures > MAX_SEGMENT_FAILURES) {
                    throw DownloadException(ErrorKind.Network, e.message ?: "Network error", true, e)
                }
                delay(1000L * failures)
            }
        }
    }

    private suspend fun readRange(c: Cancellable, ctx: Ctx, seg: Seg, startPos: Long) {
        val ranged = ctx.ranges && ctx.total > 0
        // without Range support a broken connection can only start over
        if (!ranged && seg.downloaded.get() != 0L) seg.downloaded.set(0)
        val pos = if (ranged) startPos else seg.start
        val end = seg.end
        val range = if (ranged) HttpRanges.rangeHeader(pos, end) else null
        val ifRange = if (ranged) HttpRanges.ifRangeValue(ctx.etag, ctx.lastModified) else null
        val (resp, _) = fetch(c, ctx.client, ctx.task, ctx.settings, ctx.url, range, ifRange)
        resp.use { r ->
            when {
                ranged && r.code == 206 -> {}
                ranged && r.code == 200 && pos == 0L && ctx.singleConnection -> {}
                !ranged && r.code == 200 -> {}
                ranged && (r.code == 200 || r.code == 416) -> throw ResourceChanged()
                else -> throw httpError(r.code, r.message)
            }
            val input = r.body?.byteStream() ?: throw IOException("Empty response")
            val buf = ByteArray(BUFFER_SIZE)
            while (true) {
                currentCoroutineContext().ensureActive()
                val p = seg.start + seg.downloaded.get()
                val curEnd = seg.end
                val want = if (curEnd >= 0) minOf(buf.size.toLong(), curEnd - p + 1).toInt() else buf.size
                if (want <= 0) return
                val n = input.read(buf, 0, want)
                if (n < 0) {
                    if (curEnd >= 0 && p <= seg.end) throw IOException("Connection closed early")
                    return
                }
                ctx.taskLimiter.acquire(n)
                ctx.globalLimiter.acquire(n)
                try {
                    ctx.sink.writeAt(p, buf, 0, n)
                } catch (e: IOException) {
                    throw DownloadException(ErrorKind.Storage, e.message ?: "Can not write the file", false, e)
                }
                seg.downloaded.addAndGet(n.toLong())
            }
        }
    }

    companion object {
        private const val BUFFER_SIZE = 64 * 1024
        private const val MAX_SEGMENT_FAILURES = 3
    }
}
