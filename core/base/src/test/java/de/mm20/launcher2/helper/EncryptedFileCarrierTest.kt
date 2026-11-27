package de.mm20.launcher2.helper

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class EncryptedFileCarrierTest {
    @get:Rule
    val files = TemporaryFolder()

    @Test
    fun preservesCarrierAndOnlyPublishesAuthenticatedPayload() {
        val cover = "Original carrier bytes".toByteArray()
        val payload = files.newFile().apply { writeBytes(ByteArray(150_003) { it.toByte() }) }
        val combined = File(files.root, "carrier")
        val recovered = File(files.root, "recovered")
        val secret = "a strong shared secret".toCharArray()

        EncryptedFileCarrier.embed(cover.inputStream(), payload, combined, secret, files.newFolder()) {}
        assertArrayEquals(cover, combined.inputStream().use { it.readNBytes(cover.size) })

        assertTrue(runCatching {
            EncryptedFileCarrier.extract(combined, recovered, "incorrect".toCharArray(), files.newFolder()) {}
        }.isFailure)
        assertFalse(recovered.exists())

        val corrupted = files.newFile().apply {
            writeBytes(combined.readBytes().also { it[it.lastIndex] = 0 })
        }
        assertTrue(runCatching {
            EncryptedFileCarrier.extract(corrupted, recovered, secret, files.newFolder()) {}
        }.isFailure)
        assertFalse(recovered.exists())

        EncryptedFileCarrier.extract(combined, recovered, secret, files.newFolder()) {}
        assertArrayEquals(payload.readBytes(), recovered.readBytes())
        assertTrue(runCatching {
            EncryptedFileCarrier.extract(combined, recovered, secret, files.newFolder()) {}
        }.isFailure)
        assertArrayEquals(payload.readBytes(), recovered.readBytes())
    }
}
