package de.mm20.launcher2.comms.media.video.trakt

import android.content.Context
import de.mm20.launcher2.comms.media.video.ParsedName
import de.mm20.launcher2.comms.remote.SecretBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class TraktDeviceCode(
    val deviceCode: String,
    val userCode: String,
    val verificationUrl: String,
    val intervalSeconds: Int,
    val expiresInSeconds: Int,
)

/**
 * Trakt.tv (https://trakt.tv): scrobbling of what is played, watched marks in the library and the
 * watchlist. The user creates an application at trakt.tv/oauth/applications and enters its
 * client id and secret; signing in uses the device code (no redirect address needed).
 */
object Trakt {
    private const val API = "https://api.trakt.tv"
    private const val PREFS = "trakt"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---- stored login ----

    data class Login(
        val clientId: String,
        val clientSecret: String,
        val accessToken: String,
        val refreshToken: String,
        val expiresAtSeconds: Long,
        val enabled: Boolean,
    ) {
        val connected: Boolean get() = accessToken.isNotBlank() && clientId.isNotBlank()
    }

    fun login(context: Context): Login {
        val p = prefs(context)
        return Login(
            clientId = p.getString("client_id", "").orEmpty(),
            clientSecret = SecretBox.decrypt(p.getString("client_secret", "").orEmpty()),
            accessToken = SecretBox.decrypt(p.getString("access", "").orEmpty()),
            refreshToken = SecretBox.decrypt(p.getString("refresh", "").orEmpty()),
            expiresAtSeconds = p.getLong("expires", 0),
            enabled = p.getBoolean("enabled", true),
        )
    }

    fun saveApp(context: Context, clientId: String, clientSecret: String) {
        prefs(context).edit()
            .putString("client_id", clientId.trim())
            .putString("client_secret", SecretBox.encrypt(clientSecret.trim()))
            .apply()
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("enabled", enabled).apply()
    }

    fun signOut(context: Context) {
        prefs(context).edit().remove("access").remove("refresh").remove("expires").remove("watched").apply()
        watchedCache = null
    }

    private fun saveTokens(context: Context, json: JSONObject) {
        prefs(context).edit()
            .putString("access", SecretBox.encrypt(json.getString("access_token")))
            .putString("refresh", SecretBox.encrypt(json.optString("refresh_token")))
            .putLong("expires", json.optLong("created_at", System.currentTimeMillis() / 1000) + json.optLong("expires_in", 7_776_000))
            .apply()
    }

    // ---- http ----

    private class HttpResult(val code: Int, val body: String)

    private fun request(
        context: Context?,
        method: String,
        path: String,
        body: JSONObject? = null,
        clientId: String,
        token: String? = null,
    ): HttpResult {
        val c = URL(API + path).openConnection() as HttpURLConnection
        c.requestMethod = method
        // the API never redirects; a redirect must not carry the bearer token to another host
        c.instanceFollowRedirects = false
        c.connectTimeout = 10000
        c.readTimeout = 20000
        c.setRequestProperty("Content-Type", "application/json")
        c.setRequestProperty("trakt-api-version", "2")
        c.setRequestProperty("trakt-api-key", clientId)
        c.setRequestProperty("User-Agent", "Telos Video/1.0")
        token?.let { c.setRequestProperty("Authorization", "Bearer $it") }
        try {
            if (body != null) {
                c.doOutput = true
                c.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            return HttpResult(code, text)
        } finally {
            c.disconnect()
        }
    }

    // ---- sign in with the device code ----

    suspend fun startDeviceLogin(clientId: String): TraktDeviceCode = withContext(Dispatchers.IO) {
        val r = request(null, "POST", "/oauth/device/code", JSONObject().put("client_id", clientId), clientId)
        if (r.code != 200) error("Trakt answered ${r.code}, check the client id")
        val j = JSONObject(r.body)
        TraktDeviceCode(
            deviceCode = j.getString("device_code"),
            userCode = j.getString("user_code"),
            verificationUrl = j.optString("verification_url", "https://trakt.tv/activate"),
            intervalSeconds = j.optInt("interval", 5),
            expiresInSeconds = j.optInt("expires_in", 600),
        )
    }

    /** Waits until the user entered the code on trakt.tv; throws when it expired or was refused. */
    suspend fun finishDeviceLogin(context: Context, clientId: String, clientSecret: String, code: TraktDeviceCode) {
        var waited = 0
        var interval = code.intervalSeconds.coerceAtLeast(1)
        while (waited < code.expiresInSeconds) {
            delay(interval * 1000L)
            waited += interval
            val r = withContext(Dispatchers.IO) {
                request(
                    context, "POST", "/oauth/device/token",
                    JSONObject().put("code", code.deviceCode).put("client_id", clientId).put("client_secret", clientSecret),
                    clientId,
                )
            }
            when (r.code) {
                200 -> {
                    saveTokens(context, JSONObject(r.body))
                    return
                }
                400 -> Unit // still waiting
                429 -> interval += 1
                404, 409 -> error("Invalid code")
                410 -> error("The code expired")
                418 -> error("Sign in was denied")
                else -> error("Trakt answered ${r.code}")
            }
        }
        error("The code expired")
    }

    /** A valid access token, refreshed when it is about to expire; null when not signed in. */
    private fun token(context: Context): String? {
        val l = login(context)
        if (!l.connected) return null
        val now = System.currentTimeMillis() / 1000
        if (l.expiresAtSeconds - now > 3600 || l.refreshToken.isBlank()) return l.accessToken
        val r = request(
            context, "POST", "/oauth/token",
            JSONObject().put("refresh_token", l.refreshToken).put("client_id", l.clientId)
                .put("client_secret", l.clientSecret).put("redirect_uri", "urn:ietf:wg:oauth:2.0:oob")
                .put("grant_type", "refresh_token"),
            l.clientId,
        )
        if (r.code != 200) return l.accessToken
        saveTokens(context, JSONObject(r.body))
        return login(context).accessToken
    }

    // ---- scrobbling ----

    private fun media(parsed: ParsedName): JSONObject {
        return if (parsed.isEpisode) {
            JSONObject()
                .put("show", JSONObject().put("title", parsed.title))
                .put("episode", JSONObject().put("season", parsed.season).put("number", parsed.episode))
        } else {
            JSONObject().put(
                "movie",
                JSONObject().put("title", parsed.title).apply { parsed.year?.let { put("year", it) } },
            )
        }
    }

    /** action: start, pause or stop. Trakt marks the title as watched when it is stopped at 80 % or more. */
    suspend fun scrobble(context: Context, action: String, parsed: ParsedName, progressPercent: Float): Boolean =
        withContext(Dispatchers.IO) {
            val l = login(context)
            if (!l.enabled || !l.connected || parsed.title.isBlank()) return@withContext false
            val token = token(context) ?: return@withContext false
            val body = media(parsed).put("progress", progressPercent.coerceIn(0f, 100f).toDouble())
            val r = runCatching { request(context, "POST", "/scrobble/$action", body, l.clientId, token) }.getOrNull()
            val ok = r != null && r.code in 200..299
            if (ok && action == "stop" && progressPercent >= 80f) markWatchedLocally(context, parsed)
            ok
        }

    // ---- watched marks ----

    private fun normalize(s: String) = s.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    private fun watchedKeyMovie(title: String, year: Int?) = "m:" + normalize(title) + (year?.let { ":$it" } ?: "")
    private fun watchedKeyEpisode(show: String, season: Int, episode: Int) = "e:" + normalize(show) + ":$season:$episode"

    @Volatile
    private var watchedCache: Set<String>? = null

    @Synchronized
    private fun watchedSet(context: Context): MutableSet<String> =
        prefs(context).getStringSet("watched", emptySet())!!.toMutableSet()

    private fun cachedWatched(context: Context): Set<String> =
        watchedCache ?: watchedSet(context).also { watchedCache = it }

    @Synchronized
    private fun markWatchedLocally(context: Context, parsed: ParsedName) {
        val set = watchedSet(context)
        set += if (parsed.isEpisode) watchedKeyEpisode(parsed.title, parsed.season!!, parsed.episode!!)
        else watchedKeyMovie(parsed.title, parsed.year)
        prefs(context).edit().putStringSet("watched", set).apply()
        watchedCache = set
    }

    fun isWatched(context: Context, parsed: ParsedName): Boolean {
        val set = cachedWatched(context)
        return if (parsed.isEpisode) watchedKeyEpisode(parsed.title, parsed.season!!, parsed.episode!!) in set
        else watchedKeyMovie(parsed.title, parsed.year) in set || watchedKeyMovie(parsed.title, null) in set
    }

    /** Downloads what the user already watched, so it can be marked in the library. At most once an hour. */
    suspend fun refreshWatched(context: Context, force: Boolean = false) = withContext(Dispatchers.IO) {
        val l = login(context)
        if (!l.enabled || !l.connected) return@withContext
        val last = prefs(context).getLong("watched_at", 0)
        if (!force && System.currentTimeMillis() - last < 3_600_000) return@withContext
        val token = token(context) ?: return@withContext
        runCatching {
            val set = HashSet<String>()
            val movies = request(context, "GET", "/sync/watched/movies", null, l.clientId, token)
            if (movies.code == 200) {
                val a = JSONArray(movies.body)
                for (i in 0 until a.length()) {
                    val m = a.getJSONObject(i).optJSONObject("movie") ?: continue
                    val title = m.optString("title")
                    set += watchedKeyMovie(title, m.optInt("year").takeIf { it > 0 })
                    set += watchedKeyMovie(title, null)
                }
            }
            val full = request(context, "GET", "/sync/watched/shows", null, l.clientId, token)
            if (full.code == 200) {
                val a = JSONArray(full.body)
                for (i in 0 until a.length()) {
                    val o = a.getJSONObject(i)
                    val show = o.optJSONObject("show")?.optString("title") ?: continue
                    val seasons = o.optJSONArray("seasons") ?: continue
                    for (s in 0 until seasons.length()) {
                        val season = seasons.getJSONObject(s)
                        val sn = season.optInt("number")
                        val eps = season.optJSONArray("episodes") ?: continue
                        for (e in 0 until eps.length()) set += watchedKeyEpisode(show, sn, eps.getJSONObject(e).optInt("number"))
                    }
                }
            }
            if (movies.code == 200 || full.code == 200) {
                prefs(context).edit().putStringSet("watched", set).putLong("watched_at", System.currentTimeMillis()).apply()
                watchedCache = set
            }
        }
    }

    // ---- watchlist ----

    /** Adds a movie or a series to the Trakt watchlist. */
    suspend fun addToWatchlist(context: Context, title: String, year: Int?, series: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            val l = login(context)
            if (!l.connected) return@withContext false
            val token = token(context) ?: return@withContext false
            val item = JSONObject().put("title", title).apply { year?.let { put("year", it) } }
            val body = JSONObject().put(if (series) "shows" else "movies", JSONArray().put(item))
            val r = runCatching { request(context, "POST", "/sync/watchlist", body, l.clientId, token) }.getOrNull()
            r != null && r.code in 200..299 && (JSONObject(r.body).optJSONObject("added")?.let {
                it.optInt("movies") + it.optInt("shows") > 0
            } ?: false || JSONObject(r.body).optJSONObject("existing")?.let { it.optInt("movies") + it.optInt("shows") > 0 } ?: false)
        }
}
