package com.telos.desktop.update

import java.io.InputStream
import java.net.URI
import java.nio.charset.StandardCharsets

/**
 * GitHub Releases channel for Telos Desktop signed updates (VOS-5).
 *
 * Default HTTPS URL points at the latest release asset
 * `telos-desktop-update.manifest` on `koukomin/Telos-Launcher`. Callers may
 * also resolve the asset via the documented Releases API JSON
 * (`/repos/{owner}/{repo}/releases/latest`) using [resolveManifestUrlFromReleasesJson]
 * or [lookupLatestManifestUrl].
 *
 * The URL alone is not trust: [DesktopSignedUpdate.check] still requires
 * Ed25519 verification and never auto-installs.
 */
object DesktopGitHubReleasesChannel {
    const val DEFAULT_OWNER = "koukomin"
    const val DEFAULT_REPO = "Telos-Launcher"
    const val MANIFEST_ASSET = DesktopSignedUpdate.MANIFEST_NAME

    /** Static latest-download HTTPS URL (no network call to build). */
    fun latestDownloadUrl(
        owner: String = DEFAULT_OWNER,
        repo: String = DEFAULT_REPO,
        assetName: String = MANIFEST_ASSET,
    ): String = "https://github.com/$owner/$repo/releases/latest/download/$assetName"

    /** Documented GitHub Releases API endpoint for the latest release. */
    fun releasesApiUrl(
        owner: String = DEFAULT_OWNER,
        repo: String = DEFAULT_REPO,
    ): String = "https://api.github.com/repos/$owner/$repo/releases/latest"

    /**
     * Parse GitHub Releases API JSON and return the HTTPS `browser_download_url`
     * for [assetName], or null if the asset is absent.
     *
     * Accepts a narrow JSON shape (assets[].name / browser_download_url) without
     * pulling a full JSON library into the desktop module.
     */
    fun resolveManifestUrlFromReleasesJson(
        json: String,
        assetName: String = MANIFEST_ASSET,
    ): String? {
        val assets = assetObjects(json) ?: return null
        for (asset in assets) {
            val name = jsonStringField(asset, "name") ?: continue
            if (name != assetName) continue
            val url = jsonStringField(asset, "browser_download_url") ?: continue
            if (url.startsWith("https://", ignoreCase = true)) return url
        }
        return null
    }

    /**
     * Fetch the Releases API over HTTPS and resolve the manifest asset URL.
     * [fetchJson] is injectable for unit tests (mock response, no network).
     */
    fun lookupLatestManifestUrl(
        owner: String = DEFAULT_OWNER,
        repo: String = DEFAULT_REPO,
        assetName: String = MANIFEST_ASSET,
        fetchJson: (apiUrl: String) -> String = ::httpsGetUtf8,
    ): String? {
        val api = releasesApiUrl(owner, repo)
        val body = runCatching { fetchJson(api) }.getOrNull() ?: return null
        return resolveManifestUrlFromReleasesJson(body, assetName)
    }

    private fun httpsGetUtf8(url: String): String {
        require(url.startsWith("https://", ignoreCase = true)) { "Releases API must use HTTPS" }
        openHttps(URI(url)).use { return it.readBytes().toString(StandardCharsets.UTF_8) }
    }

    private fun openHttps(uri: URI): InputStream {
        require(uri.scheme.equals("https", ignoreCase = true)) { "HTTPS required" }
        return uri.toURL().openStream()
    }

    /** Extract raw `{...}` objects inside the top-level `"assets": [ ... ]` array. */
    private fun assetObjects(json: String): List<String>? {
        val key = Regex(""""assets"\s*:\s*\[""", RegexOption.IGNORE_CASE)
        val match = key.find(json) ?: return null
        val start = match.range.last + 1
        var depth = 1
        var i = start
        while (i < json.length) {
            when (json[i]) {
                '[' -> depth++
                ']' -> {
                    depth--
                    if (depth == 0) {
                        return splitJsonObjects(json.substring(start, i))
                    }
                }
                '"' -> {
                    i++
                    while (i < json.length) {
                        if (json[i] == '\\') {
                            i += 2
                            continue
                        }
                        if (json[i] == '"') break
                        i++
                    }
                }
            }
            i++
        }
        return null
    }

    private fun splitJsonObjects(arrayBody: String): List<String> {
        val out = ArrayList<String>()
        var depth = 0
        var start = -1
        var i = 0
        while (i < arrayBody.length) {
            when (arrayBody[i]) {
                '{' -> {
                    if (depth == 0) start = i
                    depth++
                }
                '}' -> {
                    depth--
                    if (depth == 0 && start >= 0) {
                        out += arrayBody.substring(start, i + 1)
                        start = -1
                    }
                }
                '"' -> {
                    i++
                    while (i < arrayBody.length) {
                        if (arrayBody[i] == '\\') {
                            i += 2
                            continue
                        }
                        if (arrayBody[i] == '"') break
                        i++
                    }
                }
            }
            i++
        }
        return out
    }

    private fun jsonStringField(obj: String, field: String): String? {
        val re = Regex(""""$field"\s*:\s*"((?:\\.|[^"\\])*)"""")
        val m = re.find(obj) ?: return null
        return unescapeJson(m.groupValues[1])
    }

    private fun unescapeJson(s: String): String =
        buildString(s.length) {
            var i = 0
            while (i < s.length) {
                val c = s[i]
                if (c == '\\' && i + 1 < s.length) {
                    when (s[i + 1]) {
                        '"', '\\', '/' -> append(s[i + 1])
                        'n' -> append('\n')
                        'r' -> append('\r')
                        't' -> append('\t')
                        else -> append(s[i + 1])
                    }
                    i += 2
                } else {
                    append(c)
                    i++
                }
            }
        }
}
