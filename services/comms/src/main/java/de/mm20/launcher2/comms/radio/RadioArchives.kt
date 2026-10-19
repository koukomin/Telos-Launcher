package de.mm20.launcher2.comms.radio

import de.mm20.launcher2.comms.model.RadioStation
import org.json.JSONObject
import org.json.JSONArray
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Interoperates with Transistor's MIT-licensed Collection/Station JSON schema and ZIP layout.
 * Written independently: archive names are never extracted to disk. Transistor's own ZIP writer
 * prefixes entries with '/', so a leading slash is accepted; parent-directory components are not.
 */
object RadioArchives {
    const val MAX_STATIONS = 5000
    const val MAX_IMAGE_BYTES = 2 * 1024 * 1024
    private const val MAX_JSON_BYTES = 4 * 1024 * 1024
    private const val MAX_ARCHIVE_BYTES = 64 * 1024 * 1024
    private const val MAX_FILES = 10000
    data class Imported(val station: RadioStation, val image: ByteArray? = null)

    fun read(input: InputStream): List<Imported> {
        val stream = input.buffered()
        stream.mark(4)
        val zip = stream.read() == 'P'.code && stream.read() == 'K'.code
        stream.reset()
        if (!zip) return parseText(readBounded(stream, MAX_JSON_BYTES).toString(Charsets.UTF_8))
        val files = linkedMapOf<String, ByteArray>()
        var total = 0
        var count = 0
        ZipInputStream(stream).use { archive ->
            while (true) {
                val entry = archive.nextEntry ?: break
                require(++count <= MAX_FILES) { "Too many archive entries" }
                val name = archiveName(entry.name)
                if (!entry.isDirectory && name != null) {
                    val recognized = name.endsWith(".json", true) || name.endsWith(".m3u", true) ||
                        name.endsWith(".m3u8", true) || name.endsWith(".pls", true) || isImage(name)
                    // Bound every entry, including ignored files, before advancing to the next entry.
                    val bytes = readBounded(archive, if (name.endsWith(".json", true)) MAX_JSON_BYTES else MAX_IMAGE_BYTES)
                    total += bytes.size
                    require(total <= MAX_ARCHIVE_BYTES) { "Archive too large" }
                    if (recognized) files.putIfAbsent(name, bytes)
                } else if (!entry.isDirectory) {
                    throw IllegalArgumentException("Invalid archive path")
                }
                archive.closeEntry()
            }
        }
        val collection = files.entries.firstOrNull { it.key.substringAfterLast('/') == "collection.json" }
            ?: files.entries.firstOrNull { it.key.endsWith(".json", true) }
        if (collection != null) return parseJson(collection.value.toString(Charsets.UTF_8), files)
        // Transistor 3 exports one M3U and matching PNG per station.
        return files.entries.filter { it.key.endsWith(".m3u", true) || it.key.endsWith(".m3u8", true) || it.key.endsWith(".pls", true) }
            .flatMap { (path, bytes) ->
                parseText(bytes.toString(Charsets.UTF_8)).map { imported ->
                    val stem = path.substringBeforeLast('.')
                    imported.copy(image = files["$stem.png"] ?: files["$stem.jpg"])
                }
            }.distinctBy { it.station.streamUrl }.take(MAX_STATIONS)
    }

    fun parseText(text: String): List<Imported> = if (text.trimStart().startsWith("{")) {
        parseJson(text)
    } else RadioPlaylists.parse(text).take(MAX_STATIONS).map { e ->
        Imported(RadioStation("local-${UUID.randomUUID()}", e.name.ifBlank { URI(e.urls.first()).host.orEmpty() }, e.urls.first(), "", alternateStreams = e.urls.drop(1)))
    }

    fun parseJson(text: String, images: Map<String, ByteArray> = emptyMap()): List<Imported> {
        val stations = JSONObject(text).optJSONArray("stations") ?: error("No station collection")
        require(stations.length() <= MAX_STATIONS) { "Too many stations" }
        return (0 until stations.length()).mapNotNull { index ->
            val item = stations.optJSONObject(index) ?: return@mapNotNull null
            val transistor = item.optJSONArray("streamUris")
            val choices = if (transistor != null) strings(transistor).filter(::validStream) else
                (listOf(item.optString("streamUrl")) + strings(item.optJSONArray("alternateStreams"))).filter(::validStream)
            if (choices.isEmpty()) return@mapNotNull null
            val selected = item.optInt("stream", 0).coerceIn(0, choices.lastIndex)
            val urls = (listOf(choices[selected]) + choices).distinct()
            val imagePath = item.optString("imageEntry").ifBlank { item.optString("image") }
            val image = findImage(images, imagePath) ?: findImage(images, item.optString("smallImage"))
            val remote = item.optString("faviconUrl").ifBlank { item.optString("remoteImageLocation") }
            Imported(
                RadioStation(
                    id = item.optString("id").ifBlank { item.optString("uuid") }.take(200).ifBlank { "local-${UUID.randomUUID()}" },
                    name = item.optString("name").take(500).ifBlank { URI(urls.first()).host.orEmpty() },
                    streamUrl = urls.first(), faviconUrl = remote.takeIf(::validStream).orEmpty(),
                    homepage = item.optString("homepage").take(4000), streamContent = item.optString("streamContent").take(200),
                    nameManuallySet = item.optBoolean("nameManuallySet"), alternateStreams = urls.drop(1),
                    lastPlayedAt = item.optLong("lastPlayedAt", 0L).coerceAtLeast(0L),
                ), image,
            )
        }.distinctBy { it.station.streamUrl }
    }

    /** Includes a local copy of each available image; remote addresses remain a fallback. */
    fun write(stations: List<RadioStation>, output: OutputStream, image: (RadioStation) -> ByteArray?) {
        require(stations.size <= MAX_STATIONS)
        ZipOutputStream(output.buffered()).use { zip ->
            val entries = JSONArray()
            var totalImages = 0
            for (station in stations) {
                val item = JSONObject().put("id", station.id).put("name", station.name).put("streamUrl", station.streamUrl)
                    .put("faviconUrl", station.faviconUrl.takeIf(::validStream).orEmpty()).put("homepage", station.homepage)
                    .put("streamContent", station.streamContent).put("nameManuallySet", station.nameManuallySet)
                    .put("alternateStreams", JSONArray(station.alternateStreams)).put("lastPlayedAt", station.lastPlayedAt)
                val bytes = image(station)
                if (bytes != null && bytes.size <= MAX_IMAGE_BYTES && totalImages + bytes.size <= MAX_ARCHIVE_BYTES - MAX_JSON_BYTES) {
                    val name = "images/${imageKey(station.id)}.img"
                    zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry()
                    item.put("imageEntry", name)
                    totalImages += bytes.size
                }
                entries.put(item)
            }
            val json = JSONObject().put("version", 2).put("stations", entries).toString(2).toByteArray()
            require(json.size <= MAX_JSON_BYTES)
            zip.putNextEntry(ZipEntry("collection.json")); zip.write(json); zip.closeEntry()
        }
    }

    fun imageKey(id: String): String = MessageDigest.getInstance("SHA-256").digest(id.toByteArray()).take(16).joinToString("") { "%02x".format(it) }
    fun validStream(url: String): Boolean = runCatching { URI(url).let { it.scheme in listOf("http", "https") && !it.host.isNullOrBlank() && it.userInfo == null } }.getOrDefault(false)
    private fun strings(array: JSONArray?): List<String> = (0 until (array?.length() ?: 0)).map { array!!.optString(it) }.take(100)
    private fun isImage(name: String) = listOf(".png", ".jpg", ".jpeg", ".webp", ".img").any { name.endsWith(it, true) }
    private fun archiveName(name: String): String? = name.replace('\\', '/').trimStart('/').takeIf { it.length <= 1000 && it.split('/').none { part -> part == ".." || part == "." } }
    private fun findImage(images: Map<String, ByteArray>, path: String): ByteArray? {
        val clean = archiveName(path.substringAfter("://", path)) ?: return null
        if (clean.isBlank()) return null
        val exact = images[clean]
        if (exact != null && isImage(clean)) return exact
        // Transistor stores absolute Android paths in JSON, ZIP entries are relative to its files directory.
        return images.entries.filter { isImage(it.key) && clean.endsWith("/" + it.key) }.maxByOrNull { it.key.length }?.value
    }
    private fun readBounded(input: InputStream, maximum: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            require(output.size() + read <= maximum) { "Archive entry too large" }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }
}
