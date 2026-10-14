package de.mm20.launcher2.comms.files

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class FilesSharingServerTest {
    @Test fun traversalAndEncodedSeparatorsAreRejected() {
        val root = Files.createTempDirectory("share-root").toFile()
        try {
            val file = root.resolve("a+b.txt").apply { writeText("ok") }
            assertEquals(file, FilesSharingServer.resolve(root, "a%2Bb.txt"))
            for (path in listOf("..", "%2e%2e", "%2Fetc", "%5cetc", "%00", "missing")) {
                assertNull(path, FilesSharingServer.resolve(root, path))
            }
        } finally { root.deleteRecursively() }
    }
    @Test fun symlinkCannotExposeFilesOutsideSelectedFolder() {
        val root = Files.createTempDirectory("share-root").toFile()
        val outside = Files.createTempDirectory("share-outside").toFile()
        try {
            outside.resolve("private").writeText("secret")
            Files.createSymbolicLink(root.resolve("link").toPath(), outside.toPath())
            assertNull(FilesSharingServer.resolve(root, "link/private"))
            assertNull(FilesSharingServer.resolve(root, "link"))
            assertEquals(root, FilesSharingServer.resolve(root, ""))
        } finally { root.resolve("link").delete(); root.deleteRecursively(); outside.deleteRecursively() }
    }
}
