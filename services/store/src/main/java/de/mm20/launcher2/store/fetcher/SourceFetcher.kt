package de.mm20.launcher2.store.fetcher

import de.mm20.launcher2.store.model.AppSource
import de.mm20.launcher2.store.model.ReleaseArtifact

/**
 * Resolves the latest available [ReleaseArtifact] for one [AppSource] variant. Each source type
 * that needs active lookup (as opposed to [AppSource.DirectApk]/[AppSource.AffiliateDirect],
 * which already carry a fixed download URL) gets its own implementation in `:data:store`.
 */
interface SourceFetcher<T : AppSource> {
    /**
     * @return the newest release, or null if the source has none, is unreachable, or the
     * response can't be parsed. Implementations should not throw for ordinary failure modes
     * (network errors, 404s, malformed responses) - only for programmer errors.
     */
    suspend fun fetchLatestRelease(source: T): ReleaseArtifact?
}
