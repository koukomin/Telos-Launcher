package de.mm20.launcher2.data.store.fetcher

import android.os.Build
import de.mm20.launcher2.store.fetcher.SourceFetcher
import de.mm20.launcher2.store.model.AppSource
import de.mm20.launcher2.store.model.ReleaseArtifact
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.jvm.javaio.toInputStream
import android.util.JsonReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * F-Droid and F-Droid compatible repositories.
 * - f-droid.org and IzzyOnDroid have a small per-app address that lists the versions and the version
 *   the repository suggests; their APK files are named `<package>_<versionCode>.apk`.
 * - Any other repository is read through its `index-v1.json`, which names every APK file and its CPU types.
 */
class FDroidFetcher(
    private val httpClient: HttpClient = HttpClient(),
) : SourceFetcher<AppSource.FDroid> {

    override suspend fun fetchLatestRelease(source: AppSource.FDroid): ReleaseArtifact? =
        try { fetch(source) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { null }

    suspend fun fetch(source: AppSource.FDroid): ReleaseArtifact {
        val repo = source.repoUrl.trimEnd('/')
        val host = repo.substringAfter("://").substringBefore('/')
        return when {
            host == "f-droid.org" -> fromPackageApi("https://f-droid.org/api/v1/packages/${source.packageName}", repo, source.packageName)
            host == "apt.izzysoft.de" -> fromPackageApi("https://apt.izzysoft.de/fdroid/api/v1/packages/${source.packageName}", repo, source.packageName)
            else -> fromIndex(repo, source.packageName)
        }
    }

    private suspend fun fromPackageApi(apiUrl: String, repo: String, packageName: String): ReleaseArtifact {
        val answer = httpClient.getText(apiUrl, accept = "application/json")
        when {
            answer.code == 404 -> throw StoreFetchException("$packageName is not in this repository")
            !answer.ok -> throw StoreFetchException("The repository answered ${answer.code}")
        }
        val json = JSONObject(answer.body)
        val versions = json.optJSONArray("packages") ?: throw StoreFetchException("No versions listed for $packageName")
        val entries = (0 until versions.length()).map { versions.getJSONObject(it) }
        if (entries.isEmpty()) throw StoreFetchException("No versions listed for $packageName")
        // the repository's own suggestion avoids picking a build for another CPU type
        val suggested = json.optLong("suggestedVersionCode", 0)
        val chosen = entries.firstOrNull { it.optLong("versionCode") == suggested } ?: entries.maxByOrNull { it.optLong("versionCode") }!!
        val code = chosen.optLong("versionCode")
        return ReleaseArtifact(
            version = chosen.optString("versionName"),
            versionCode = code,
            downloadUrl = "$repo/${packageName}_$code.apk",
        )
    }

    private suspend fun fromIndex(repo: String, packageName: String): ReleaseArtifact {
        val (list, suggested) = withContext(Dispatchers.IO) {
            httpClient.prepareGet("$repo/index-v1.json") { header("Accept", "application/json") }.execute { response ->
                if (response.status.value !in 200..299) throw StoreFetchException("The repository answered ${response.status}")
                val channel = response.bodyAsChannel()
                try {
                    JsonReader(channel.toInputStream().reader(Charsets.UTF_8)).use { reader ->
                        var versions: org.json.JSONArray? = null
                        var suggested = 0L
                        reader.beginObject()
                        while (reader.hasNext()) when (reader.nextName()) {
                            "apps" -> {
                                reader.beginArray()
                                while (reader.hasNext()) {
                                    var pkg = ""; var code = 0L
                                    reader.beginObject()
                                    while (reader.hasNext()) when (reader.nextName()) {
                                        "packageName" -> pkg = reader.nextString()
                                        "suggestedVersionCode" -> code = reader.nextLong()
                                        else -> reader.skipValue()
                                    }
                                    reader.endObject()
                                    if (pkg == packageName) suggested = code
                                }
                                reader.endArray()
                            }
                            "packages" -> {
                                reader.beginObject()
                                while (reader.hasNext()) {
                                    if (reader.nextName() != packageName) { reader.skipValue(); continue }
                                    val selected = org.json.JSONArray()
                                    reader.beginArray()
                                    while (reader.hasNext()) {
                                        if (selected.length() >= 1000) throw StoreFetchException("Too many versions of $packageName")
                                        val build = JSONObject()
                                        reader.beginObject()
                                        while (reader.hasNext()) {
                                            val field = reader.nextName()
                                            when (field) {
                                                "versionCode", "size", "added" -> build.put(field, reader.nextLong())
                                                "versionName", "apkName" -> build.put(field, reader.nextString())
                                                "nativecode" -> {
                                                    val abis = org.json.JSONArray()
                                                    reader.beginArray()
                                                    while (reader.hasNext()) abis.put(reader.nextString())
                                                    reader.endArray()
                                                    build.put(field, abis)
                                                }
                                                else -> reader.skipValue()
                                            }
                                        }
                                        reader.endObject()
                                        selected.put(build)
                                    }
                                    reader.endArray()
                                    versions = selected
                                }
                                reader.endObject()
                            }
                            else -> reader.skipValue()
                        }
                        reader.endObject()
                        (versions ?: throw StoreFetchException("$packageName is not in this repository")) to suggested
                    }
                } finally { channel.cancel() }
            }
        }
        val abis = Build.SUPPORTED_ABIS.toSet()
        val builds = (0 until list.length()).map { list.getJSONObject(it) }.filter { b ->
            val native = b.optJSONArray("nativecode")
            native == null || native.length() == 0 || (0 until native.length()).any { native.getString(it) in abis }
        }
        if (builds.isEmpty()) throw StoreFetchException("No build of $packageName for this phone")
        val chosen = builds.firstOrNull { it.optLong("versionCode") == suggested } ?: builds.maxByOrNull { it.optLong("versionCode") }!!
        return ReleaseArtifact(
            version = chosen.optString("versionName"),
            versionCode = chosen.optLong("versionCode"),
            size = chosen.optLong("size").takeIf { it > 0 },
            downloadUrl = "$repo/${chosen.optString("apkName")}",
            publishedAt = chosen.optLong("added").takeIf { it > 0 },
        )
    }
}
