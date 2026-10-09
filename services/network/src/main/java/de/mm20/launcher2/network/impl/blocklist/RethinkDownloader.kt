/*
 * Download of the Rethink DNS blocklist files.
 *
 * Adapted from RethinkDNS (https://github.com/celzero/rethink-app): LocalBlocklistCoordinator.kt,
 * BlocklistDownloadHelper.kt and Constants.kt (same server, same paths and the same validation).
 * Copyright 2022 RethinkDNS and its authors, licensed under the Apache License, Version 2.0
 * (https://www.apache.org/licenses/LICENSE-2.0).
 */
package de.mm20.launcher2.network.impl.blocklist

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

internal class RethinkDownloader {
    class Check(val update: Boolean, val latest: Long)

    /** Asks the server whether there is a version newer than [timestamp] (`/update/blocklists`). */
    fun check(timestamp: Long): Check {
        val conn = connect("$BASE/update/blocklists?tstamp=$timestamp&vcode=$VCODE")
        val text = try {
            conn.inputStream.reader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
        val o = Json.parseToJsonElement(text).jsonObject
        val version = o["version"]?.jsonPrimitive?.intOrNull ?: 0
        if (version != RESPONSE_VERSION) throw java.io.IOException("unsupported update response")
        return Check(
            update = o["update"]?.jsonPrimitive?.booleanOrNull ?: false,
            latest = o["latest"]?.jsonPrimitive?.longOrNull ?: 0L,
        )
    }

    /** Downloads the four files into [dir] and verifies the trie and rank file against `basicconfig.json`. */
    fun download(dir: File, onProgress: (Float) -> Unit) {
        dir.mkdirs()
        FILES.forEachIndexed { index, (path, name) ->
            val conn = connect("$BASE/$path?vcode=$VCODE&compressed=")
            try {
                val total = conn.contentLengthLong
                File(dir, name).outputStream().use { out ->
                    conn.inputStream.use { input ->
                        val buf = ByteArray(16 * 1024)
                        var done = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            val part = if (total > 0) (done.toFloat() / total).coerceAtMost(1f) else 0f
                            onProgress((index + part) / FILES.size)
                        }
                    }
                }
            } finally {
                conn.disconnect()
            }
        }
        onProgress(1f)
        verify(dir)
    }

    private fun verify(dir: File) {
        val config = Json.parseToJsonElement(File(dir, BASIC_CONFIG).readText()).jsonObject
        val td = config["tdmd5"]?.jsonPrimitive?.contentOrNull
        val rd = config["rdmd5"]?.jsonPrimitive?.contentOrNull
        if (td != null && !md5(File(dir, TRIE)).equals(td, ignoreCase = true)) throw java.io.IOException("checksum mismatch (trie)")
        if (rd != null && !md5(File(dir, RANK)).equals(rd, ignoreCase = true)) throw java.io.IOException("checksum mismatch (rank)")
    }

    private fun md5(file: File): String {
        val digest = MessageDigest.getInstance("MD5")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun connect(url: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 20_000
        conn.readTimeout = 60_000
        conn.setRequestProperty("User-Agent", "TelosNetwork")
        if (conn.responseCode != 200) {
            val code = conn.responseCode
            conn.disconnect()
            throw java.io.IOException("HTTP $code")
        }
        return conn
    }

    companion object {
        /** The server the Rethink DNS app downloads from. */
        const val BASE = "https://dl.rethinkdns.com"

        /** The app version code that is sent to the server (Rethink v057). */
        const val VCODE = 69
        private const val RESPONSE_VERSION = 1

        const val FILETAG = "filetag.json"
        const val BASIC_CONFIG = "basicconfig.json"
        const val RANK = "rd.txt"
        const val TRIE = "td.txt"

        /** Server path to local file name. */
        private val FILES = listOf(
            "blocklists" to FILETAG,
            "basicconfig" to BASIC_CONFIG,
            "rank" to RANK,
            "trie" to TRIE,
        )
    }
}
