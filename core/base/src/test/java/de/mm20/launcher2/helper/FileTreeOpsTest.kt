package de.mm20.launcher2.helper

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class FileTreeOpsTest {
    @Test fun deletingAParentNeverDeletesTheTargetOfAChildLink() {
        val root = Files.createTempDirectory("delete-links").toFile()
        val outside = Files.createTempDirectory("outside-links").toFile()
        try {
            outside.resolve("keep.txt").writeText("keep"); root.resolve("delete.txt").writeText("delete")
            Files.createSymbolicLink(root.resolve("link").toPath(), outside.toPath())
            assertEquals(6L, FileTreeOps.size(root)); FileTreeOps.delete(root)
            assertFalse(root.exists()); assertEquals("keep", outside.resolve("keep.txt").readText())
        } finally { if (root.exists()) FileTreeOps.delete(root); outside.deleteRecursively() }
    }
    @Test fun deletingOneLinkLeavesItsTargetIntact() {
        val root = Files.createTempDirectory("one-link").toFile()
        try {
            val target = root.resolve("target.txt").apply { writeText("keep") }; val link = root.resolve("link")
            Files.createSymbolicLink(link.toPath(), target.toPath()); FileTreeOps.delete(link)
            assertEquals("keep", target.readText()); assertFalse(Files.isSymbolicLink(link.toPath()))
        } finally { root.deleteRecursively() }
    }
}
