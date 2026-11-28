package de.mm20.launcher2.keepasstl

import app.keemobile.kotpass.cryptography.EncryptedValue
import app.keemobile.kotpass.database.Credentials
import app.keemobile.kotpass.database.KeePassDatabase
import app.keemobile.kotpass.database.decode
import app.keemobile.kotpass.database.encode
import app.keemobile.kotpass.models.Meta
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Test

class KotpassDatabaseTest {
    @Test
    fun roundTripsEncryptedDatabaseWithXmlSpecialCharacters() {
        val credentials = Credentials.from(EncryptedValue.fromString("fixture password"))
        val groupName = "Accounts <work> & personal"
        val database = KeePassDatabase.Ver4x.create(groupName, Meta(), credentials)
        val output = ByteArrayOutputStream()

        database.encode(output)
        val decoded = KeePassDatabase.decode(output.toByteArray().inputStream(), credentials)

        assertEquals(groupName, decoded.content.group.name)
    }
}
