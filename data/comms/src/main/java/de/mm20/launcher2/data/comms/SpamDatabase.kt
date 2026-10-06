package de.mm20.launcher2.data.comms

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.runBlocking

@Entity(tableName = "blocked_numbers")
data class BlockedNumberEntity(
    @PrimaryKey
    val number: String
)

@Dao
interface BlockedNumberDao {
    @Query("SELECT * FROM blocked_numbers WHERE number = :number")
    suspend fun getBlockedNumber(number: String): BlockedNumberEntity?

    @Query("SELECT number FROM blocked_numbers")
    suspend fun getAllNumbers(): List<String>

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
                val factory = net.sqlcipher.database.SupportFactory(SpamDbKey.get(context))
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SpamDatabase::class.java,
                    "spam_secure.db"
                ).openHelperFactory(factory).build()
                INSTANCE = instance
                migratePlaintext(context, instance)
                instance
            }
        }

        private fun migratePlaintext(context: Context, encrypted: SpamDatabase) {
            val old = context.getDatabasePath("spam.db")
            if (!old.exists()) return
            try {
                val sqlite = android.database.sqlite.SQLiteDatabase.openDatabase(
                    old.path,
                    null,
                    android.database.sqlite.SQLiteDatabase.OPEN_READONLY,
                )
                sqlite.rawQuery("SELECT number FROM blocked_numbers", null)?.use { c ->
                    while (c.moveToNext()) {
                        val n = c.getString(0) ?: continue
                        kotlinx.coroutines.runBlocking {
                            encrypted.blockedNumberDao().insert(BlockedNumberEntity(n))
                        }
                    }
                }
                sqlite.close()
                old.delete()
                context.getDatabasePath("spam.db-wal").delete()
                context.getDatabasePath("spam.db-shm").delete()
            } catch (_: Exception) {
            }
        }
    }
}
