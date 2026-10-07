package de.mm20.launcher2.data.store.fetcher

import android.os.Build
import de.mm20.launcher2.store.fetcher.SourceFetcher
import de.mm20.launcher2.store.model.AppSource
import de.mm20.launcher2.store.model.ReleaseArtifact
import io.ktor.client.HttpClient
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
        val answer = httpClient.getText("$repo/index-v1.json", accept = "application/json")
        if (!answer.ok) throw StoreFetchException("The repository answered ${answer.code}")
        val root = JSONObject(answer.body)
        val list = root.optJSONObject("packages")?.optJSONArray(packageName)
            ?: throw StoreFetchException("$packageName is not in this repository")
        val abis = Build.SUPPORTED_ABIS.toSet()
        val builds = (0 until list.length()).map { list.getJSONObject(it) }.filter { b ->
            val native = b.optJSONArray("nativecode")
            native == null || native.length() == 0 || (0 until native.length()).any { native.getString(it) in abis }
        }
        if (builds.isEmpty()) throw StoreFetchException("No build of $packageName for this phone")
        var suggested = 0L
        root.optJSONArray("apps")?.let { apps ->
            for (i in 0 until apps.length()) {
                val a = apps.getJSONObject(i)
                if (a.optString("packageName") == packageName) suggested = a.optLong("suggestedVersionCode", 0)
            }
        }
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
