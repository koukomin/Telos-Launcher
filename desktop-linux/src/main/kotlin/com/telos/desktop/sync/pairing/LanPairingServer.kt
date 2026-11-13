package com.telos.desktop.sync.pairing

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import com.telos.desktop.network.DesktopNetworkController
import com.telos.desktop.sync.DesktopSessionSync
import com.telos.desktop.vault.TelosSessionAuditLog
import java.io.File
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

/**
 * Short-lived LAN HTTP pairing server (TLS optional later).
 * Binds only private/local interfaces. Never logs pairing codes or tokens.
 */
class LanPairingServer(
    private val bindHost: String = LanPairProtocol.suggestLanBindHost(),
    private val port: Int = LanPairProtocol.DEFAULT_PORT,
    private val tls: Boolean = false,
) {
    data class LiveSession(
        val code: String,
        val host: String,
        val port: Int,
        val tls: Boolean,
        val expiresAtEpochMs: Long,
        val uri: String,
    ) {
        fun isExpired(now: Long = System.currentTimeMillis()): Boolean = now >= expiresAtEpochMs
    }

    data class Status(
        val running: Boolean,
        val session: LiveSession? = null,
        val lastError: String? = null,
        val pairedDeviceId: String? = null,
        val incomingBundleName: String? = null,
    )

    private val sessionRef = AtomicReference<LiveSession?>(null)
    private val pairedDeviceRef = AtomicReference<String?>(null)
    private val lastErrorRef = AtomicReference<String?>(null)
    private var server: HttpServer? = null
    private val executor = Executors.newCachedThreadPool { r ->
        Thread(r, "telos-lan-pair").apply { isDaemon = true }
    }

    fun status(): Status {
        val live = sessionRef.get()?.takeUnless { it.isExpired() }
        if (sessionRef.get()?.isExpired() == true) {
            sessionRef.compareAndSet(sessionRef.get(), null)
        }
        return Status(
            running = server != null,
            session = live,
            lastError = lastErrorRef.get(),
            pairedDeviceId = pairedDeviceRef.get(),
            incomingBundleName = incomingBundle().takeIf { it.isFile }?.name,
        )
    }

    fun start(code: String = LanPairProtocol.generateCode()): Result<LiveSession> = runCatching {
        stopInternal(audit = false)
        val addr = LanPairProtocol.requirePrivateBindAddress(bindHost)
        val normalized = LanPairProtocol.normalizeCode(code)
        require(normalized.length >= 4) { "pairing code too short" }
        val http = HttpServer.create(InetSocketAddress(addr, port), 0)
        http.executor = executor
        http.createContext("/v1/health", ::handleHealth)
        http.createContext("/v1/offer", ::handleOffer)
        http.createContext("/v1/pair", ::handlePair)
        http.createContext("/v1/bundle", ::handleBundle)
        http.start()
        server = http
        val live = LiveSession(
            code = normalized,
            host = addr.hostAddress,
            port = port,
            tls = tls,
            expiresAtEpochMs = System.currentTimeMillis() + LanPairProtocol.CODE_TTL_MS,
            uri = LanPairProtocol.buildUri(normalized, addr.hostAddress, port, tls),
        )
        sessionRef.set(live)
        lastErrorRef.set(null)
        pairedDeviceRef.set(null)
        // Audit: host/port only — never the pairing code.
        TelosSessionAuditLog.append("lan_pair", "started host=${addr.hostAddress} port=$port")
        live
    }.onFailure { err ->
        lastErrorRef.set(err.message?.take(200))
        TelosSessionAuditLog.append("lan_pair", "start_failed")
    }

    fun stop() = stopInternal(audit = true)

    private fun stopInternal(audit: Boolean) {
        runCatching { server?.stop(0) }
        server = null
        sessionRef.set(null)
        if (audit) TelosSessionAuditLog.append("lan_pair", "stopped")
    }

    private fun handleHealth(ex: HttpExchange) {
        if (ex.requestMethod != "GET") {
            sendText(ex, 405, """{"error":"method_not_allowed"}""")
            return
        }
        sendText(ex, 200, """{"ok":true,"service":"telos-lan-pair","tls":$tls}""")
    }

    private fun handleOffer(ex: HttpExchange) {
        if (ex.requestMethod != "GET") {
            sendText(ex, 405, """{"error":"method_not_allowed"}""")
            return
        }
        val live = requireLiveSession(ex) ?: return
        val provided = LanPairProtocol.normalizeCode(
            headerCode(ex) ?: queryParam(ex, "code").orEmpty(),
        )
        if (provided != live.code) {
            sendText(ex, 401, """{"error":"invalid_code"}""")
            return
        }
        val bundle = File(DesktopSessionSync.defaultExportPath(), DesktopSessionSync.BUNDLE_NAME)
        val json = buildString {
            append('{')
            append("\"expiresAt\":").append(live.expiresAtEpochMs).append(',')
            append("\"host\":\"").append(jsonEscape(live.host)).append("\",")
            append("\"port\":").append(live.port).append(',')
            append("\"bundleName\":\"").append(jsonEscape(DesktopSessionSync.BUNDLE_NAME)).append("\",")
            append("\"bundleReady\":").append(bundle.isFile).append(',')
            append("\"format\":\"zip4j-aes256\"")
            append('}')
        }
        sendText(ex, 200, json)
    }

    private fun handlePair(ex: HttpExchange) {
        if (ex.requestMethod != "POST") {
            sendText(ex, 405, """{"error":"method_not_allowed"}""")
            return
        }
        val live = requireLiveSession(ex) ?: return
        val body = String(ex.requestBody.readBytes(), StandardCharsets.UTF_8)
        val code = LanPairProtocol.normalizeCode(
            extractJsonString(body, "code")
                ?: headerCode(ex)
                ?: queryParam(ex, "code").orEmpty(),
        )
        if (code != live.code) {
            sendText(ex, 401, """{"error":"invalid_code"}""")
            return
        }
        val deviceId = extractJsonString(body, "deviceId")?.takeIf { it.isNotBlank() } ?: "android"
        val deviceName = extractJsonString(body, "deviceName")?.takeIf { it.isNotBlank() } ?: "Android"
        val token = LanPairTokenStore.mintToken()
        if (!LanPairTokenStore.remember(token, deviceId, deviceName)) {
            sendText(ex, 500, """{"error":"token_store_failed"}""")
            return
        }
        pairedDeviceRef.set(deviceId.take(64))
        TelosSessionAuditLog.append("lan_pair", "paired device=${deviceId.take(64)}")
        val json = buildString {
            append('{')
            append("\"token\":\"").append(jsonEscape(token)).append("\",")
            append("\"expiresAt\":").append(live.expiresAtEpochMs).append(',')
            append("\"bundleName\":\"").append(jsonEscape(DesktopSessionSync.BUNDLE_NAME)).append('"')
            append('}')
        }
        sendText(ex, 200, json)
    }

    private fun handleBundle(ex: HttpExchange) {
        when (ex.requestMethod) {
            "GET" -> {
                if (!authorizeToken(ex)) {
                    sendText(ex, 401, """{"error":"unauthorized"}""")
                    return
                }
                val bundle = File(DesktopSessionSync.defaultExportPath(), DesktopSessionSync.BUNDLE_NAME)
                if (!bundle.isFile) {
                    sendText(ex, 404, """{"error":"bundle_missing"}""")
                    return
                }
                sendBytes(ex, 200, "application/octet-stream", bundle.readBytes(), bundle.name)
                TelosSessionAuditLog.append("lan_pair", "bundle_sent bytes=${bundle.length()}")
            }
            "POST" -> {
                if (!authorizeToken(ex)) {
                    sendText(ex, 401, """{"error":"unauthorized"}""")
                    return
                }
                val bytes = ex.requestBody.readBytes()
                if (bytes.isEmpty() || bytes.size > 64 * 1024 * 1024) {
                    sendText(ex, 400, """{"error":"invalid_bundle"}""")
                    return
                }
                val dest = incomingBundle()
                dest.parentFile?.mkdirs()
                dest.writeBytes(bytes)
                TelosSessionAuditLog.append("lan_pair", "bundle_received bytes=${bytes.size}")
                sendText(
                    ex,
                    200,
                    """{"ok":true,"savedAs":"${jsonEscape(dest.name)}","bytes":${bytes.size}}""",
                )
            }
            else -> sendText(ex, 405, """{"error":"method_not_allowed"}""")
        }
    }

    private fun requireLiveSession(ex: HttpExchange): LiveSession? {
        val live = sessionRef.get()
        if (live == null || live.isExpired()) {
            sendText(ex, 410, """{"error":"pairing_expired"}""")
            return null
        }
        return live
    }

    private fun authorizeToken(ex: HttpExchange): Boolean {
        val auth = ex.requestHeaders.getFirst("Authorization").orEmpty()
        val bearer = if (auth.startsWith("Bearer ", ignoreCase = true)) {
            auth.substring(7).trim()
        } else {
            ""
        }
        val headerToken = ex.requestHeaders.getFirst("X-Telos-Pair-Token").orEmpty().trim()
        val token = bearer.ifBlank { headerToken }
        return LanPairTokenStore.isAuthorized(token)
    }

    private fun headerCode(ex: HttpExchange): String? =
        ex.requestHeaders.getFirst("X-Telos-Pair-Code")?.trim()?.takeIf { it.isNotEmpty() }

    private fun queryParam(ex: HttpExchange, key: String): String? {
        val raw = ex.requestURI.rawQuery ?: return null
        return LanPairProtocol.parseQuery(raw)[key]
    }

    private fun incomingBundle(): File =
        File(DesktopNetworkController.configHome(), "sync/incoming/${DesktopSessionSync.BUNDLE_NAME}")

    private fun sendText(ex: HttpExchange, code: Int, body: String) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        ex.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
        ex.responseHeaders.set("Cache-Control", "no-store")
        ex.sendResponseHeaders(code, bytes.size.toLong())
        ex.responseBody.use { it.write(bytes) }
    }

    private fun sendBytes(
        ex: HttpExchange,
        code: Int,
        contentType: String,
        bytes: ByteArray,
        filename: String,
    ) {
        ex.responseHeaders.set("Content-Type", contentType)
        ex.responseHeaders.set("Content-Disposition", "attachment; filename=\"$filename\"")
        ex.responseHeaders.set("Cache-Control", "no-store")
        ex.sendResponseHeaders(code, bytes.size.toLong())
        ex.responseBody.use { it.write(bytes) }
    }

    private fun extractJsonString(body: String, key: String): String? {
        val pattern = Regex(""""$key"\s*:\s*"([^"\\]*(?:\\.[^"\\]*)*)"""")
        val match = pattern.find(body) ?: return null
        return match.groupValues[1]
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
    }

    private fun jsonEscape(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")

    companion object {
        @Volatile
        private var shared: LanPairingServer? = null

        fun sharedOrNull(): LanPairingServer? = shared

        fun shared(): LanPairingServer {
            shared?.let { return it }
            synchronized(this) {
                shared?.let { return it }
                return LanPairingServer().also { shared = it }
            }
        }

        /** Stop any previous shared server, bind [bindHost], mint a code, start listening. */
        fun startShared(
            bindHost: String = LanPairProtocol.suggestLanBindHost(),
            port: Int = LanPairProtocol.DEFAULT_PORT,
            code: String = LanPairProtocol.generateCode(),
        ): Result<LiveSession> = synchronized(this) {
            shared?.stop()
            val next = LanPairingServer(bindHost = bindHost, port = port)
            shared = next
            next.start(code)
        }

        fun stopShared() = synchronized(this) {
            shared?.stop()
            shared = null
        }

        fun resetSharedForTests() = stopShared()
    }
}
