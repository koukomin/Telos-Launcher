package de.mm20.launcher2.helper

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class FileTrashTest {
    @Test fun restoresWithoutOverwritingAndExpiresOnlyAfterRetention() {
        val root = Files.createTempDirectory("telos-trash").toFile()
        try {
            var now = 1_000L
            val trash = FileTrash(File(root, "trash"), root) { now }
            val original = File(root, "document.txt").apply { writeText("original") }
            val entry = trash.moveToTrash(original, 30)
            assertFalse(original.exists())
            assertEquals("original", entry.file.readText())
            original.writeText("new file")
            assertTrue(runCatching { trash.restore(entry.id) }.isFailure)
            assertEquals("new file", original.readText())
            original.delete()
            assertEquals("original", trash.restore(entry.id).readText())
            val expiring = trash.moveToTrash(original, 7)
            now = expiring.expiresAt - 1
            assertEquals(0, trash.purgeExpired())
            now++
            assertEquals(1, trash.purgeExpired())
            assertTrue(trash.entries().isEmpty())
        } finally { root.deleteRecursively() }
    }
    @Test fun refusesOutsidePathsAndTrashAncestors() {
        val root = Files.createTempDirectory("telos-trash").toFile()
        val outside = Files.createTempFile("outside", ".txt").toFile()
        try {
            val trash = FileTrash(File(root, "nested/trash"), root)
            assertTrue(runCatching { trash.moveToTrash(outside, 30) }.isFailure)
            File(root, "nested").mkdirs()
            assertTrue(runCatching { trash.moveToTrash(File(root, "nested"), 30) }.isFailure)
            assertTrue(outside.exists())
        } finally { root.deleteRecursively(); outside.delete() }
    }
    @Test fun purgingDoesNotFollowSymlinksInsideTrashedFolders() {
        val root = Files.createTempDirectory("telos-trash").toFile()
        val outside = Files.createTempDirectory("outside").toFile()
        try {
            File(outside, "keep.txt").writeText("keep")
            val folder = File(root, "folder").apply { mkdirs() }
            Files.createSymbolicLink(File(folder, "link").toPath(), outside.toPath())
            val trash = FileTrash(File(root, "trash"), root)
            val entry = trash.moveToTrash(folder, 30)
            assertTrue(trash.deletePermanently(entry.id))
            assertEquals("keep", File(outside, "keep.txt").readText())
        } finally { root.deleteRecursively(); outside.deleteRecursively() }
    }

}
