package de.mm20.launcher2.data.comms.radio

import android.content.Context
import androidx.room.*
import de.mm20.launcher2.comms.model.RadioStation
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "favorite_stations")
data class RadioStationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val streamUrl: String,
    val faviconUrl: String
)

@Dao
interface RadioStationDao {
    @Query("SELECT * FROM favorite_stations")
    fun observeFavorites(): Flow<List<RadioStationEntity>>

    @Query("SELECT * FROM favorite_stations WHERE id = :id")
    suspend fun getStation(id: String): RadioStationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(station: RadioStationEntity)

    @Delete
    suspend fun delete(station: RadioStationEntity)
}

@Database(entities = [RadioStationEntity::class], version = 1, exportSchema = false)
abstract class RadioDatabase : RoomDatabase() {
    abstract fun radioStationDao(): RadioStationDao

    companion object {
        @Volatile
        private var INSTANCE: RadioDatabase? = null

        fun getDatabase(context: Context): RadioDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RadioDatabase::class.java,
                    "telos_radio.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
