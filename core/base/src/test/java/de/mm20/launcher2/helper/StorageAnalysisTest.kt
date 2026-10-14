package de.mm20.launcher2.helper

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class StorageAnalysisTest {
    @Test fun findsOnlyByteIdenticalFilesAndDoesNotFollowLinks() {
        val root = Files.createTempDirectory("telos-analysis").toFile()
        try {
            File(root, "a").writeText("same")
            File(root, "b").writeText("same")
            File(root, "c").writeText("diff")
            Files.createSymbolicLink(File(root, "loop").toPath(), root.toPath())
            val scan = StorageAnalysis.scan(root)
            assertEquals(3, scan.files.size)
            assertEquals(12L, scan.bytes)
            val duplicates = StorageAnalysis.duplicates(scan.files)
            assertEquals(1, duplicates.size)
            assertEquals(setOf("a", "b"), duplicates.single().map { it.file.name }.toSet())
        } finally { root.deleteRecursively() }
    }
    @Test fun changedFilesAreExcludedAndScanIsBounded() {
        val root = Files.createTempDirectory("telos-analysis").toFile()
        try {
            File(root, "a").writeText("same"); File(root, "b").writeText("same")
            val scan = StorageAnalysis.scan(root)
            File(root, "a").appendText(" changed")
            assertTrue(StorageAnalysis.duplicates(scan.files).isEmpty())
            assertTrue(StorageAnalysis.scan(root, limit = 1).truncated)
        } finally { root.deleteRecursively() }
    }
    @Test fun reusesBuffersAcrossLargeDuplicateGroupsWithoutMixingContents() {
        val root = Files.createTempDirectory("telos-analysis-buffers").toFile()
        try {
            val first = ByteArray(150_000) { (it % 251).toByte() }
            val second = first.clone().also { it[it.lastIndex] = (it.last() + 1).toByte() }
            repeat(3) { root.resolve("first-$it").writeBytes(first) }
            repeat(2) { root.resolve("second-$it").writeBytes(second) }
            // A later, short group must not retain bytes from the previous group's buffer.
            root.resolve("short-a").writeText("short")
            root.resolve("short-b").writeText("short")
            val groups = StorageAnalysis.duplicates(StorageAnalysis.scan(root).files)
            assertEquals(setOf(setOf("first-0", "first-1", "first-2"),
                setOf("second-0", "second-1"), setOf("short-a", "short-b")),
                groups.map { group -> group.map { it.file.name }.toSet() }.toSet())
        } finally { root.deleteRecursively() }
    }
    @Test fun cancellationInsideHashingPropagatesImmediately() {
        val root = Files.createTempDirectory("telos-analysis-cancel").toFile()
        try {
            root.resolve("a").writeText("same"); root.resolve("b").writeText("same")
            val records = StorageAnalysis.scan(root).files
            val cancelled = java.util.concurrent.CancellationException("cancelled")
            var checkpoints = 0
            try {
                StorageAnalysis.duplicates(records) { if (++checkpoints == 2) throw cancelled }
                fail("Hashing swallowed cancellation")
            } catch (actual: java.util.concurrent.CancellationException) {
                assertSame(cancelled, actual)
                assertEquals(2, checkpoints)
            }
        } finally { root.deleteRecursively() }
    }
}
