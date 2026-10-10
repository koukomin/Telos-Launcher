package de.mm20.launcher2.comms.tv

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.DecodeSequenceMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.decodeToSequence
import kotlinx.serialization.json.intOrNull
import java.io.InputStream

/**
 * Turns the JSON files of the iptv-org API into a [TvIndex]. Tolerant by design: every entry is read
 * field by field, fields of an unexpected type count as missing, unknown fields are ignored and a broken
 * entry is skipped, so a change of the schema never throws (it can at worst yield fewer channels).
 * The files are read as streams, one entry at a time, to keep the memory use small.
 */
object TvCatalogParser {

    /** The raw files; every file except streams may be missing. */
    class Sources(
        val channels: () -> InputStream?,
        val streams: () -> InputStream?,
        val logos: () -> InputStream? = { null },
        val feeds: () -> InputStream? = { null },
        val blocklist: () -> InputStream? = { null },
        val categories: () -> InputStream? = { null },
    )

    fun fromStrings(
        channels: String?, streams: String?, logos: String? = null, feeds: String? = null,
        blocklist: String? = null, categories: String? = null,
    ): TvIndex {
        fun s(t: String?): () -> InputStream? = { t?.byteInputStream(Charsets.UTF_8) }
        return parse(Sources(s(channels), s(streams), s(logos), s(feeds), s(blocklist), s(categories)))
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @OptIn(ExperimentalSerializationApi::class)
    private fun forEachObject(open: () -> InputStream?, block: (JsonObject) -> Unit) {
        val stream = runCatching { open() }.getOrNull() ?: return
        stream.use { input ->
            try {
                for (element in json.decodeToSequence(input, JsonElement.serializer(), DecodeSequenceMode.ARRAY_WRAPPED)) {
                    val obj = element as? JsonObject ?: continue
                    runCatching { block(obj) }
                }
            } catch (_: Exception) {
                // a truncated or malformed file: keep what was read
            }
        }
    }

    fun parse(src: Sources): TvIndex {
        // streams first: only channels that can be played are kept
        val streamsByChannel = HashMap<String, MutableList<TvStream>>()
        forEachObject(src.streams) { o ->
            val channel = o.str("channel") ?: return@forEachObject
            val url = TvUrls.sanitize(o.str("url")) ?: return@forEachObject
            if (!isPublicHost(url)) return@forEachObject
            val list = streamsByChannel.getOrPut(channel) { ArrayList(2) }
            if (list.none { it.url == url }) {
                list.add(
                    TvStream(
                        url = url,
                        quality = o.str("quality").orEmpty().take(16),
                        userAgent = o.str("user_agent").orEmpty().take(256),
                        referrer = TvUrls.sanitize(o.str("referrer")).orEmpty(),
                        title = o.str("title").orEmpty().take(120),
                    )
                )
            }
        }

        val blocked = HashSet<String>()
        forEachObject(src.blocklist) { o -> o.str("channel")?.let { blocked.add(it) } }

        val languagesByChannel = HashMap<String, MutableList<String>>()
        forEachObject(src.feeds) { o ->
            val ch = o.str("channel") ?: return@forEachObject
            if (ch !in streamsByChannel) return@forEachObject
            val list = languagesByChannel.getOrPut(ch) { ArrayList(2) }
            val langs = o.strList("languages").map { it.lowercase() }.filter { it.length in 2..3 }
            // the main feed's languages first
            if (o.bool("is_main") == true) list.addAll(0, langs.filter { it !in list }) else list.addAll(langs.filter { it !in list })
        }

        val logos = HashMap<String, Pair<Int, String>>()
        forEachObject(src.logos) { o ->
            val ch = o.str("channel") ?: return@forEachObject
            if (ch !in streamsByChannel) return@forEachObject
            val url = TvUrls.sanitize(o.str("url")) ?: return@forEachObject
            val format = o.str("format")?.uppercase().orEmpty()
            val width = o.int("width") ?: 0
            // SVG is last choice (the image loader of the app may not draw it); a medium size beats a huge one
            var score = if (format == "SVG" || url.endsWith(".svg", ignoreCase = true)) 0 else 1000
            score += if (width in 1..600) width else if (width > 600) 300 else 100
            if (o.str("feed") == null) score += 50
            val old = logos[ch]
            if (old == null || score > old.first) logos[ch] = score to url
        }

        val categoryNames = HashMap<String, String>()
        forEachObject(src.categories) { o ->
            val id = o.str("id") ?: return@forEachObject
            val name = o.str("name") ?: return@forEachObject
            categoryNames[id.lowercase()] = name
        }

        val channels = ArrayList<TvChannel>()
        forEachObject(src.channels) { o ->
            val id = o.str("id") ?: return@forEachObject
            val streams = streamsByChannel[id] ?: return@forEachObject
            if (id in blocked) return@forEachObject
            if (o.bool("is_nsfw") == true) return@forEachObject
            if (!o.str("closed").isNullOrBlank() || !o.str("replaced_by").isNullOrBlank()) return@forEachObject
            val categories = o.strList("categories").map { it.lowercase() }.distinct()
            if ("xxx" in categories) return@forEachObject
            val name = o.str("name")?.trim()?.takeIf { it.isNotEmpty() } ?: return@forEachObject
            // older schemas had languages and logo in the channel itself
            val languages = (languagesByChannel[id].orEmpty() + o.strList("languages").map { it.lowercase() })
                .filter { it.length in 2..3 }.distinct()
            val logo = logos[id]?.second ?: TvUrls.sanitize(o.str("logo")).orEmpty()
            channels.add(
                TvChannel(
                    id = id,
                    name = name.take(TvLimits.MAX_NAME),
                    altNames = o.strList("alt_names").map { it.trim() }.filter { it.isNotEmpty() }.take(10),
                    country = TvIndex.normalizeCountry(o.str("country").orEmpty()),
                    languages = languages,
                    categories = categories,
                    logoUrl = logo,
                    streams = streams.sortedByDescending { qualityRank(it.quality) },
                )
            )
        }
        return TvIndex(channels, categoryNames)
    }

    /** 1080p > 720p > 576p > 480p..., interlaced counts the same; unknown quality ranks in the middle */
    fun qualityRank(quality: String): Int {
        val digits = quality.takeWhile { it.isDigit() }
        val h = digits.toIntOrNull() ?: return 500
        // 4K streams are tried just after 1080p: they are heavy for phones
        return if (h > 1080) 1079 else h
    }

    private fun isPublicHost(url: String): Boolean =
        de.mm20.launcher2.comms.media.video.Http.isPublicWeb(url)

    private fun JsonObject.prim(key: String): JsonPrimitive? = this[key] as? JsonPrimitive
    private fun JsonObject.str(key: String): String? {
        val p = prim(key) ?: return null
        if (!p.isString) return null
        return p.content.takeIf { it.isNotBlank() }
    }
    private fun JsonObject.bool(key: String): Boolean? = prim(key)?.booleanOrNull
    private fun JsonObject.int(key: String): Int? = prim(key)?.intOrNull
    private fun JsonObject.strList(key: String): List<String> =
        (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }.orEmpty()
}
