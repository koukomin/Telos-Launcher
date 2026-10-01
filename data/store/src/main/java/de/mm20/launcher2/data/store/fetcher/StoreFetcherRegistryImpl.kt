package de.mm20.launcher2.data.store.fetcher

import android.util.Log
import de.mm20.launcher2.store.fetcher.StoreFetcherRegistry
import de.mm20.launcher2.store.model.AppSource
import de.mm20.launcher2.store.model.ReleaseArtifact

class StoreFetcherRegistryImpl(
    private val gitHubFetcher: GitHubFetcher,
) : StoreFetcherRegistry {

    override suspend fun fetchLatestRelease(source: AppSource): ReleaseArtifact? {
        return when (source) {
            is AppSource.GitHub -> gitHubFetcher.fetchLatestRelease(source)

            is AppSource.DirectApk -> ReleaseArtifact(
                version = "unknown",
                versionCode = null,
                downloadUrl = source.downloadUrl,
            )

            is AppSource.AffiliateDirect -> ReleaseArtifact(
                version = "unknown",
                versionCode = null,
                downloadUrl = source.downloadUrl,
            )

            is AppSource.FDroid -> {
                // TODO(store): implement an F-Droid index-v2.json fetcher (Phase 2). The
                //  AppSource variant exists now so StoreItem sources don't need to migrate later.
                Log.w(TAG, "F-Droid sources are not resolvable yet: ${source.packageName}")
                null
            }

            is AppSource.AffiliatePlayStore -> {
                // Handled entirely by StoreActionHandler via a Play Store intent - there is no
                // release to resolve or download.
                null
            }
        }
    }

    companion object {
        private const val TAG = "StoreFetcherRegistry"
    }
}
