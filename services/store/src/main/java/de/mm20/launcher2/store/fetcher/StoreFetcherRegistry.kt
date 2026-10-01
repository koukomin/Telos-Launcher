package de.mm20.launcher2.store.fetcher

import de.mm20.launcher2.store.model.AppSource
import de.mm20.launcher2.store.model.ReleaseArtifact

/**
 * Dispatches to the right [SourceFetcher] for an [AppSource] at runtime, since callers
 * ([de.mm20.launcher2.store.action.StoreActionHandler], `StoreUpdateWorker`) deal with the
 * sealed interface, not its concrete subtypes.
 */
interface StoreFetcherRegistry {
    /**
     * @return the latest release for [source]. For [AppSource.DirectApk]/[AppSource.AffiliateDirect],
     * which are already a fixed download, this returns a [ReleaseArtifact] built directly from
     * the source with no network call and a null `versionCode` (there's nothing to compare
     * against). For [AppSource.AffiliatePlayStore] this always returns null - the Play Store
     * install itself isn't something the launcher downloads or resolves a release for.
     */
    suspend fun fetchLatestRelease(source: AppSource): ReleaseArtifact?
}
