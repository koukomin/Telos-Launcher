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
}
