package de.mm20.launcher2.store.transfer

import de.mm20.launcher2.store.model.AppSource
import de.mm20.launcher2.store.model.StoreItem
import de.mm20.launcher2.store.options.ItemOptions
import de.mm20.launcher2.store.parser.StoreUrlParser
import org.json.JSONArray
import org.json.JSONObject

/** One app read from an Obtainium export. */
data class ImportedApp(
    val packageName: String,
    val name: String,
    val source: AppSource,
    val options: ItemOptions,
)

/**
 * Reads and writes the export file of Obtainium (https://github.com/ImranR98/Obtainium), a JSON file
 * with an "apps" list whose entries have the app id (its package name), the source address and the
 * settings of the app as a JSON text. The same file works in both directions, so a list of apps
 * can be moved from Obtainium to Telos and back.
 */
object ObtainiumTransfer {

    fun export(items: List<StoreItem>, options: (String) -> ItemOptions): String {
        val apps = JSONArray()
        for (item in items) {
            val o = options(item.id)
            val url = StoreUrlParser.toUrl(item.source)
            val settings = JSONObject()
                .put("appName", item.displayName)
                .put("trackOnly", o.trackOnly)
                .put("exemptFromBackgroundUpdates", o.excludeFromBackground)
            when (val s = item.source) {
                is AppSource.GitHub -> settings.put("includePrereleases", s.includePrereleases).put("apkFilterRegEx", s.assetNameRegex.orEmpty())
                is AppSource.GitLab -> settings.put("includePrereleases", s.includePrereleases).put("apkFilterRegEx", s.assetNameRegex.orEmpty())
                is AppSource.Gitea -> settings.put("includePrereleases", s.includePrereleases).put("apkFilterRegEx", s.assetNameRegex.orEmpty())
                is AppSource.SourceForge -> settings.put("apkFilterRegEx", s.assetNameRegex.orEmpty())
                is AppSource.Html -> settings.put("apkFilterRegEx", s.linkRegex.orEmpty())
                else -> Unit
            }
            val release = item.latestRelease
            apps.put(
                JSONObject()
                    .put("id", item.packageName)
                    .put("url", url)
                    .put("author", authorOf(item.source))
                    .put("name", item.displayName)
                    .put("installedVersion", item.installedVersionName ?: JSONObject.NULL)
                    .put("latestVersion", release?.version ?: JSONObject.NULL)
                    .put("apkUrls", if (release != null) JSONArray().put(JSONArray().put(release.downloadUrl.substringAfterLast('/')).put(release.downloadUrl)).toString() else "[]")
                    .put("otherAssetUrls", "[]")
                    .put("preferredApkIndex", 0)
                    .put("additionalSettings", settings.toString())
                    .put("lastUpdateCheck", item.lastCheckedAt ?: JSONObject.NULL)
                    .put("pinned", o.pinned)
                    .put("categories", JSONArray().apply { if (o.category.isNotBlank()) put(o.category) })
                    .put("overrideSource", JSONObject.NULL)
                    .put("allowIdChange", false)
            )
        }
        return JSONObject().put("apps", apps).put("settings", JSONObject()).toString(2)
    }

    /** Apps of the file whose address Telos understands, and how many it could not use. */
    fun parse(text: String): Pair<List<ImportedApp>, Int> {
        val root = JSONObject(text)
        val array = root.optJSONArray("apps") ?: return emptyList<ImportedApp>() to 0
        val result = ArrayList<ImportedApp>()
        var unsupported = 0
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val url = o.optString("url")
            val parsed = StoreUrlParser.parse(url)
            val unsupportedHost = listOf("apkmirror", "uptodown", "aptoide", "apkpure", "play.google", "huawei", "tencent", "rustore", "telegram")
                .any { url.contains(it, ignoreCase = true) }
            if (parsed == null || unsupportedHost) {
                unsupported++
                continue
            }
            val settings = additionalSettings(o)
            var source = parsed.source
            val regex = settings.optString("apkFilterRegEx").ifBlank { null }
            val prerelease = settings.optBoolean("includePrereleases", false)
            source = when (source) {
                is AppSource.GitHub -> source.copy(assetNameRegex = regex, includePrereleases = prerelease)
                is AppSource.GitLab -> source.copy(assetNameRegex = regex, includePrereleases = prerelease)
                is AppSource.Gitea -> source.copy(assetNameRegex = regex, includePrereleases = prerelease)
                is AppSource.SourceForge -> source.copy(assetNameRegex = regex)
                is AppSource.Html -> source.copy(linkRegex = regex)
                else -> source
            }
            val categories = o.optJSONArray("categories")
            result += ImportedApp(
                packageName = o.optString("id").ifBlank { parsed.packageName ?: "unknown.package" },
                name = settings.optString("appName").ifBlank { o.optString("name").ifBlank { parsed.suggestedName } },
                source = source,
                options = ItemOptions(
                    trackOnly = settings.optBoolean("trackOnly", false),
                    pinned = o.optBoolean("pinned", false),
                    excludeFromBackground = settings.optBoolean("exemptFromBackgroundUpdates", false),
                    category = categories?.optString(0).orEmpty(),
                ),
            )
        }
        return result to unsupported
    }

    /** Obtainium writes the settings of an app as a JSON text inside the JSON file */
    private fun additionalSettings(app: JSONObject): JSONObject {
        val raw = app.opt("additionalSettings")
        return when (raw) {
            is JSONObject -> raw
            is String -> runCatching { JSONObject(raw) }.getOrDefault(JSONObject())
            else -> JSONObject()
        }
    }

    private fun authorOf(source: AppSource): String = when (source) {
        is AppSource.GitHub -> source.owner
        is AppSource.Gitea -> source.owner
        is AppSource.GitLab -> source.path.substringBefore('/')
        else -> ""
    }
}
