package de.mm20.launcher2.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Row for one Store-tracked app. [sourceType]/[sourceJson] store the Store module's `AppSource`
 * sealed interface as a discriminator + its JSON-encoded payload, rather than this module
 * depending on `:services:store` for the type itself - `:data:database` is a low-level module
 * shared by nearly everything, and `AppSource` is encoded/decoded by `:data:store` instead.
 */
@Entity(tableName = "StoreItem")
data class StoreItemEntity(
    @PrimaryKey val id: String,
    val packageName: String,
    val displayName: String,
    val sourceType: String,
    val sourceJson: String,
    val installedVersionCode: Long?,
    val latestVersionCode: Long?,
    val latestVersion: String?,
    val latestDownloadUrl: String?,
    val latestChangelog: String?,
    val latestSize: Long?,
    val latestPublishedAt: Long?,
    val lastCheckedAt: Long?,
)
