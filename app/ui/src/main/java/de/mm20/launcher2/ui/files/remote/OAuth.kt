package de.mm20.launcher2.ui.files.remote

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.security.SecureRandom
import android.util.Base64
import java.net.URLDecoder
import java.net.URLEncoder

/** How one cloud service signs a user in. The app has to be registered by the user, who enters its ID. */
class OAuthProvider(
    val authUrl: String,
    val tokenUrl: String,
    val scope: String?,
    val redirect: String,
    val extraAuth: Map<String, String> = emptyMap(),
    val needsSecret: Boolean = false,
    val consoleHint: String,
)

object OAuthProviders {
    /** The address the service sends the user back to. A small server on the phone listens there while signing in. */
    const val PORT = 53682

    fun of(type: RemoteType): OAuthProvider = when (type) {
        RemoteType.Dropbox -> OAuthProvider(
            authUrl = "https://www.dropbox.com/oauth2/authorize", tokenUrl = "https://api.dropboxapi.com/oauth2/token", scope = null,
            redirect = "http://localhost:$PORT/", extraAuth = mapOf("token_access_type" to "offline"),
            consoleHint = "In the Dropbox App Console create an app (scoped access, full Dropbox), enable files.metadata.read/write and files.content.read/write, and add the redirect address http://localhost:$PORT/ . Enter its app key here.",
        )
        RemoteType.GoogleDrive -> OAuthProvider(
            authUrl = "https://accounts.google.com/o/oauth2/v2/auth", tokenUrl = "https://oauth2.googleapis.com/token",
            scope = "https://www.googleapis.com/auth/drive", redirect = "http://127.0.0.1:$PORT/",
            extraAuth = mapOf("access_type" to "offline", "prompt" to "consent"), needsSecret = true,
            consoleHint = "In the Google Cloud console enable the Google Drive API, create an OAuth client ID of type Desktop app and enter its client ID and client secret here.",
        )
        RemoteType.OneDrive -> OAuthProvider(
            authUrl = "https://login.microsoftonline.com/common/oauth2/v2.0/authorize", tokenUrl = "https://login.microsoftonline.com/common/oauth2/v2.0/token",
            scope = "Files.ReadWrite.All offline_access", redirect = "http://localhost:$PORT/",
            consoleHint = "In the Azure portal register an app (personal and work accounts), add a Mobile and desktop platform with the redirect address http://localhost:$PORT/ , and enter the application (client) ID here.",
        )
        else -> throw IllegalArgumentException("Not a cloud storage")
    }
}

object Pkce {
    private val random = SecureRandom()
    fun verifier(): String = ByteArray(48).also { random.nextBytes(it) }.let { Base64.encodeToString(it, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP) }
    fun challenge(verifier: String): String =
        Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    fun state(): String = verifier().take(24)
}

object OAuth {
    private val http = OkHttpClient()

    fun authorizeUrl(c: RemoteConnection, verifier: String, state: String): String {
        val p = OAuthProviders.of(c.type)
        val params = linkedMapOf(
            "client_id" to c.clientId, "response_type" to "code", "redirect_uri" to p.redirect,
            "code_challenge" to Pkce.challenge(verifier), "code_challenge_method" to "S256", "state" to state,
        )
        p.scope?.let { params["scope"] = it }
        params.putAll(p.extraAuth)
        return p.authUrl + "?" + params.entries.joinToString("&") { "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}" }
    }

    /**
     * Waits for the service to send the user back with a code. Returns the code, or fails after [timeoutMs].
     * Only a request with the expected [state] is accepted.
     */
    fun waitForCode(state: String, timeoutMs: Int = 180_000): String {
        ServerSocket(OAuthProviders.PORT, 5, InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout = timeoutMs
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                val socket = try { server.accept() } catch (e: SocketTimeoutException) { break }
                socket.use { s ->
                    // a browser may open a connection and send nothing: do not wait for it forever
                    s.soTimeout = 5_000
                    val line = try { s.getInputStream().bufferedReader().readLine().orEmpty() } catch (e: java.io.IOException) { return@use } // GET /?code=...&state=... HTTP/1.1
                    val query = line.substringAfter(' ').substringBefore(' ').substringAfter('?', "")
                    val params = query.split('&').filter { it.contains('=') }.associate {
                        URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('='), "UTF-8")
                    }
                    val ok = params["state"] == state && params["code"] != null
                    val body = if (ok) "Signed in. You can close this tab and go back to Telos." else (params["error_description"] ?: params["error"] ?: "Nothing to do here.")
                    val safeBody = body.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
                    val html = "<html><body style=\"font-family:sans-serif;text-align:center;margin-top:20vh\"><h2>$safeBody</h2></body></html>"
                    s.getOutputStream().apply {
                        write("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${html.toByteArray().size}\r\nConnection: close\r\n\r\n$html".toByteArray())
                        flush()
                    }
                    if (ok) return params["code"]!!
                    if (params.containsKey("error")) throw IOException(params["error_description"] ?: params["error"])
                }
            }
        }
        throw IOException("Signing in took too long")
    }

    /** Trades the code for a refresh token. */
    fun exchange(c: RemoteConnection, code: String, verifier: String): String {
        val p = OAuthProviders.of(c.type)
        val form = FormBody.Builder()
            .add("grant_type", "authorization_code").add("code", code).add("client_id", c.clientId)
            .add("redirect_uri", p.redirect).add("code_verifier", verifier)
        if (c.clientSecret.isNotEmpty()) form.add("client_secret", c.clientSecret)
        http.newCall(Request.Builder().url(p.tokenUrl).post(form.build()).build()).execute().use { r ->
            val json = JSONObject(r.body?.string().orEmpty().ifBlank { "{}" })
            if (!r.isSuccessful) throw IOException(json.optString("error_description", json.optString("error", "Sign-in failed (${r.code})")))
            return json.optString("refresh_token").ifEmpty { throw IOException("The service did not give a refresh token") }
        }
    }
}

/** Keeps an access token fresh using the refresh token of the connection. */
class AccessTokens(private var connection: RemoteConnection, private val persist: (RemoteConnection) -> Unit) {
    private val http = OkHttpClient()
    private var token = ""
    private var expires = 0L

    @Synchronized
    fun get(): String {
        if (token.isNotEmpty() && System.currentTimeMillis() < expires - 60_000) return token
        if (connection.refreshToken.isEmpty()) throw IOException("Not signed in. Open the connection and sign in.")
        val p = OAuthProviders.of(connection.type)
        val form = FormBody.Builder().add("grant_type", "refresh_token").add("refresh_token", connection.refreshToken).add("client_id", connection.clientId)
        if (connection.clientSecret.isNotEmpty()) form.add("client_secret", connection.clientSecret)
        p.scope?.let { form.add("scope", it) }
        http.newCall(Request.Builder().url(p.tokenUrl).post(form.build()).build()).execute().use { r ->
            val json = JSONObject(r.body?.string().orEmpty().ifBlank { "{}" })
            if (!r.isSuccessful) throw IOException(json.optString("error_description", json.optString("error", "Could not sign in again (${r.code})")))
            token = json.getString("access_token")
            expires = System.currentTimeMillis() + json.optLong("expires_in", 3600) * 1000
            val rotated = json.optString("refresh_token")
            if (rotated.isNotEmpty() && rotated != connection.refreshToken) {
                connection = connection.copy(refreshToken = rotated)
                persist(connection)
            }
        }
        return token
    }
}
