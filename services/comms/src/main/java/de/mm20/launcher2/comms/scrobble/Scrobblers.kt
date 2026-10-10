package de.mm20.launcher2.comms.scrobble

import android.content.Context
import de.mm20.launcher2.comms.remote.SecretBox
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

data class ScrobbleTrack(
    val artist: String,
    val title: String,
    val album: String,
    val durationSeconds: Int,
    val timestampSeconds: Long = System.currentTimeMillis() / 1000,
    /** Name of the player that is reported to the service */
    val player: String = "Telos Music",
)

/** Saved logins of the scrobbling services. Passwords are never stored, only session keys and tokens (encrypted). */
data class ScrobbleConfig(
    val lastfmEnabled: Boolean = false,
    val lastfmKey: String = "",
    val lastfmSecret: String = "",
    val lastfmUser: String = "",
    val lastfmSession: String = "",
    val librefmEnabled: Boolean = false,
    val librefmUser: String = "",
    /** md5 of the password, which is what the Libre.fm protocol needs */
    val librefmPasswordHash: String = "",
    val listenbrainzEnabled: Boolean = false,
    val listenbrainzToken: String = "",
    val listenbrainzServer: String = "https://api.listenbrainz.org",
    /** Scrobble what Telos Music plays */
    val musicEnabled: Boolean = false,
    /** Scrobble the current track of Telos Radio */
    val radioEnabled: Boolean = false,
) {
    val any: Boolean get() = (lastfmEnabled && lastfmSession.isNotBlank()) ||
        (librefmEnabled && librefmPasswordHash.isNotBlank()) ||
        (listenbrainzEnabled && listenbrainzToken.isNotBlank())
}

object Scrobblers {
    private const val PREFS = "scrobble"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context): ScrobbleConfig {
        val p = prefs(context)
        fun secret(k: String) = SecretBox.decrypt(p.getString(k, "").orEmpty())
        val c = ScrobbleConfig(
            lastfmEnabled = p.getBoolean("lastfm_on", false),
            lastfmKey = p.getString("lastfm_key", "").orEmpty(),
            lastfmSecret = secret("lastfm_secret"),
            lastfmUser = p.getString("lastfm_user", "").orEmpty(),
            lastfmSession = secret("lastfm_session"),
            librefmEnabled = p.getBoolean("librefm_on", false),
            librefmUser = p.getString("librefm_user", "").orEmpty(),
            librefmPasswordHash = secret("librefm_hash"),
            listenbrainzEnabled = p.getBoolean("lb_on", false),
            listenbrainzToken = secret("lb_token"),
            listenbrainzServer = p.getString("lb_server", "https://api.listenbrainz.org").orEmpty(),
            radioEnabled = p.getBoolean("radio_on", false),
        )
        // versions before the music switch scrobbled whenever a service was switched on
        return c.copy(musicEnabled = if (p.contains("music_on")) p.getBoolean("music_on", false) else c.any)
    }

    fun save(context: Context, c: ScrobbleConfig) {
        prefs(context).edit()
            .putBoolean("lastfm_on", c.lastfmEnabled)
            .putString("lastfm_key", c.lastfmKey.trim())
            .putString("lastfm_secret", SecretBox.encrypt(c.lastfmSecret.trim()))
            .putString("lastfm_user", c.lastfmUser.trim())
            .putString("lastfm_session", SecretBox.encrypt(c.lastfmSession))
            .putBoolean("librefm_on", c.librefmEnabled)
            .putString("librefm_user", c.librefmUser.trim())
            .putString("librefm_hash", SecretBox.encrypt(c.librefmPasswordHash))
            .putBoolean("music_on", c.musicEnabled)
            .putBoolean("radio_on", c.radioEnabled)
            .putBoolean("lb_on", c.listenbrainzEnabled)
            .putString("lb_token", SecretBox.encrypt(c.listenbrainzToken.trim()))
            .putString("lb_server", c.listenbrainzServer.trim().trimEnd('/').ifBlank { "https://api.listenbrainz.org" })
            .apply()
    }

    // ---- offline queue: scrobbles that could not be sent yet ----

    @Synchronized
    fun enqueue(context: Context, service: String, t: ScrobbleTrack) {
        val q = queue(context)
        q.put(JSONObject().put("s", service).put("a", t.artist).put("t", t.title).put("b", t.album).put("l", t.durationSeconds).put("ts", t.timestampSeconds))
        while (q.length() > 500) q.remove(0)
        setQueue(context, q)
    }

    @Synchronized
    fun queue(context: Context): JSONArray =
        runCatching {
            val raw = prefs(context).getString("queue", "").orEmpty()
            // the listening history is stored encrypted; older versions wrote plain JSON
            val text = if (raw.startsWith("[")) raw else SecretBox.decrypt(raw)
            JSONArray(text.ifBlank { "[]" })
        }.getOrDefault(JSONArray())

    @Synchronized
    fun clearQueue(context: Context) = setQueue(context, JSONArray())

    @Synchronized
    fun setQueue(context: Context, q: JSONArray) {
        val text = q.toString()
        val stored = runCatching { SecretBox.encrypt(text) }.getOrDefault(text)
        prefs(context).edit().putString("queue", stored).apply()
    }

    // ---- shared helpers ----

    fun md5Public(s: String): String = md5(s)

    internal fun md5(s: String): String =
        MessageDigest.getInstance("MD5").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    internal fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    /**
     * A server the user typed in must use https. Plain http is only accepted for a server in the own
     * network (localhost, *.local, private addresses), e.g. a self-hosted ListenBrainz.
     */
    fun isAllowedServer(url: String): Boolean {
        val u = runCatching { java.net.URI(url.trim()) }.getOrNull() ?: return false
        val host = u.host?.lowercase() ?: return false
        return when (u.scheme?.lowercase()) {
            "https" -> true
            "http" -> host == "localhost" || host.endsWith(".local") || host.endsWith(".lan") ||
                Regex("""^(10\.|127\.|192\.168\.|172\.(1[6-9]|2\d|3[01])\.)\d+\.\d+\.?\d*$""").matches(host) ||
                host == "::1" || host == "[::1]"
            else -> false
        }
    }

    internal fun post(url: String, form: String, headers: Map<String, String> = emptyMap(), json: Boolean = false): String {
        val c = URL(url).openConnection() as HttpURLConnection
        // a redirect could send the login to another host
        c.instanceFollowRedirects = false
        c.requestMethod = "POST"
        c.doOutput = true
        c.connectTimeout = 10000
        c.readTimeout = 15000
        c.setRequestProperty("User-Agent", "Telos Music/1.0 (https://github.com/koukomin/Telos-Launcher)")
        c.setRequestProperty("Content-Type", if (json) "application/json" else "application/x-www-form-urlencoded")
        headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
        try {
            c.outputStream.use { it.write(form.toByteArray()) }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("HTTP $code ${text.take(120)}")
            return text
        } finally {
            c.disconnect()
        }
    }

    internal fun get(url: String, headers: Map<String, String> = emptyMap()): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.instanceFollowRedirects = false
        headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
        c.connectTimeout = 10000
        c.readTimeout = 15000
        c.setRequestProperty("User-Agent", "Telos Music/1.0 (https://github.com/koukomin/Telos-Launcher)")
        try {
            if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }
}

/** Last.fm web service 2.0. Needs an API key and secret, which the user creates for free at last.fm/api. */
class LastFm(private val key: String, private val secret: String, private val session: String) {
    private val base = "https://ws.audioscrobbler.com/2.0/"

    private fun signed(params: Map<String, String>): String {
        val all = params + ("api_key" to key)
        val signature = Scrobblers.md5(all.toSortedMap().entries.joinToString("") { it.key + it.value } + secret)
        return (all + ("api_sig" to signature) + ("format" to "json"))
            .entries.joinToString("&") { it.key + "=" + Scrobblers.enc(it.value) }
    }

    fun nowPlaying(t: ScrobbleTrack) {
        val p = mutableMapOf("method" to "track.updateNowPlaying", "artist" to t.artist, "track" to t.title, "sk" to session)
        if (t.album.isNotBlank()) p["album"] = t.album
        if (t.durationSeconds > 0) p["duration"] = t.durationSeconds.toString()
        Scrobblers.post(base, signed(p))
    }

    fun scrobble(t: ScrobbleTrack) {
        val p = mutableMapOf(
            "method" to "track.scrobble", "artist" to t.artist, "track" to t.title,
            "timestamp" to t.timestampSeconds.toString(), "sk" to session,
        )
        if (t.album.isNotBlank()) p["album"] = t.album
        if (t.durationSeconds > 0) p["duration"] = t.durationSeconds.toString()
        Scrobblers.post(base, signed(p))
    }

    companion object {
        /** Returns the session key for a user name and password */
        fun login(key: String, secret: String, user: String, password: String): String {
            val params = mapOf("method" to "auth.getMobileSession", "username" to user, "password" to password)
            val all = params + ("api_key" to key)
            val sig = Scrobblers.md5(all.toSortedMap().entries.joinToString("") { it.key + it.value } + secret)
            val body = (all + ("api_sig" to sig) + ("format" to "json"))
                .entries.joinToString("&") { it.key + "=" + Scrobblers.enc(it.value) }
            val answer = JSONObject(Scrobblers.post("https://ws.audioscrobbler.com/2.0/", body))
            answer.optJSONObject("session")?.optString("key")?.takeIf { it.isNotBlank() }?.let { return it }
            throw IllegalStateException(answer.optString("message").takeIf { it.isNotBlank() })
        }
    }
}

/** Libre.fm (and other GNU FM servers) with the Audioscrobbler 1.2 protocol. */
class LibreFm(private val user: String, private val passwordHash: String) {
    private var sessionId = ""
    private var nowPlayingUrl = ""
    private var submitUrl = ""

    /** Checks the login; throws with the reason when it is refused */
    fun login() {
        sessionId = ""
        handshake()
    }

    private fun handshake() {
        if (sessionId.isNotEmpty()) return
        val ts = System.currentTimeMillis() / 1000
        val token = Scrobblers.md5(passwordHash + ts)
        val answer = Scrobblers.get(
            "https://turtle.libre.fm/?hs=true&p=1.2&c=tst&v=1.0&u=${Scrobblers.enc(user)}&t=$ts&a=$token"
        ).lines()
        if (answer.firstOrNull()?.trim() != "OK" || answer.size < 4) throw IllegalStateException(answer.firstOrNull().orEmpty().takeIf { it.isNotBlank() })
        val np = answer[2].trim()
        val sub = answer[3].trim()
        // the addresses come from the server's answer: only libre.fm itself
        if (!isLibreFmUrl(np) || !isLibreFmUrl(sub)) throw IllegalStateException(null as String?)
        sessionId = answer[1].trim()
        nowPlayingUrl = np
        submitUrl = sub
    }

    private fun isLibreFmUrl(url: String): Boolean {
        val u = runCatching { java.net.URI(url) }.getOrNull() ?: return false
        val host = u.host?.lowercase() ?: return false
        return u.scheme?.lowercase() in setOf("http", "https") && (host == "libre.fm" || host.endsWith(".libre.fm"))
    }

    private fun check(answer: String) {
        if (!answer.trim().startsWith("OK")) {
            sessionId = "" // session expired: log in again next time
            throw IllegalStateException(answer.trim().takeIf { it.isNotBlank() })
        }
    }

    fun nowPlaying(t: ScrobbleTrack) {
        handshake()
        check(
            Scrobblers.post(
                nowPlayingUrl,
                "s=$sessionId&a=${Scrobblers.enc(t.artist)}&t=${Scrobblers.enc(t.title)}&b=${Scrobblers.enc(t.album)}&l=${if (t.durationSeconds > 0) t.durationSeconds else ""}&n=&m="
            )
        )
    }

    fun scrobble(t: ScrobbleTrack) {
        handshake()
        check(
            Scrobblers.post(
                submitUrl,
                "s=$sessionId&a[0]=${Scrobblers.enc(t.artist)}&t[0]=${Scrobblers.enc(t.title)}&i[0]=${t.timestampSeconds}" +
                    "&o[0]=${if (t.durationSeconds > 0) "P" else "R"}&r[0]=&l[0]=${if (t.durationSeconds > 0) t.durationSeconds else ""}&b[0]=${Scrobblers.enc(t.album)}&n[0]=&m[0]="
            )
        )
    }
}

/** ListenBrainz (or a compatible server) with a user token from listenbrainz.org/profile. */
class ListenBrainz(private val token: String, private val server: String) {
    private fun payload(type: String, t: ScrobbleTrack): String {
        val meta = JSONObject()
            .put("artist_name", t.artist).put("track_name", t.title)
            .apply { if (t.album.isNotBlank()) put("release_name", t.album) }
            .put("additional_info", JSONObject().put("media_player", t.player).apply { if (t.durationSeconds > 0) put("duration", t.durationSeconds) })
        val listen = JSONObject().put("track_metadata", meta)
        if (type == "single") listen.put("listened_at", t.timestampSeconds)
        return JSONObject().put("listen_type", type).put("payload", JSONArray().put(listen)).toString()
    }

    private fun send(type: String, t: ScrobbleTrack) {
        if (!Scrobblers.isAllowedServer(server)) error("https")
        Scrobblers.post("$server/1/submit-listens", payload(type, t), mapOf("Authorization" to "Token $token"), json = true)
    }

    /** Checks the token and returns the user name it belongs to */
    fun validate(): String {
        if (!Scrobblers.isAllowedServer(server)) error("https")
        val answer = JSONObject(Scrobblers.get("$server/1/validate-token", mapOf("Authorization" to "Token $token")))
        if (!answer.optBoolean("valid")) throw IllegalStateException(answer.optString("message").takeIf { it.isNotBlank() })
        return answer.optString("user_name")
    }

    fun nowPlaying(t: ScrobbleTrack) = send("playing_now", t)
    fun scrobble(t: ScrobbleTrack) = send("single", t)
}
