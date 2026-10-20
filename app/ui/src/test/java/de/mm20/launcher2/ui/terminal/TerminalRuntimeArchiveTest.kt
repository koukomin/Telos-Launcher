package de.mm20.launcher2.ui.terminal

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class TerminalRuntimeArchiveTest {
    private fun zip(vararg files: Pair<String, String>): ByteArrayInputStream {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { out -> files.forEach { (name, text) ->
            out.putNextEntry(ZipEntry(name)); out.write(text.toByteArray()); out.closeEntry()
        } }
        return ByteArrayInputStream(bytes.toByteArray())
    }
    @Test fun rejectsTraversalWithoutChangingSiblingFiles() {
        val parent = Files.createTempDirectory("runtime-test").toFile()
        try {
            val sibling = File(parent, "secret").apply { writeText("keep") }
            assertThrows(IOException::class.java) {
                TerminalRuntimeArchive.extract(zip("../secret" to "replace"), File(parent, "runtime"))
            }
            assertEquals("keep", sibling.readText())
        } finally { parent.deleteRecursively() }
    }
    @Test fun refusesExistingSymlinksThatEscapeTheRuntime() {
        val parent = Files.createTempDirectory("runtime-test").toFile()
        try {
            val root = File(parent, "runtime").apply { mkdir() }
            val outside = File(parent, "outside").apply { mkdir() }
            Files.createSymbolicLink(File(root, "bin").toPath(), outside.toPath())
            assertThrows(IOException::class.java) {
                TerminalRuntimeArchive.extract(zip("bin/bash" to "bad"), root)
            }
            assertFalse(File(outside, "bash").exists())
        } finally { parent.deleteRecursively() }
    }
    @Test fun countsActualExpandedBytesAndEntries() {
        val parent = Files.createTempDirectory("runtime-test").toFile()
        try {
            assertThrows(IOException::class.java) {
                TerminalRuntimeArchive.extract(zip("one" to "12345", "two" to "12345"), File(parent, "total"), maxBytes = 8)
            }
            assertThrows(IOException::class.java) {
                TerminalRuntimeArchive.extract(zip("one" to "12345"), File(parent, "entry"), maxEntryBytes = 4)
            }
            assertThrows(IOException::class.java) {
                TerminalRuntimeArchive.extract(zip("one" to "1", "two" to "2"), File(parent, "count"), maxEntries = 1)
            }
            TerminalRuntimeArchive.extract(zip("bin/bash" to "sample", "lib/data" to "ok"), File(parent, "valid"))
            assertEquals("sample", File(parent, "valid/bin/bash").readText())
            assertEquals("ok", File(parent, "valid/lib/data").readText())
        } finally { parent.deleteRecursively() }
    }
}
