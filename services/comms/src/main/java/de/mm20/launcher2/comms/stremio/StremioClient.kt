package de.mm20.launcher2.comms.stremio

import de.mm20.launcher2.comms.media.video.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/** Requests to add-ons over HTTP(S). Only addresses of the public internet are used (see [Http]). */
class StremioClient {

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        Http.readText(Http.open(url, USER_AGENT))
    }

    /** Reads the manifest at the address the user gave; fails with a readable message if it is not an add-on */
    suspend fun fetchAddon(input: String): Result<Addon> = runCatching {
        val base = StremioProtocol.baseUrl(input) ?: error("This is not a web address")
        val raw = get(StremioProtocol.manifestUrl(base))
        val manifest = StremioProtocol.parseManifest(raw) ?: error("This is not a Stremio add-on")
        Addon(base, manifest, raw)
    }

    suspend fun catalog(addon: Addon, catalog: AddonCatalog, extra: Map<String, String> = emptyMap()): List<MetaPreview> =
        runCatching {
            StremioProtocol.parseCatalog(get(StremioProtocol.resourceUrl(addon.baseUrl, "catalog", catalog.type, catalog.id, extra)))
        }.getOrDefault(emptyList())

    suspend fun meta(addon: Addon, type: String, id: String): Meta? =
        runCatching { StremioProtocol.parseMeta(get(StremioProtocol.resourceUrl(addon.baseUrl, "meta", type, id))) }.getOrNull()

    suspend fun streams(addon: Addon, type: String, id: String): List<StreamItem> =
        runCatching { StremioProtocol.parseStreams(get(StremioProtocol.resourceUrl(addon.baseUrl, "stream", type, id)), addon.name) }
            .getOrDefault(emptyList())

    suspend fun subtitles(addon: Addon, type: String, id: String, extra: Map<String, String> = emptyMap()): List<SubtitleItem> =
        runCatching { StremioProtocol.parseSubtitles(get(StremioProtocol.resourceUrl(addon.baseUrl, "subtitles", type, id, extra))) }
            .getOrDefault(emptyList())

    /** The add-ons listed by an add-on catalog (`addon_catalog` resource) of [addon]: a directory of other add-ons */
    suspend fun addonCatalog(addon: Addon, catalog: AddonCatalog): List<DirectoryAddon> =
        runCatching {
            StremioProtocol.parseAddonCatalog(get(StremioProtocol.resourceUrl(addon.baseUrl, "addon_catalog", catalog.type, catalog.id)))
        }.getOrDefault(emptyList())

    /** A list of add-ons at an address (Stremio's public add-on list); empty when it cannot be read */
    suspend fun addonList(url: String): List<DirectoryAddon> =
        runCatching { StremioProtocol.parseAddonList(get(url)) }.getOrDefault(emptyList())

    /** The streams of all add-ons that answer for this content, asked in parallel */
    suspend fun allStreams(addons: List<Addon>, type: String, id: String): List<StreamItem> = coroutineScope {
        addons.filter { it.manifest.supports("stream", type, id) }
            .map { a -> async { streams(a, type, id) } }
            .flatMap { it.await() }
    }

    suspend fun allSubtitles(addons: List<Addon>, type: String, id: String, extra: Map<String, String> = emptyMap()): List<SubtitleItem> = coroutineScope {
        addons.filter { it.manifest.supports("subtitles", type, id) }
            .map { a -> async { subtitles(a, type, id, extra) } }
            .flatMap { it.await() }
    }

    companion object {
        const val USER_AGENT = "Telos Media (Stremio add-on client)"
    }
}
