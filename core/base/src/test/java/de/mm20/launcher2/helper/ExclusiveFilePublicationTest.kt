package de.mm20.launcher2.helper

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.nio.file.Files

class ExclusiveFilePublicationTest {
    @Test fun publishesWithoutDeletingStagingAndRejectsExistingDestination() {
        val root = Files.createTempDirectory("publication").toFile()
        try {
            val source = root.resolve("source").apply { writeBytes(ByteArray(200_000) { it.toByte() }) }
            val destination = root.resolve("destination")
            ExclusiveFilePublication.copy(source, destination)
            assertArrayEquals(source.readBytes(), destination.readBytes())
            assertTrue(source.exists())
            try { ExclusiveFilePublication.copy(source, destination); fail("collision accepted") } catch (_: IOException) {}
            assertArrayEquals(source.readBytes(), destination.readBytes())
        } finally { root.deleteRecursively() }
    }
    @Test fun cancellationRemovesOnlyItsOwnPartialFile() {
        val root = Files.createTempDirectory("publication-cancel").toFile()
        try {
            val source = root.resolve("source").apply { writeBytes(ByteArray(200_000)) }
            val destination = root.resolve("destination")
            var checks = 0
            try {
                ExclusiveFilePublication.copy(source, destination) { if (++checks == 3) throw IOException("cancelled") }
                fail("cancellation ignored")
            } catch (_: IOException) {}
            assertFalse(destination.exists())
            assertEquals(200_000L, source.length())
        } finally { root.deleteRecursively() }
    }
    @Test fun cancellationNeverDeletesAnotherWritersReplacement() {
        val root = Files.createTempDirectory("publication-replaced").toFile()
        try {
            val source = root.resolve("source").apply { writeBytes(ByteArray(200_000)) }
            val destination = root.resolve("destination")
            var checks = 0
            try {
                ExclusiveFilePublication.copy(source, destination) {
                    if (++checks == 3) {
                        Files.move(destination.toPath(), root.resolve("old").toPath())
                        destination.writeText("replacement")
                        throw IOException("cancelled")
                    }
                }
                fail("cancellation ignored")
            } catch (_: IOException) {}
            assertEquals("replacement", destination.readText())
        } finally { root.deleteRecursively() }
    }
}
