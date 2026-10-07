package de.mm20.launcher2.data.store.fetcher

import de.mm20.launcher2.store.fetcher.StoreFetcherRegistry
import de.mm20.launcher2.store.model.AppSource
import de.mm20.launcher2.store.model.ReleaseArtifact
import io.ktor.client.HttpClient

class StoreFetcherRegistryImpl(
    private val gitHubFetcher: GitHubFetcher,
    private val gitLabFetcher: GitLabFetcher,
    private val giteaFetcher: GiteaFetcher,
    private val fDroidFetcher: FDroidFetcher,
    private val sourceForgeFetcher: SourceForgeFetcher,
    private val htmlFetcher: HtmlFetcher,
) : StoreFetcherRegistry {

    override suspend fun fetchLatestRelease(source: AppSource): ReleaseArtifact? =
        resolveLatestRelease(source).getOrNull()

    /** Like [fetchLatestRelease], but with the reason in words when it fails */
    override suspend fun resolveLatestRelease(source: AppSource): Result<ReleaseArtifact> = try {
        Result.success(
            when (source) {
                is AppSource.GitHub -> gitHubFetcher.fetch(source)
                is AppSource.GitLab -> gitLabFetcher.fetch(source)
                is AppSource.Gitea -> giteaFetcher.fetch(source)
                is AppSource.FDroid -> fDroidFetcher.fetch(source)
                is AppSource.SourceForge -> sourceForgeFetcher.fetch(source)
                is AppSource.Html -> htmlFetcher.fetch(source)
                is AppSource.DirectApk -> ReleaseArtifact(version = "unknown", versionCode = null, downloadUrl = source.downloadUrl)
                is AppSource.AffiliateDirect -> ReleaseArtifact(version = "unknown", versionCode = null, downloadUrl = source.downloadUrl)
                // Handled entirely by StoreActionHandler via a Play Store intent - nothing to resolve
                is AppSource.AffiliatePlayStore -> throw StoreFetchException("Installed through the Play Store")
            }
        )
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    companion object {
        fun create(httpClient: HttpClient, githubToken: () -> String = { "" }) = StoreFetcherRegistryImpl(
            gitHubFetcher = GitHubFetcher(httpClient, githubToken),
            gitLabFetcher = GitLabFetcher(httpClient),
            giteaFetcher = GiteaFetcher(httpClient),
            fDroidFetcher = FDroidFetcher(httpClient),
            sourceForgeFetcher = SourceForgeFetcher(httpClient),
            htmlFetcher = HtmlFetcher(httpClient),
        )
    }
}
