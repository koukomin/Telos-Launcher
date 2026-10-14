package de.mm20.launcher2.helper

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class PasswordFileEncryptionTest {
    private fun withFiles(block: (File, File) -> Unit) {
        val root = Files.createTempDirectory("telos-encryption-test").toFile()
        try { block(root, File(root, "private").apply { mkdir() }) } finally { root.deleteRecursively() }
    }

    @Test fun roundTripAcrossMultipleChunksAndEmptyFile() = withFiles { root, staging ->
        val source = File(root, "original").apply { writeBytes(ByteArray(150_003) { (it * 37).toByte() }) }
        val encrypted = File(root, "encrypted"); val restored = File(root, "restored")
        val password = "long private password".toCharArray()
        PasswordFileEncryption.encrypt(source, encrypted, password, staging)
        PasswordFileEncryption.decrypt(encrypted, restored, password, staging)
        assertArrayEquals(source.readBytes(), restored.readBytes())
        assertTrue(staging.listFiles().orEmpty().isEmpty())
        val empty = File(root, "empty").apply { writeBytes(byteArrayOf()) }
        val sealedEmpty = File(root, "empty.aes"); val restoredEmpty = File(root, "empty.restored")
        PasswordFileEncryption.encrypt(empty, sealedEmpty, password, staging)
        PasswordFileEncryption.decrypt(sealedEmpty, restoredEmpty, password, staging)
        assertEquals(0L, restoredEmpty.length())
    }

    @Test fun wrongPasswordCorruptionAndTruncationNeverPublishPlaintext() = withFiles { root, staging ->
        val original = File(root, "original").apply { writeBytes(ByteArray(70_000) { it.toByte() }) }
        val encrypted = File(root, "encrypted")
        PasswordFileEncryption.encrypt(original, encrypted, "correct".toCharArray(), staging)
        val destination = File(root, "output")
        assertTrue(runCatching { PasswordFileEncryption.decrypt(encrypted, destination, "wrong".toCharArray(), staging) }.isFailure)
        assertFalse(destination.exists())
        val corrupt = File(root, "corrupt").apply { writeBytes(encrypted.readBytes().also { it[100] = (it[100].toInt() xor 1).toByte() }) }
        assertTrue(runCatching { PasswordFileEncryption.decrypt(corrupt, destination, "correct".toCharArray(), staging) }.isFailure)
        assertFalse(destination.exists())
        val truncated = File(root, "truncated").apply { writeBytes(encrypted.readBytes().dropLast(20).toByteArray()) }
        assertTrue(runCatching { PasswordFileEncryption.decrypt(truncated, destination, "correct".toCharArray(), staging) }.isFailure)
        assertFalse(destination.exists())
        assertTrue(staging.listFiles().orEmpty().isEmpty())
    }

    @Test fun refusesDestinationCollisionAndRollsBackCancellation() = withFiles { root, staging ->
        val source = File(root, "original").apply { writeText("keep original") }
        val destination = File(root, "existing").apply { writeText("keep destination") }
        assertTrue(runCatching { PasswordFileEncryption.encrypt(source, destination, "password".toCharArray(), staging) }.isFailure)
        assertEquals("keep destination", destination.readText())
        var calls = 0
        val cancelled = File(root, "cancelled")
        assertTrue(runCatching {
            PasswordFileEncryption.encrypt(source, cancelled, "password".toCharArray(), staging,
                checkpoint = { if (++calls == 6) throw java.util.concurrent.CancellationException() })
        }.isFailure)
        assertFalse(cancelled.exists())
        assertEquals("keep original", source.readText())
        assertTrue(staging.listFiles().orEmpty().isEmpty())
    }
}
