package de.mm20.launcher2.data.comms.tv

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "tv_favorites")
data class TvFavoriteEntity(
    @PrimaryKey val channelId: String,
    val position: Int,
    val addedAt: Long,
)

@Entity(tableName = "tv_recents")
data class TvRecentEntity(
    @PrimaryKey val channelId: String,
    val playedAt: Long,
)

@Entity(tableName = "tv_custom_channels")
data class TvCustomEntity(
    @PrimaryKey val id: String,
    val name: String,
    val streamUrl: String,
    val logoUrl: String,
    val groupName: String,
    val addedAt: Long,
)

@Entity(tableName = "tv_preferred_streams")
data class TvPreferredEntity(
    @PrimaryKey val channelId: String,
    val streamUrl: String,
)

@Dao
interface TvDao {
    @Query("SELECT * FROM tv_favorites ORDER BY position ASC, addedAt ASC")
    fun observeFavorites(): Flow<List<TvFavoriteEntity>>

    @Query("SELECT * FROM tv_favorites ORDER BY position ASC, addedAt ASC")
    suspend fun getFavorites(): List<TvFavoriteEntity>

    @Query("SELECT COUNT(*) FROM tv_favorites WHERE channelId = :id")
    suspend fun favoriteCount(id: String): Int

    @Query("SELECT COUNT(*) FROM tv_favorites")
    suspend fun totalFavorites(): Int

    @Query("SELECT COALESCE(MAX(position), -1) FROM tv_favorites")
    suspend fun maxFavoritePosition(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(entity: TvFavoriteEntity)

    @Query("DELETE FROM tv_favorites WHERE channelId = :id")
    suspend fun deleteFavorite(id: String)

    @Query("UPDATE tv_favorites SET position = :position WHERE channelId = :id")
    suspend fun setFavoritePosition(id: String, position: Int)

    @Query("SELECT * FROM tv_recents ORDER BY playedAt DESC LIMIT :limit")
    fun observeRecents(limit: Int): Flow<List<TvRecentEntity>>

    @Query("SELECT * FROM tv_recents")
    suspend fun getRecents(): List<TvRecentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecent(entity: TvRecentEntity)

    @Query("DELETE FROM tv_recents WHERE channelId NOT IN (SELECT channelId FROM tv_recents ORDER BY playedAt DESC LIMIT :keep)")
    suspend fun trimRecents(keep: Int)

    @Query("DELETE FROM tv_recents")
    suspend fun clearRecents()

    @Query("SELECT * FROM tv_custom_channels ORDER BY name COLLATE NOCASE ASC")
    fun observeCustom(): Flow<List<TvCustomEntity>>

    @Query("SELECT * FROM tv_custom_channels")
    suspend fun getCustom(): List<TvCustomEntity>

    @Query("SELECT * FROM tv_custom_channels WHERE id = :id")
    suspend fun getCustomById(id: String): TvCustomEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustom(entity: TvCustomEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomList(entities: List<TvCustomEntity>)

    @Query("DELETE FROM tv_custom_channels WHERE id = :id")
    suspend fun deleteCustom(id: String)

    @Query("DELETE FROM tv_favorites WHERE channelId = :id")
    suspend fun deleteCustomFavorite(id: String)

    @Query("DELETE FROM tv_recents WHERE channelId = :id")
    suspend fun deleteRecent(id: String)

    @Query("SELECT COUNT(*) FROM tv_custom_channels")
    suspend fun customCount(): Int

    @Query("SELECT * FROM tv_preferred_streams WHERE channelId = :id")
    suspend fun getPreferred(id: String): TvPreferredEntity?

    @Query("SELECT * FROM tv_preferred_streams")
    suspend fun getAllPreferred(): List<TvPreferredEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreferred(entity: TvPreferredEntity)

    @Query("DELETE FROM tv_preferred_streams WHERE channelId = :id")
    suspend fun deletePreferred(id: String)
}

/** The user's TV data. A new database (version 1), so no existing data is touched; later changes must add migrations. */
@Database(
    entities = [TvFavoriteEntity::class, TvRecentEntity::class, TvCustomEntity::class, TvPreferredEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class TvDatabase : RoomDatabase() {
    abstract fun tvDao(): TvDao

    companion object {
        @Volatile
        private var INSTANCE: TvDatabase? = null

        fun getDatabase(context: Context): TvDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    TvDatabase::class.java,
                    "telos_tv.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
