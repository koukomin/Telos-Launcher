package de.mm20.launcher2.network.nextdns

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

internal class NextDnsRequest(val method: String, val path: String, val apiKey: String, val body: String? = null) {
    override fun toString() = "NextDnsRequest($method)"
}
internal data class NextDnsResponse(val status: Int, val body: String)
internal fun interface NextDnsTransport { suspend fun exchange(request: NextDnsRequest): NextDnsResponse }

/** Only the official HTTPS origin is reachable, without redirects, logging or mutation retries. */
internal class NextDnsHttpsTransport : NextDnsTransport {
    private val client by lazy {
        OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
            .connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS).build()
    }
    override suspend fun exchange(request: NextDnsRequest): NextDnsResponse = suspendCancellableCoroutine { continuation ->
        val httpRequest = try { buildNextDnsHttpRequest(request) }
            catch (failure: NextDnsException) {
                continuation.resumeWithException(failure)
                return@suspendCancellableCoroutine
            }
        val call = client.newCall(httpRequest)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(NextDnsException(NextDnsErrorKind.NETWORK))
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val result = response.use {
                        val output = ByteArrayOutputStream()
                        val body = it.body
                        if (body != null) {
                            if (body.contentLength() > MAX_BYTES) throw NextDnsException(NextDnsErrorKind.API)
                            body.byteStream().use { stream ->
                                val buffer = ByteArray(8192)
                                while (true) {
                                    val count = stream.read(buffer)
                                    if (count < 0) break
                                    if (output.size() + count > MAX_BYTES) throw NextDnsException(NextDnsErrorKind.API)
                                    output.write(buffer, 0, count)
                                }
                            }
                        }
                        NextDnsResponse(it.code, output.toString("UTF-8"))
                    }
                    if (continuation.isActive) continuation.resume(result)
                } catch (failure: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(
                        failure as? NextDnsException ?: NextDnsException(NextDnsErrorKind.NETWORK))
                }
            }
        })
    }
    private companion object { const val MAX_BYTES = 1_048_576L }
}

/** Shared by production and contract tests; credentials never become URL/query parameters. */
internal fun buildNextDnsHttpRequest(request: NextDnsRequest): Request {
    if (!request.path.startsWith("/profiles/") || request.path.any(Char::isISOControl) ||
        request.method !in setOf("GET", "POST", "PATCH", "DELETE"))
        throw NextDnsException(NextDnsErrorKind.INVALID)
    return Request.Builder().url("https://api.nextdns.io${request.path}")
        .header("X-Api-Key", NextDnsValidation.apiKey(request.apiKey)).header("Accept", "application/json")
        .method(request.method, request.body?.toRequestBody("application/json; charset=utf-8".toMediaType())).build()
}
