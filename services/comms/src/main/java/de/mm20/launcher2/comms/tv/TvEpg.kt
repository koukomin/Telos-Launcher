package de.mm20.launcher2.comms.tv

import android.content.Context
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.FileInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.PushbackInputStream
import java.util.zip.GZIPInputStream

/** One programme of the guide. Times are epoch millis. */
data class TvProgramme(
    val start: Long,
    val stop: Long,
    val title: String,
    /** at most 300 characters, may be empty */
    val description: String = "",
    val category: String = "",
)

/** What is on now and what follows; either may be null (nothing known for that moment) */
data class TvProgrammes(val current: TvProgramme?, val next: TvProgramme?)

/** The programmes of one channel as compact parallel arrays, sorted by start */
class TvChannelProgrammes(
    private val starts: LongArray,
    private val stops: LongArray,
    private val titles: Array<String>,
    private val descriptions: Array<String>,
    private val categories: Array<String>,
) {
    val size: Int get() = starts.size

    private fun at(i: Int) = TvProgramme(starts[i], stops[i], titles[i], descriptions[i], categories[i])

    /** The programme that is on at [now] (the latest started one if some overlap) and the next one starting after it; null when neither is known */
    fun nowNext(now: Long): TvProgrammes? {
        var cur = -1
        for (i in starts.indices) {
            if (starts[i] <= now && now < stops[i]) cur = i
            if (starts[i] > now) break
        }
        val after = if (cur >= 0) stops[cur] else now
        var nxt = -1
        for (i in starts.indices) {
            if (i != cur && starts[i] >= after && starts[i] > now) { nxt = i; break }
        }
        if (cur < 0 && nxt < 0) return null
        return TvProgrammes(if (cur >= 0) at(cur) else null, if (nxt >= 0) at(nxt) else null)
    }

    companion object {
        fun of(list: List<TvProgramme>): TvChannelProgrammes {
            val s = list.sortedBy { it.start }.distinctBy { it.start }
            return TvChannelProgrammes(
                LongArray(s.size) { s[it].start }, LongArray(s.size) { s[it].stop },
                Array(s.size) { s[it].title }, Array(s.size) { s[it].description }, Array(s.size) { s[it].category },
            )
        }
    }
}

/** Streaming XMLTV reader. Pure apart from the [XmlPullParser] it is given. */
object XmltvParser {
    const val MAX_DESC = 300
    const val KEEP_PAST_MS = 60L * 60 * 1000
    const val KEEP_AHEAD_MS = 36L * 60 * 60 * 1000
    const val MAX_PER_CHANNEL = 300
    private const val MAX_NAMES = 20

    /**
     * Reads programmes that end after now-1h and start before now+36h. [resolve] maps an XMLTV channel
     * id and its display names to a catalog channel id (or null to ignore the channel). A parse error
     * ends the reading; what was read so far is returned.
     */
    fun parse(
        p: XmlPullParser, now: Long, resolve: (xmltvId: String, names: List<String>) -> String?,
    ): Map<String, TvChannelProgrammes> {
        val names = HashMap<String, List<String>>()
        val resolved = HashMap<String, String?>()
        val acc = HashMap<String, ArrayList<TvProgramme>>()
        try {
            var e = p.eventType
            while (e != XmlPullParser.END_DOCUMENT) {
                if (e == XmlPullParser.START_TAG) {
                    when (p.name) {
                        "channel" -> readChannel(p, names)
                        "programme" -> readProgramme(p, now, resolve, names, resolved, acc)
                    }
                }
                e = p.next()
            }
        } catch (_: Exception) {
            // truncated or malformed: keep what was read
        }
        return acc.mapValues { TvChannelProgrammes.of(it.value) }
    }

    private fun readChannel(p: XmlPullParser, names: HashMap<String, List<String>>) {
        val id = p.getAttributeValue(null, "id")?.trim().orEmpty()
        val list = ArrayList<String>(2)
        while (true) {
            val e = p.next()
            if (e == XmlPullParser.END_DOCUMENT || (e == XmlPullParser.END_TAG && p.name == "channel")) break
            if (e == XmlPullParser.START_TAG && p.name == "display-name") {
                val t = p.nextText().trim()
                if (t.isNotEmpty() && list.size < MAX_NAMES) list.add(t.take(TvLimits.MAX_NAME))
            }
        }
        if (id.isNotEmpty() && names.size < 20_000) names[id] = list
    }

    private fun readProgramme(
        p: XmlPullParser, now: Long, resolve: (String, List<String>) -> String?,
        names: Map<String, List<String>>, resolved: HashMap<String, String?>,
        acc: HashMap<String, ArrayList<TvProgramme>>,
    ) {
        val xmlId = p.getAttributeValue(null, "channel")?.trim().orEmpty()
        val start = parseTime(p.getAttributeValue(null, "start").orEmpty())
        var stop = parseTime(p.getAttributeValue(null, "stop").orEmpty())
        val target = if (xmlId.isEmpty()) null else run {
            if (!resolved.containsKey(xmlId)) resolved[xmlId] = resolve(xmlId, names[xmlId].orEmpty())
            resolved[xmlId]
        }
        if (start != null && stop == null) stop = start + 30 * 60_000L
        val wanted = target != null && start != null && stop != null && stop > start &&
            stop > now - KEEP_PAST_MS && start < now + KEEP_AHEAD_MS
        var title = ""
        var desc = ""
        var cat = ""
        while (true) {
            val e = p.next()
            if (e == XmlPullParser.END_DOCUMENT || (e == XmlPullParser.END_TAG && p.name == "programme")) break
            if (!wanted || e != XmlPullParser.START_TAG) continue
            when (p.name) {
                "title" -> { val t = p.nextText().trim(); if (title.isEmpty()) title = t.take(TvLimits.MAX_NAME) }
                "desc" -> { val t = p.nextText().trim(); if (desc.isEmpty()) desc = t.take(MAX_DESC) }
                "category" -> { val t = p.nextText().trim(); if (cat.isEmpty()) cat = t.take(TvLimits.MAX_GROUP) }
            }
        }
        if (!wanted || title.isEmpty()) return
        val list = acc.getOrPut(target!!) { ArrayList() }
        if (list.size < MAX_PER_CHANNEL) list.add(TvProgramme(start!!, stop!!, title, desc, cat))
    }

    /**
     * XMLTV time "yyyyMMddHHmmss +0200" (seconds, minutes and the zone may be missing; no zone means UTC)
     * to epoch millis; null when it is not a time.
     */
    fun parseTime(raw: String): Long? {
        val s = raw.trim()
        var i = 0
        while (i < s.length && s[i].isDigit()) i++
        if (i < 8 || i > 14 || i % 2 != 0) return null
        val d = s.substring(0, i)
        fun num(from: Int, len: Int) = if (from + len <= d.length) d.substring(from, from + len).toInt() else 0
        val y = num(0, 4); val mo = num(4, 2); val da = num(6, 2)
        val h = num(8, 2); val mi = num(10, 2); val se = num(12, 2)
        if (mo !in 1..12 || da !in 1..31 || h > 23 || mi > 59 || se > 60) return null
        var offsetMin = 0
        val z = s.substring(i).trim()
        if (z.isNotEmpty() && (z[0] == '+' || z[0] == '-')) {
            val digits = z.substring(1).filter { it.isDigit() }
            if (digits.length < 2) return null
            val oh = digits.substring(0, 2).toInt()
            val om = if (digits.length >= 4) digits.substring(2, 4).toInt() else 0
            if (oh > 14 || om > 59) return null
            offsetMin = (oh * 60 + om) * if (z[0] == '-') -1 else 1
        }
        val days = daysFromCivil(y, mo, da)
        return ((days * 24 + h) * 60 + mi - offsetMin) * 60_000L + se * 1000L
    }

    /** Days since 1970-01-01 of a proleptic Gregorian date */
    private fun daysFromCivil(y0: Int, m: Int, d: Int): Long {
        val y = if (m <= 2) y0 - 1 else y0
        val era = Math.floorDiv(y, 400)
        val yoe = y - era * 400
        val doy = (153 * (m + (if (m > 2) -3 else 9)) + 2) / 5 + d - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era.toLong() * 146097 + doe - 719468
    }
}

/**
 * The optional programme guide (EPG) for Greek channels, from XMLTV files. Loaded only when TV is open
 * ([refreshEpgIfStale]), only when Greece is selected and the setting is on, at most every 6 hours
 * (conditional GET, disk cache). Programmes are kept in memory for channels of the TV index only, from
 * one hour ago to 36 hours ahead. Any failure is silent: the guide is simply empty.
 */
class TvEpg(
    context: Context,
    private val settings: TvSettings,
    private val catalog: TvCatalog,
    private val cache: TvExtraCache,
) {
    @Suppress("unused") private val app = context.applicationContext
    private val mutex = Mutex()

    @Volatile private var data: Map<String, TvChannelProgrammes> = emptyMap()
    private var loadedKey: String? = null
    private val _version = MutableStateFlow(0)

    /** Increases whenever the guide data changed; collect it to refresh the "now / next" texts */
    val epgVersion: StateFlow<Int> get() = _version

    /** True when the guide may be used: setting on and Greece among the selected countries */
    fun isActive(): Boolean = settings.epgEnabled.value && settings.greeceSelected()

    /** What is on at [now] and what comes next on the catalog channel [channelId]; null when the guide knows nothing */
    fun nowNext(channelId: String, now: Long = System.currentTimeMillis()): TvProgrammes? {
        if (!isActive()) return null
        return data[channelId]?.nowNext(now)
    }

    /** Loads the cached guide and, when the last check is older than 6 hours, updates it. Silent on failure. */
    suspend fun refreshEpgIfStale() {
        if (!isActive()) return
        mutex.withLock {
            try {
                val index = catalog.index.value ?: return
                val changed = withContext(Dispatchers.IO) { refresh(index, System.currentTimeMillis()) }
                if (changed) _version.value = _version.value + 1
            } catch (_: Exception) {
            }
        }
    }

    private fun refresh(index: TvIndex, now: Long): Boolean {
        for ((i, url) in URLS.withIndex()) {
            val key = "epg$i.gz"
            val due = now - cache.okAt(key) >= REFRESH_MS
            var file = cache.usable(key, now)
            var updated = false
            if (due || file == null) {
                val outcome = cache.fetch(key, url, MAX_COMPRESSED, "application/gzip, application/xml, */*")
                if (outcome == TvExtraCache.Outcome.FAILED && file == null) continue
                updated = outcome == TvExtraCache.Outcome.UPDATED
                file = cache.usable(key, now) ?: continue
            }
            if (loadedKey == key && !updated && !due) return false
            val f = file ?: continue
            val parsed = runCatching { parseFile(f, index, now) }.getOrNull().orEmpty()
            if (parsed.isNotEmpty()) {
                data = parsed
                loadedKey = key
                return true
            }
        }
        return false
    }

    private fun parseFile(file: File, index: TvIndex, now: Long): Map<String, TvChannelProgrammes> {
        val resolver = resolver(index)
        FileInputStream(file).use { raw ->
            val pb = PushbackInputStream(raw, 2)
            val b0 = pb.read()
            val b1 = pb.read()
            if (b1 >= 0) pb.unread(b1)
            if (b0 >= 0) pb.unread(b0)
            val gz = b0 == 0x1f && b1 == 0x8b
            val plain: InputStream = if (gz) GZIPInputStream(pb) else pb
            CappedInputStream(plain, MAX_UNCOMPRESSED).use { input ->
                val parser = Xml.newPullParser()
                parser.setInput(input, null)
                return XmltvParser.parse(parser, now, resolver)
            }
        }
    }

    private class CappedInputStream(input: InputStream, private val limit: Long) : FilterInputStream(input) {
        private var count = 0L
        override fun read(): Int {
            val b = super.read()
            if (b >= 0 && ++count > limit) throw IOException("too big")
            return b
        }
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = super.read(b, off, len)
            if (n > 0) { count += n; if (count > limit) throw IOException("too big") }
            return n
        }
    }

    companion object {
        val URLS = listOf(
            "https://ext.greektv.app/epg/epg.xml.gz",
            "https://epgshare01.online/epgshare01/epg_ripper_GR1.xml.gz",
        )
        const val REFRESH_MS = 6L * 60 * 60 * 1000
        const val MAX_COMPRESSED = 25L * 1024 * 1024
        const val MAX_UNCOMPRESSED = 150L * 1024 * 1024

        /** XMLTV channel id to catalog channel id: the id itself ("ERT1.gr"), else the normalized display name */
        fun resolver(index: TvIndex): (String, List<String>) -> String? {
            val byKey = HashMap<String, String>()
            for (c in index.all) {
                if (c.country != "GR") continue
                for (n in listOf(c.name) + c.altNames) TvExtraMerge.nameKey(n).takeIf { it.length >= 2 }?.let { byKey.putIfAbsent(it, c.id) }
            }
            return { id, names ->
                index.channel(id)?.id
                    ?: index.channel(id.substringBefore('@'))?.id
                    ?: names.firstNotNullOfOrNull { n -> byKey[TvExtraMerge.nameKey(n)] }
                    ?: byKey[TvExtraMerge.nameKey(id)]
            }
        }
    }
}
