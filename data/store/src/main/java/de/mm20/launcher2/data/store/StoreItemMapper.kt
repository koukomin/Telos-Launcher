package de.mm20.launcher2.data.store

import de.mm20.launcher2.database.entities.StoreItemEntity
import de.mm20.launcher2.store.model.AppSource
import de.mm20.launcher2.store.model.ReleaseArtifact
import de.mm20.launcher2.store.model.StoreItem
import kotlinx.serialization.json.Json

/**
 * Converts between the domain [StoreItem]/[AppSource] (`:services:store`) and [StoreItemEntity]
 * (`:data:database`). This mapping lives here, in `:data:store`, rather than in `:data:database`
 * itself, so the low-level database module never needs to depend on a specific feature's domain
 * types - see [StoreItemEntity]'s doc comment.
 */
internal object StoreItemMapper {

    private val json = Json { ignoreUnknownKeys = true }

    fun toDomain(entity: StoreItemEntity): StoreItem {
        val source = json.decodeFromString(AppSource.serializer(), entity.sourceJson)
        val latestRelease = entity.latestDownloadUrl?.let {
            ReleaseArtifact(
                version = entity.latestVersion ?: "unknown",
                versionCode = entity.latestVersionCode,
                size = entity.latestSize,
                downloadUrl = it,
                changelog = entity.latestChangelog,
                publishedAt = entity.latestPublishedAt,
            )
        }
        return StoreItem(
            id = entity.id,
            packageName = entity.packageName,
            displayName = entity.displayName,
            source = source,
            installedVersionCode = entity.installedVersionCode,
            latestRelease = latestRelease,
            lastCheckedAt = entity.lastCheckedAt,
        )
    }

    fun toEntity(item: StoreItem): StoreItemEntity {
        return StoreItemEntity(
            id = item.id,
            packageName = item.packageName,
            displayName = item.displayName,
            sourceType = item.source.sourceTypeName(),
            sourceJson = json.encodeToString(AppSource.serializer(), item.source),
            installedVersionCode = item.installedVersionCode,
            latestVersionCode = item.latestRelease?.versionCode,
            latestVersion = item.latestRelease?.version,
            latestDownloadUrl = item.latestRelease?.downloadUrl,
            latestChangelog = item.latestRelease?.changelog,
            latestSize = item.latestRelease?.size,
            latestPublishedAt = item.latestRelease?.publishedAt,
            lastCheckedAt = item.lastCheckedAt,
        )
    }

    /** Matches the `@SerialName` on each [AppSource] subtype - kept here for the entity column. */
    private fun AppSource.sourceTypeName(): String = when (this) {
        is AppSource.GitHub -> "github"
        is AppSource.FDroid -> "fdroid"
        is AppSource.DirectApk -> "direct_apk"
        is AppSource.AffiliatePlayStore -> "affiliate_play_store"
        is AppSource.AffiliateDirect -> "affiliate_direct"
    }
}
