package de.mm20.launcher2.data.comms

import android.content.Context
import androidx.room.*

@Entity(tableName = "blocked_numbers")
data class BlockedNumberEntity(
    @PrimaryKey
    val number: String
)

@Dao
interface BlockedNumberDao {
    @Query("SELECT * FROM blocked_numbers WHERE number = :number")
    suspend fun getBlockedNumber(number: String): BlockedNumberEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(number: BlockedNumberEntity)

    @Delete
    suspend fun delete(number: BlockedNumberEntity)
}

@Database(entities = [BlockedNumberEntity::class], version = 1, exportSchema = false)
abstract class SpamDatabase : RoomDatabase() {
    abstract fun blockedNumberDao(): BlockedNumberDao

    companion object {
        @Volatile
        private var INSTANCE: SpamDatabase? = null

        fun getDatabase(context: Context): SpamDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SpamDatabase::class.java,
                    "spam.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
