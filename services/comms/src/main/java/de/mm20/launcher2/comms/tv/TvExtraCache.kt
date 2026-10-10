package de.mm20.launcher2.comms.tv

import android.content.Context
import de.mm20.launcher2.comms.media.video.Http
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection

/**
 * Disk cache of the optional extra sources (filesDir/tv/extra). One file per key, conditional GET
 * (ETag / Last-Modified), https only, redirects checked by [Http], size capped while streaming.
 * Failures are never reported anywhere: [fetch] just says FAILED and the caller carries on without
 * that source. A cached file stays usable for [MAX_AGE_MS] after the last successful check.
 * Blocking: call from an IO dispatcher.
 */
class TvExtraCache(context: Context) {
    enum class Outcome { UPDATED, NOT_MODIFIED, FAILED }

    private class Meta(var etag: String = "", var lastModified: String = "", var okAt: Long = 0L)

    private val dir = File(context.applicationContext.filesDir, "tv/extra")
    private val meta = HashMap<String, Meta>()
    private var loaded = false

    @Synchronized
    private fun ensure() {
        if (loaded) return
        loaded = true
        runCatching {
            val j = JSONObject(File(dir, META).readText())
            val keys = j.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val o = j.optJSONObject(k) ?: continue
                meta[k] = Meta(o.optString("etag"), o.optString("lm"), o.optLong("ok"))
            }
        }
    }

    private fun save() {
        runCatching {
            dir.mkdirs()
            val j = JSONObject()
            for ((k, m) in meta) j.put(k, JSONObject().put("etag", m.etag).put("lm", m.lastModified).put("ok", m.okAt))
            val tmp = File(dir, "$META.tmp")
            tmp.writeText(j.toString())
            tmp.renameTo(File(dir, META))
        }
    }

    /** Epoch millis of the last successful check (200 or 304) of [key], 0 = never */
    @Synchronized
    fun okAt(key: String): Long { ensure(); return meta[key]?.okAt ?: 0L }

    /** The cached file when it exists and was confirmed within [MAX_AGE_MS]; an older one is deleted */
    @Synchronized
    fun usable(key: String, now: Long = System.currentTimeMillis()): File? {
        ensure()
        val f = File(dir, key)
        if (!f.isFile) return null
        val ok = meta[key]?.okAt ?: 0L
        if (now - ok in 0..MAX_AGE_MS) return f
        f.delete()
        return null
    }

    @Synchronized
    fun fetch(key: String, url: String, maxBytes: Long, accept: String): Outcome {
        ensure()
        val target = File(dir, key)
        val m = meta.getOrPut(key) { Meta() }
        var c: HttpURLConnection? = null
        try {
            if (!url.startsWith("https://")) return Outcome.FAILED
            val first = Http.open(url, USER_AGENT, accept)
            first.connectTimeout = 8000
            first.readTimeout = 20000
            if (target.isFile) {
                if (m.etag.isNotEmpty()) first.setRequestProperty("If-None-Match", m.etag)
                if (m.lastModified.isNotEmpty()) first.setRequestProperty("If-Modified-Since", m.lastModified)
            }
            c = try { Http.resolve(first) } catch (e: Exception) { first.disconnect(); throw e }
            val code = c.responseCode
            val now = System.currentTimeMillis()
            if (code == 304 && target.isFile) {
                m.okAt = now
                save()
                return Outcome.NOT_MODIFIED
            }
            if (code !in 200..299) return Outcome.FAILED
            if (c.url.protocol.lowercase() != "https") return Outcome.FAILED
            val declared = c.contentLengthLong
            if (declared > maxBytes) return Outcome.FAILED
            dir.mkdirs()
            val tmp = File(dir, "$key.tmp")
            var total = 0L
            try {
                c.inputStream.use { input ->
                    tmp.outputStream().use { out ->
                        val buf = ByteArray(16 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            total += n
                            if (total > maxBytes) throw IOException("too big")
                            out.write(buf, 0, n)
                        }
                    }
                }
            } catch (e: Exception) {
                tmp.delete()
                throw e
            }
            if (total == 0L) { tmp.delete(); return Outcome.FAILED }
            if (!tmp.renameTo(target)) {
                target.delete()
                if (!tmp.renameTo(target)) { tmp.delete(); return Outcome.FAILED }
            }
            m.etag = c.getHeaderField("ETag").orEmpty()
            m.lastModified = c.getHeaderField("Last-Modified").orEmpty()
            m.okAt = now
            save()
            return Outcome.UPDATED
        } catch (e: Exception) {
            return Outcome.FAILED
        } finally {
            c?.disconnect()
        }
    }

    companion object {
        const val USER_AGENT = "Telos TV"
        /** A cached file is used for at most this long after its last successful check */
        const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
        private const val META = "meta.json"
    }
}
