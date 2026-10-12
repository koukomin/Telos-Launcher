package de.mm20.launcher2.comms.tv

import de.mm20.launcher2.comms.media.video.Http
import de.mm20.launcher2.comms.search.GreekText
import de.mm20.launcher2.comms.search.TelosSearch
import java.security.MessageDigest

/**
 * Merges the optional extra Greek playlists into the iptv-org [TvIndex] without duplicating channels.
 * Pure (no Android, no network), so it can be tested.
 *
 * An entry is matched to an existing channel by tvg-id (equal to the iptv-org channel id), then by
 * normalized name ([nameKey]: accents, case, Greek/Greeklish, "HD"/"TV" suffixes ignored), then by a
 * strict mutual [TelosSearch] match among the Greek channels (only when exactly one channel fits).
 * A match adds the entry's address as an additional stream AFTER the existing ones. An entry without a
 * match becomes an extra channel (id "extra:<hash>", country GR, [TvChannel.isExtra]).
 */
object TvExtraMerge {
    const val LABEL_FREE_TV = "Free-TV"
    const val LABEL_GREEKTV = "greektvm3u"
    const val EXTRA_PREFIX = "extra:"
    /** At most this many extra channels, as a guard against a broken or hostile playlist */
    const val MAX_EXTRA_CHANNELS = 1000

    /** The parsed entries of one playlist and the label that marks its streams */
    class Source(val label: String, val entries: List<TvM3uEntry>)

    class Extras(val sources: List<Source>) {
        val isEmpty: Boolean get() = sources.all { it.entries.isEmpty() }
    }

    private val SUFFIX_WORDS = setOf("hd", "sd", "fhd", "uhd", "4k", "tv", "τιβι")
    private val BRACKETS = Regex("""\([^)]*\)|\[[^]]*]""")
    private val SPLIT = Regex("""[^\p{L}\p{N}]+""")

    /** Normalized comparison key of a channel name; empty when nothing is left */
    fun nameKey(name: String): String {
        val folded = GreekText.fold(name.replace(BRACKETS, " "))
        val words = folded.split(SPLIT).filter { it.isNotEmpty() }.toMutableList()
        while (words.size > 1 && words.last() in SUFFIX_WORDS) words.removeAt(words.size - 1)
        val key = GreekText.greekToLatin(words.joinToString(""))
        // "AlphaTV", "SkaiTV", "OpenTV" are the same channels as "Alpha", "Skai", "Open"
        return if (key.length >= 6 && key.endsWith("tv")) key.dropLast(2) else key
    }

    /** Catalog category ids for a (Greek or English) playlist group; empty when not mappable */
    fun categoriesFor(group: String): List<String> {
        val g = GreekText.fold(group)
        if (g.isBlank()) return emptyList()
        val out = ArrayList<String>(2)
        fun has(vararg k: String) = k.any { it in g }
        if (has("ειδησ", "news")) out.add("news")
        if (has("αθλητ", "sport")) out.add("sports")
        if (has("παιδ", "kids", "children", "cartoon")) out.add("kids")
        if (has("μουσικ", "music")) out.add("music")
        if (has("ταινι", "movie", "cinema", "σινεμα")) out.add("movies")
        if (has("ντοκιμαντερ", "documentar")) out.add("documentary")
        if (has("θρησκ", "religio")) out.add("religious")
        if (has("οικονομ", "business")) out.add("business")
        if (has("ψυχαγωγ", "entertainment")) out.add("entertainment")
        return out
    }

    /** "MEGA COSMOS" becomes "Mega Cosmos"; short words (ERT, ANT1, TV) and names that are not all upper case stay */
    fun prettyName(name: String): String {
        val n = name.trim()
        if (n.none { it.isLetter() } || n.any { it.isLowerCase() }) return n
        return n.split(' ').joinToString(" ") { w ->
            if (w.length > 3 && w.all { it.isLetter() }) w.lowercase().replaceFirstChar { it.uppercase() } else w
        }
    }

    /** Stable id of an extra channel from its name key */
    fun extraId(key: String): String {
        val d = MessageDigest.getInstance("SHA-1").digest(key.toByteArray(Charsets.UTF_8))
        return EXTRA_PREFIX + d.take(6).joinToString("") { "%02x".format(it) }
    }

    private class Pending(val key: String) {
        var name = ""
        var logo = ""
        val categories = LinkedHashSet<String>()
        val streams = ArrayList<TvStream>()
    }

    fun merge(base: TvIndex, extras: Extras, bad: Map<String, Long>, now: Long): TvIndex {
        if (extras.isEmpty) return base
        val greek = base.all.filter { it.country == "GR" }
        val byKey = HashMap<String, TvChannel>()
        for (c in greek) {
            for (n in listOf(c.name) + c.altNames) nameKey(n).takeIf { it.length >= 2 }?.let { byKey.putIfAbsent(it, c) }
        }
        val known = HashSet<String>()
        val knownGreek = HashSet<String>()
        for (c in base.all) for (s in c.streams) { known.add(s.url); if (c.country == "GR") knownGreek.add(s.url) }

        val added = LinkedHashMap<String, MutableList<TvStream>>()
        val pendings = LinkedHashMap<String, Pending>()
        val fuzzyCache = HashMap<String, TvChannel?>()

        fun fuzzy(name: String, key: String): TvChannel? {
            if (key.length < 3) return null
            return fuzzyCache.getOrPut(key) {
                val hits = greek.filter { c ->
                    TelosSearch.matches(name, c.name) && TelosSearch.matches(c.name, name)
                }
                hits.singleOrNull()
            }
        }

        for (source in extras.sources) {
            for (e in source.entries) {
                if (!Http.isPublicWeb(e.url) || TvUrls.sanitize(e.url) == null) continue
                val label = e.tvgName.ifBlank { e.name }
                val key = nameKey(e.name).ifEmpty { nameKey(label) }
                val stream = TvStream(url = e.url, title = source.label, source = source.label)
                val match = e.tvgId.takeIf { it.isNotBlank() }?.let { base.channel(it) }
                    ?: byKey[key]
                    ?: byKey[nameKey(label)]
                    ?: fuzzy(e.name, key)
                if (match != null) {
                    if (e.url in known) continue
                    known.add(e.url)
                    added.getOrPut(match.id) { ArrayList() }.add(stream)
                } else {
                    // a stream that only exists in a channel of another country must not hide the Greek channel
                    if (key.length < 2 || e.url in known && (e.url in knownGreek || pendings.values.any { p -> p.streams.any { it.url == e.url } })) continue
                    if (pendings.size >= MAX_EXTRA_CHANNELS && key !in pendings) continue
                    known.add(e.url)
                    val p = pendings.getOrPut(key) { Pending(key) }
                    if (p.name.isEmpty()) p.name = prettyName(e.name).take(TvLimits.MAX_NAME)
                    if (p.logo.isEmpty()) p.logo = e.logo
                    p.categories.addAll(categoriesFor(e.group))
                    p.streams.add(stream)
                }
            }
        }
        if (added.isEmpty() && pendings.isEmpty()) return base

        val live = TvFailover.prune(bad, now)
        val channels = ArrayList<TvChannel>(base.size + pendings.size)
        for (c in base.all) {
            val more = added[c.id]
            channels.add(if (more == null) c else c.copy(streams = c.streams + more))
        }
        for (p in pendings.values) {
            if (p.streams.all { it.url in live }) continue
            channels.add(
                TvChannel(
                    id = extraId(p.key),
                    name = p.name,
                    country = "GR",
                    languages = listOf("ell"),
                    categories = p.categories.toList(),
                    logoUrl = p.logo,
                    streams = p.streams,
                    isExtra = true,
                )
            )
        }
        return TvIndex(channels, base.categoryNames)
    }
}
