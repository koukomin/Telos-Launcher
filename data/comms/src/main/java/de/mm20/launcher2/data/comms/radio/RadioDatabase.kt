package de.mm20.launcher2.data.comms.radio

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "favorite_stations")
data class RadioStationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val streamUrl: String,
    val faviconUrl: String,
    @ColumnInfo(defaultValue = "''") val homepage: String = "",
    @ColumnInfo(defaultValue = "''") val streamContent: String = "",
    @ColumnInfo(defaultValue = "0") val nameManuallySet: Boolean = false,
    /** Fallback streams, one URL per line */
    @ColumnInfo(defaultValue = "''") val alternateStreams: String = "",
    @ColumnInfo(defaultValue = "0") val addedAt: Long = 0L,
)

@Entity(tableName = "radio_history")
data class RadioHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val stationId: String,
    val stationName: String,
    val title: String,
    val playedAt: Long,
)

@Dao
interface RadioStationDao {
    @Query("SELECT * FROM favorite_stations ORDER BY name COLLATE NOCASE ASC")
    fun observeFavorites(): Flow<List<RadioStationEntity>>

    @Query("SELECT * FROM favorite_stations")
    suspend fun getAll(): List<RadioStationEntity>

    @Query("SELECT * FROM favorite_stations WHERE id = :id")
    suspend fun getStation(id: String): RadioStationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(station: RadioStationEntity)

    @Delete
    suspend fun delete(station: RadioStationEntity)

    @Query("DELETE FROM favorite_stations WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE favorite_stations SET name = :name, nameManuallySet = 1 WHERE id = :id")
    suspend fun rename(id: String, name: String)

    @Query("SELECT * FROM radio_history ORDER BY playedAt DESC LIMIT 300")
    fun observeHistory(): Flow<List<RadioHistoryEntity>>

    @Insert
    suspend fun insertHistory(entry: RadioHistoryEntity)

    @Query("DELETE FROM radio_history")
    suspend fun clearHistory()

    @Query("DELETE FROM radio_history WHERE id NOT IN (SELECT id FROM radio_history ORDER BY playedAt DESC LIMIT 500)")
    suspend fun trimHistory()
}

@Database(
    entities = [RadioStationEntity::class, RadioHistoryEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class RadioDatabase : RoomDatabase() {
    abstract fun radioStationDao(): RadioStationDao

    companion object {
        @Volatile
        private var INSTANCE: RadioDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE favorite_stations ADD COLUMN homepage TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE favorite_stations ADD COLUMN streamContent TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE favorite_stations ADD COLUMN nameManuallySet INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE favorite_stations ADD COLUMN alternateStreams TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE favorite_stations ADD COLUMN addedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS radio_history (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "stationId TEXT NOT NULL, " +
                        "stationName TEXT NOT NULL, " +
                        "title TEXT NOT NULL, " +
                        "playedAt INTEGER NOT NULL)"
                )
            }
        }

        fun getDatabase(context: Context): RadioDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RadioDatabase::class.java,
                    "telos_radio.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
