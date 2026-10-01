package de.mm20.launcher2.database.daos

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import de.mm20.launcher2.database.entities.StoreItemEntity

@Dao
interface StoreItemDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: StoreItemEntity)

    @Query("SELECT * FROM StoreItem WHERE id = :id")
    suspend fun get(id: String): StoreItemEntity?

    @Query("SELECT * FROM StoreItem WHERE packageName = :packageName")
    suspend fun getByPackageName(packageName: String): StoreItemEntity?

    @Query("SELECT * FROM StoreItem")
    suspend fun getAll(): List<StoreItemEntity>

    @Query("UPDATE StoreItem SET installedVersionCode = :versionCode WHERE packageName = :packageName")
    suspend fun updateInstalledVersion(packageName: String, versionCode: Long?)

    @Query(
        """
        UPDATE StoreItem SET
            latestVersionCode = :versionCode,
            latestVersion = :version,
            latestDownloadUrl = :downloadUrl,
            latestChangelog = :changelog,
            latestSize = :size,
            latestPublishedAt = :publishedAt,
            lastCheckedAt = :checkedAt
        WHERE id = :id
        """
    )
    suspend fun updateLatestRelease(
        id: String,
        versionCode: Long?,
        version: String?,
        downloadUrl: String?,
        changelog: String?,
        size: Long?,
        publishedAt: Long?,
        checkedAt: Long,
    )

    @Delete
    suspend fun delete(item: StoreItemEntity)
}
