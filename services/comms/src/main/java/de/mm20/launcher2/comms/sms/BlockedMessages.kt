package de.mm20.launcher2.comms.sms

import android.content.Context
import de.mm20.launcher2.comms.PhoneNumbers
import de.mm20.launcher2.comms.repository.SpamRepository
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** A message from a blocked number: kept by Telos, neither notified nor shown in the conversations. */
data class BlockedMessage(
    val id: Long,
    val address: String,
    val body: String,
    val date: Long,
    val mms: Boolean,
    val attachments: List<SmsAttachment>,
)

/**
 * Incoming messages of blocked numbers (the block list is the one of the call blocking, so a number
 * blocked for calls is blocked for messages and the other way round). They are kept in Telos'
 * private storage; unblocking puts them back into the message store.
 */
object BlockedMessages {
    private const val PREFS = "telos_blocked_messages"
    private const val KEY = "items"
    private const val MAX = 500

    fun list(context: Context): List<BlockedMessage> = runCatching {
        val arr = JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]"))
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            val att = o.optJSONArray("att") ?: JSONArray()
            BlockedMessage(
                o.getLong("id"), o.getString("a"), o.getString("b"), o.getLong("d"), o.optBoolean("m"),
                (0 until att.length()).map { i -> att.getJSONObject(i).let { p -> SmsAttachment(p.getString("u"), p.getString("t")) } },
            )
        }.sortedByDescending { it.date }
    }.getOrDefault(emptyList())

    fun count(context: Context): Int = list(context).size

    @Synchronized
    private fun persist(context: Context, items: List<BlockedMessage>) {
        val arr = JSONArray()
        items.forEach { m ->
            arr.put(
                JSONObject().put("id", m.id).put("a", m.address).put("b", m.body).put("d", m.date).put("m", m.mms)
                    .put("att", JSONArray().also { a -> m.attachments.forEach { a.put(JSONObject().put("u", it.uri).put("t", it.mimeType)) } })
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
    }

    private fun dir(context: Context) = File(context.filesDir, "blocked_mms").apply { mkdirs() }

    /**
     * True (and the message is kept here) when [address] is blocked. The caller then neither
     * stores the message in the system nor notifies.
     */
    suspend fun interceptIfBlocked(
        context: Context, spam: SpamRepository, address: String, body: String, date: Long, parts: List<MmsPart>? = null,
    ): Boolean {
        val blocked = runCatching { spam.isNumberBlocked(address) }.getOrDefault(false)
        if (!blocked) return false
        val id = System.nanoTime()
        val attachments = parts.orEmpty().filter { !it.contentType.startsWith("text/") && !it.contentType.contains("smil") }
            .mapIndexedNotNull { i, part ->
                runCatching {
                    val file = File(dir(context), "${id}_$i")
                    file.writeBytes(part.data)
                    SmsAttachment(file.toURI().toString(), part.contentType)
                }.getOrNull()
            }
        synchronized(this) {
            val all = (list(context) + BlockedMessage(id, address, body, date, parts != null, attachments)).sortedBy { it.date }
            val kept = all.takeLast(MAX)
            (all - kept.toSet()).forEach { dropFiles(it) }
            persist(context, kept)
        }
        return true
    }

    private fun dropFiles(m: BlockedMessage) {
        m.attachments.forEach { runCatching { File(java.net.URI(it.uri)).delete() } }
    }

    @Synchronized
    fun delete(context: Context, id: Long) {
        val all = list(context)
        all.filter { it.id == id }.forEach { dropFiles(it) }
        persist(context, all.filter { it.id != id })
    }

    /**
     * [address] was unblocked: its kept messages go into the message store (Telos must be the default
     * SMS app for that; otherwise they stay in the list). No notification is shown for them.
     */
    @Synchronized
    fun restore(context: Context, address: String) {
        if (!SmsRole.isDefault(context)) return
        val all = list(context)
        val mine = all.filter { PhoneNumbers.match(it.address, address) }
        val restored = mutableSetOf<Long>()
        for (m in mine.sortedBy { it.date }) {
            val ok = if (!m.mms) {
                SmsStore.insertInbox(context, m.address, m.body, m.date) != null
            } else {
                val parts = mutableListOf<MmsPart>()
                if (m.body.isNotBlank()) parts += MmsPart("text/plain", "text.txt", m.body.toByteArray(Charsets.UTF_8))
                m.attachments.forEachIndexed { i, a ->
                    runCatching { File(java.net.URI(a.uri)).readBytes() }.getOrNull()?.let { parts += MmsPart(a.mimeType, "part$i", it) }
                }
                MmsStore.insertIncoming(context, MmsMessage(m.address, null, m.date / 1000, parts), null, m.address) != null
            }
            if (ok) { restored += m.id; dropFiles(m) }
        }
        persist(context, all.filter { it.id !in restored })
    }
}
