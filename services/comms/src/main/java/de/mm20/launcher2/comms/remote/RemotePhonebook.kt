package de.mm20.launcher2.comms.remote

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Local cache of a remote (FRITZ!Box) phonebook used to put names on callers that are not in the
 * phone's own contacts. [lookup] only reads memory/disk, it never touches the network.
 */
object RemotePhonebook {
    private const val FILE = "remote_phonebook.json"

    private var appContext: Context? = null
    @Volatile private var index: Map<String, String>? = null
    @Volatile private var syncedAt: Long = 0L

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    val lastSyncMillis: Long
        get() {
            ensureLoaded()
            return syncedAt
        }

    /** Suffix key so that +49 30 123, 030 123 and 0049 30 123 hit the same entry. */
    private fun key(number: String): String = number.filter { it.isDigit() }.takeLast(9)

    fun lookup(number: String): String? {
        if (number.isBlank()) return null
        val k = key(number)
        if (k.isEmpty()) return null
        return ensureLoaded()[k]
    }

    suspend fun sync(host: String, user: String, password: String): Result<Int> = runCatching {
        val context = appContext ?: error("RemotePhonebook not initialised")
        val contacts = FritzBoxClient(host, user, password).fetchContacts()
        val array = JSONArray()
        val map = HashMap<String, String>()
        for (c in contacts) {
            array.put(JSONObject().put("n", c.name).put("p", JSONArray(c.numbers)))
            for (number in c.numbers) {
                val k = key(number)
                if (k.isNotEmpty()) map.putIfAbsent(k, c.name)
            }
        }
        val now = System.currentTimeMillis()
        File(context.filesDir, FILE).writeText(JSONObject().put("t", now).put("c", array).toString())
        index = map
        syncedAt = now
        contacts.size
    }

    fun clear() {
        appContext?.let { File(it.filesDir, FILE).delete() }
        index = emptyMap()
        syncedAt = 0L
    }

    private fun ensureLoaded(): Map<String, String> {
        index?.let { return it }
        val context = appContext ?: return emptyMap()
        val map = HashMap<String, String>()
        runCatching {
            val file = File(context.filesDir, FILE)
            if (file.exists()) {
                val root = JSONObject(file.readText())
                syncedAt = root.optLong("t", 0L)
                val arr = root.optJSONArray("c") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val name = o.optString("n")
                    val nums = o.optJSONArray("p") ?: continue
                    for (j in 0 until nums.length()) {
                        val k = key(nums.getString(j))
                        if (k.isNotEmpty()) map.putIfAbsent(k, name)
                    }
                }
            }
        }
        index = map
        return map
    }
}
