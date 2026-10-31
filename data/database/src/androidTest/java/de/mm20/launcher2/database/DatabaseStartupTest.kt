package de.mm20.launcher2.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseStartupTest {
    @Test
    fun productionDatabaseOpensAndReopensWithoutLosingData() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = AppDatabase.getInstance(context)
        val helper = database.openHelper
        try {
            val connection = helper.writableDatabase
            connection.query("SELECT COUNT(*) FROM SearchAction").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertTrue(cursor.getInt(0) >= 11)
            }
            connection.execSQL("INSERT INTO SearchAction (position, type) VALUES (4242, 'startup-test')")
            helper.close()
            helper.writableDatabase.query(
                "SELECT COUNT(*) FROM SearchAction WHERE position = 4242 AND type = 'startup-test'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
        } finally {
            database.close()
            context.deleteDatabase("room")
        }
    }
}
