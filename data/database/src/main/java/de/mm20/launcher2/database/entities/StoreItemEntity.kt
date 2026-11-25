import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Row for one Store-tracked app. [sourceType]/[sourceJson] store the Store module's `AppSource`
 * sealed interface as a discriminator + its JSON-encoded payload, rather than this module
 * depending on `:services:store` for the type itself - `:data:database` is a low-level module
 * shared by nearly everything, and `AppSource` is encoded/decoded by `:data:store` instead.
 */
@Entity(
    tableName = "StoreItem",
    indices = [
        Index(value = ["packageName"])
    ]
)
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
