package com.telos.desktop.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ZipArchivesTest {
    @Test
    fun roundTripAndRejectsZipSlip() {
        val root = Files.createTempDirectory("telos-zip").toFile()
        val dir = File(root, "pack").apply { mkdirs() }
        File(dir, "a.txt").writeText("hello")
        File(dir, "sub").mkdirs()
        File(dir, "sub/b.txt").writeText("b")
        val zip = File(root, "pack.zip")
        assertTrue(ZipArchives.compressDir(dir, zip))
        val kids = ZipArchives.listChildren(zip, "")
        assertEquals(setOf("a.txt", "sub"), kids.map { it.name }.toSet())
        val nested = ZipArchives.listChildren(zip, "sub")
        assertEquals(listOf("b.txt"), nested.map { it.name })
        val out = File(root, "out")
        assertTrue(ZipArchives.extractAll(zip, out))
        assertEquals("hello", File(out, "a.txt").readText())
        assertFalse(ZipArchives.safeInner("../etc/passwd") != null && ZipArchives.safeInner("../etc/passwd")!!.contains(".."))
        assertEquals(null, ZipArchives.safeInner("../etc/passwd"))
        assertEquals(null, ZipArchives.safeInner("/abs"))
    }
}
