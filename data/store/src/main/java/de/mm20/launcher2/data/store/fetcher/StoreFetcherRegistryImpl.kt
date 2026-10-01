package de.mm20.launcher2.data.store.fetcher

import de.mm20.launcher2.store.fetcher.StoreFetcherRegistry
import de.mm20.launcher2.store.model.AppSource
import de.mm20.launcher2.store.model.ReleaseArtifact

class StoreFetcherRegistryImpl(
    private val gitHubFetcher: GitHubFetcher,
    private val fDroidFetcher: FDroidFetcher,
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

            is AppSource.FDroid -> fDroidFetcher.fetchLatestRelease(source)

            is AppSource.AffiliatePlayStore -> {
                // Handled entirely by StoreActionHandler via a Play Store intent - there is no
                // release to resolve or download.
                null
            }
        }
    }
}
