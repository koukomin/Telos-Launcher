package de.mm20.launcher2.helper

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class FileSearchIndexTest {
    @Test fun indexesMetadataWithoutFollowingLinksAndTracksHiddenAncestors() {
        val root = Files.createTempDirectory("search-index").toFile()
        val outside = Files.createTempDirectory("search-outside").toFile()
        try {
            root.resolve(".hidden").mkdir(); root.resolve(".hidden/inside.txt").writeText("hidden")
            root.resolve("shown.txt").writeText("data"); outside.resolve("outside.txt").writeText("private")
            Files.createSymbolicLink(root.resolve("link").toPath(), outside.toPath())
            val index = FileSearchIndex.scan(root)
            assertTrue(index.files.single { it.name == "inside.txt" }.hidden)
            assertFalse(index.files.any { it.name == "outside.txt" })
            assertTrue(index.files.single { it.name == "link" }.symbolicLink)
            assertEquals(4L, index.files.single { it.name == "shown.txt" }.size)
        } finally { root.deleteRecursively(); outside.deleteRecursively() }
    }
    @Test fun filtersUnknownSizesAndDirectoriesRatherThanInventingValues() {
        val filter = FileSearchFilter(setOf("txt"), minimumBytes = 4, maximumBytes = 8, modifiedAfter = 100)
        assertTrue(filter.matches(IndexedFile("a", "a.TXT", false, 4, 100, false, false)))
        assertFalse(filter.matches(IndexedFile("b", "b.txt", false, -1, 100, false, false)))
        assertFalse(filter.matches(IndexedFile("c", "c.txt", true, -1, 100, false, false)))
        assertFalse(filter.matches(IndexedFile("d", "d.txt", false, 4, 99, false, false)))
    }
    @Test fun stopsAtTheScanLimitAndInvalidatesCachedMetadataAfterChanges() {
        val root = Files.createTempDirectory("search-limit").toFile()
        try {
            repeat(6) { root.resolve("$it.txt").writeText("x") }
            assertEquals(3, FileSearchIndex.scan(root, limit = 3).files.size)
            assertTrue(FileSearchIndex.scan(root, limit = 3).limited)
            assertEquals(6, FileSearchIndex.read(root).files.size)
            root.resolve("new.txt").writeText("new"); FileSearchIndex.invalidate(root.path)
            assertEquals(7, FileSearchIndex.read(root).files.size)
        } finally { FileSearchIndex.invalidate(root.path); root.deleteRecursively() }
    }
    @Test fun aSmallMemoryBudgetStopsBeforeRetainingLongMetadata() {
        val root = Files.createTempDirectory("search-memory").toFile()
        try {
            repeat(6) { root.resolve("long-filename-$it.txt").writeText("x") }
            val result = FileSearchIndex.scan(root, memoryLimitBytes = 512)
            assertTrue(result.limited); assertTrue(result.estimatedBytes <= 512); assertTrue(result.files.size < 6)
        } finally { root.deleteRecursively() }
    }
    @Test fun hiddenAncestryEndsAtTheDirectoryBoundaryAndExcludesTheScanRoot() {
        val parent = Files.createTempDirectory("search-hidden-root").toFile()
        val root = parent.resolve(".root").also { it.mkdir() }
        try {
            root.resolve(".hidden/deep").mkdirs()
            root.resolve(".hidden/deep/file.txt").writeText("hidden")
            root.resolve("visible/deep").mkdirs()
            root.resolve("visible/deep/file.txt").writeText("shown")
            val entries = FileSearchIndex.scan(root).files
            assertTrue(entries.filter { it.path.contains("/.hidden/") }.all { it.hidden })
            assertTrue(entries.filter { it.path.contains("/visible") }.all { !it.hidden })
        } finally { parent.deleteRecursively() }
    }
}
