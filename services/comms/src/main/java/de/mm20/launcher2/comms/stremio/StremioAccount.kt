package de.mm20.launcher2.comms.stremio

import android.content.Context
import de.mm20.launcher2.comms.media.video.Http
import de.mm20.launcher2.comms.remote.SecretBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/**
 * Optional sign-in to a Stremio account, to keep the add-ons, the library and "continue watching" in step with the
 * account (this is how the Stremio apps back these up). It talks to Stremio's account service at api.strem.io with the
 * requests its open-source core (stremio-core, MIT) documents: login, addonCollectionGet/Set and datastoreGet/Put.
 * The password is sent once and never stored; only the session key it returns is kept, encrypted with the Android Keystore.
 * Stremio can change or block this service at any time; nothing here is endorsed by Stremio.
 */
class StremioAccount(
    context: Context,
    private val addons: StremioAddons,
    private val library: StremioLibrary,
) {
    private val prefs = context.applicationContext.getSharedPreferences("stremio_account", Context.MODE_PRIVATE)
    private val mutex = Mutex()

    private val _email = MutableStateFlow(prefs.getString("email", null)?.takeIf { authKey() != null })
    /** The email of the account that is signed in, or null */
    val email: StateFlow<String?> get() = _email

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> get() = _busy

    private val _watched = MutableStateFlow<Map<String, StremioWatched>>(emptyMap())
    val watched: StateFlow<Map<String, StremioWatched>> get() = _watched

    private fun authKey(): String? =
        prefs.getString("key", null)?.let { runCatching { SecretBox.decrypt(it) }.getOrNull() }?.takeIf { it.isNotBlank() }

    val signedIn: Boolean get() = authKey() != null

    // ---- requests ----

    private suspend fun post(path: String, body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val c = Http.open("$API/$path", StremioClient.USER_AGENT)
        c.requestMethod = "POST"
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        c.outputStream.use { it.write(body.toString().toByteArray()) }
        val answer = JSONObject(Http.readText(c))
        answer.optJSONObject("error")?.let { error(it.optString("message", "Stremio refused the request")) }
        answer
    }

    suspend fun signIn(email: String, password: String): Result<Unit> = runCatching {
        val answer = post("login", JSONObject().put("type", "Login").put("email", email.trim()).put("password", password).put("facebook", false))
        val key = answer.optJSONObject("result")?.optString("authKey").orEmpty()
        check(key.isNotBlank()) { "Stremio sent no session" }
        prefs.edit().putString("key", SecretBox.encrypt(key)).putString("email", email.trim()).apply()
        _email.value = email.trim()
    }

    suspend fun signOut() {
        val key = mutex.withLock {
            val currentKey = authKey()
            prefs.edit().clear().apply()
            _email.value = null
            _watched.value = emptyMap()
            currentKey
        }
        // ends the session on the server too; failing to do so changes nothing here
        if (key != null) runCatching { post("logout", JSONObject().put("type", "Logout").put("authKey", key)) }
    }

    // ---- add-ons ----

    private suspend fun remoteAddons(key: String): List<JSONObject> {
        val result = post("addonCollectionGet", JSONObject().put("type", "AddonCollectionGet").put("authKey", key).put("update", false))
            .optJSONObject("result") ?: return emptyList()
        val arr = result.optJSONArray("addons") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
    }

    private fun descriptorOf(a: Addon): JSONObject {
        val manifest = runCatching { JSONObject(a.rawManifest) }.getOrNull() ?: JSONObject()
        return JSONObject().put("manifest", manifest).put("transportUrl", StremioProtocol.manifestUrl(a.baseUrl))
            .put("flags", JSONObject().put("official", a.official).put("protected", a.protected))
    }

    /** What the account has is merged into the installed add-ons; add-ons only found here are sent to the account */
    private suspend fun syncAddons(key: String): Int {
        val remote = remoteAddons(key)
        val fromAccount = remote.mapNotNull { d ->
            val manifestObject = d.optJSONObject("manifest") ?: return@mapNotNull null
            val raw = manifestObject.toString()
            val manifest = StremioProtocol.parseManifest(raw) ?: return@mapNotNull null
            val base = StremioProtocol.baseUrl(d.optString("transportUrl")) ?: return@mapNotNull null
            val flags = d.optJSONObject("flags")
            Addon(base, manifest, raw, flags?.optBoolean("official") ?: false, flags?.optBoolean("protected") ?: false)
        }
        val before = addons.addons.value.size
        addons.upsert(fromAccount)
        val local = addons.addons.value
        val onlyLocal = local.filter { l -> remote.none { StremioProtocol.baseUrl(it.optString("transportUrl")) == l.baseUrl } }
        if (onlyLocal.isNotEmpty()) pushAddons(key, remote)
        return addons.addons.value.size - before
    }

    /** Sets the account's add-ons to everything the account already has plus what is installed here (never fewer) */
    private suspend fun pushAddons(key: String, remote: List<JSONObject>, removed: String? = null) {
        val list = JSONArray()
        val seen = HashSet<String>()
        // the account's own entries first, as they are (a removed one is left out unless the account protects it)
        for (d in remote) {
            val base = StremioProtocol.baseUrl(d.optString("transportUrl"))
            val protectedFlag = d.optJSONObject("flags")?.optBoolean("protected") ?: false
            if (base != null && base == removed && !protectedFlag) continue
            list.put(d)
            if (base != null) seen += base
        }
        addons.addons.value.filter { it.baseUrl !in seen && it.baseUrl != removed }.forEach { list.put(descriptorOf(it)) }
        post("addonCollectionSet", JSONObject().put("type", "AddonCollectionSet").put("authKey", key).put("addons", list))
    }

    /** Called after the user removed an add-on here: it is removed from the account as well */
    suspend fun addonRemoved(baseUrl: String) {
        val key = authKey() ?: return
        mutex.withLock { runCatching { pushAddons(key, remoteAddons(key), removed = baseUrl) } }
    }

    /** Called after the user added an add-on here */
    suspend fun addonAdded() {
        val key = authKey() ?: return
        mutex.withLock { runCatching { pushAddons(key, remoteAddons(key)) } }
    }

    // ---- library and continue watching ----

    private suspend fun remoteLibrary(key: String, ids: List<String> = emptyList()): List<JSONObject> {
        val body = JSONObject().put("authKey", key).put("collection", "libraryItem").put("ids", JSONArray(ids)).put("all", ids.isEmpty())
        val arr = post("datastoreGet", body).optJSONArray("result") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
    }

    private fun millis(s: String?): Long = runCatching { Instant.parse(s).toEpochMilli() }.getOrDefault(0L)

    private fun nullIfBlank(s: String?): String? = s?.takeIf { it.isNotBlank() && it != "null" }

    private suspend fun syncLibrary(key: String) {
        val items = remoteLibrary(key)
        val saved = ArrayList<LibraryEntry>()
        val watching = ArrayList<WatchProgress>()
        val removed = HashMap<String, Long>()
        val watched = HashMap<String, StremioWatched>()
        for (o in items) {
            val id = o.optString("_id")
            val type = o.optString("type")
            if (id.isBlank() || type == "other") continue
            val poster = nullIfBlank(o.optString("poster"))
            val state = o.optJSONObject("state")
            if (state != null) watched[id] = StremioWatched(
                movie = type == "movie" && (state.optInt("timesWatched") > 0 || state.optInt("flaggedWatched") > 0),
                videos = nullIfBlank(state.optString("watched")),
            )
            val isRemoved = o.optBoolean("removed")
            val temp = o.optBoolean("temp")
            val mtime = millis(o.optString("_mtime"))
            if (isRemoved) removed[id] = mtime
            else if (!temp) saved += LibraryEntry(type, id, o.optString("name"), poster, millis(nullIfBlank(o.optString("_ctime"))).takeIf { it > 0 } ?: mtime)
            if ((!isRemoved || temp) && (state?.optLong("timeOffset") ?: 0L) > 0L) {
                val video = nullIfBlank(state?.optString("video_id")) ?: id
                watching += WatchProgress(
                    type, id, o.optString("name"), poster, video, video.takeIf { it != id }.orEmpty(),
                    millis(nullIfBlank(state?.optString("lastWatched"))).takeIf { it > 0 } ?: mtime, null,
                )
            }
        }
        library.mergeRemote(saved, watching, removed)
        _watched.value = watched
        // titles saved only here go to the account
        val remoteIds = items.map { it.optString("_id") }.toSet()
        library.library.value.filter { it.id !in remoteIds }.forEach { e ->
            runCatching { putItem(key, e.type, e.id, e.name, e.poster, removed = false, videoId = null, watched = false, ctime = e.addedAt) }
        }
    }

    /** Writes one title to the account's library, changing only what changed: the rest of an existing item is kept */
    private suspend fun putItem(
        key: String, type: String, id: String, name: String, poster: String?,
        removed: Boolean, videoId: String?, watched: Boolean, ctime: Long? = null,
    ) {
        val now = Instant.now().toString()
        val item = remoteLibrary(key, listOf(id)).firstOrNull() ?: JSONObject()
            .put("_id", id).put("name", name).put("type", type).put("poster", poster ?: JSONObject.NULL).put("posterShape", "poster")
            .put("temp", false).put("_ctime", Instant.ofEpochMilli(ctime ?: System.currentTimeMillis()).toString())
            .put("state", JSONObject().put("lastWatched", JSONObject.NULL).put("timeWatched", 0).put("timeOffset", 0).put("overallTimeWatched", 0)
                .put("timesWatched", 0).put("flaggedWatched", 0).put("duration", 0).put("video_id", JSONObject.NULL).put("watched", JSONObject.NULL).put("noNotif", false))
            .put("behaviorHints", JSONObject().put("defaultVideoId", JSONObject.NULL).put("featuredVideoId", JSONObject.NULL).put("hasScheduledVideos", false))
        item.put("removed", removed).put("_mtime", now)
        if (watched) {
            val state = item.optJSONObject("state") ?: JSONObject().also { item.put("state", it) }
            state.put("lastWatched", now)
            // Stremio shows a title in continue watching when its offset is more than zero; the real position is not known here
            if (state.optLong("timeOffset") <= 0L) state.put("timeOffset", 1)
            if (videoId != null) state.put("video_id", videoId)
            item.put("removed", false)
        }
        post("datastorePut", JSONObject().put("authKey", key).put("collection", "libraryItem").put("changes", JSONArray().put(item)))
    }

    /** After the user saved or removed a title here */
    suspend fun saveChanged(type: String, id: String, name: String, poster: String?, removed: Boolean) {
        val key = authKey() ?: return
        mutex.withLock { runCatching { putItem(key, type, id, name, poster, removed, null, false) } }
    }

    /** After something was played here */
    suspend fun played(p: WatchProgress) {
        val key = authKey() ?: return
        mutex.withLock { runCatching { putItem(key, p.type, p.id, p.name, p.poster, false, p.videoId, true) } }
    }

    // ---- all of it ----

    /** Brings add-ons, library and continue watching in step with the account; the result tells how many add-ons were new here */
    suspend fun syncAll(): Result<Int> {
        val key = authKey() ?: return Result.failure(IllegalStateException("Not signed in"))
        _busy.value = true
        return try {
            mutex.withLock {
                runCatching {
                    val added = syncAddons(key)
                    syncLibrary(key)
                    added
                }
            }
        } finally {
            _busy.value = false
        }
    }

    companion object {
        private const val API = "https://api.strem.io/api"
    }
}
