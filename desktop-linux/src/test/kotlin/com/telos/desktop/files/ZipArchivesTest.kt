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

    @Test
    fun encryptedZipNeedsPassword() {
        val root = Files.createTempDirectory("telos-ezip").toFile()
        val src = File(root, "secret.txt").apply { writeText("hidden") }
        val zip = File(root, "secret.zip")
        val pw = "s3cret".toCharArray()
        net.lingala.zip4j.ZipFile(zip, pw).use { z ->
            val params = net.lingala.zip4j.model.ZipParameters().apply {
                isEncryptFiles = true
                encryptionMethod = net.lingala.zip4j.model.enums.EncryptionMethod.AES
            }
            z.addFile(src, params)
        }
        try {
            ZipArchives.listChildren(zip, "")
            org.junit.Assert.fail("expected password")
        } catch (_: ArchivePasswordException) {
        }
        val kids = ZipArchives.listChildren(zip, "", pw)
        assertEquals(listOf("secret.txt"), kids.map { it.name })
        val out = File(root, "out")
        assertTrue(ZipArchives.extractAll(zip, out, pw))
        assertEquals("hidden", File(out, "secret.txt").readText())
    }

    @Test
    fun splitZipRootAndBrowse() {
        val mapped = File("/tmp/pack.z01")
        assertEquals(File("/tmp/pack.zip"), ZipArchives.archiveRoot(mapped))
        assertEquals(File("/tmp/pack.zip"), ZipArchives.archiveRoot(File("/tmp/pack.zip.001")))
        val root = Files.createTempDirectory("telos-split").toFile()
        val src = File(root, "blob.bin").apply { writeBytes(ByteArray(80_000) { 1 }) }
        val zip = File(root, "blob.zip")
        net.lingala.zip4j.ZipFile(zip).use { z ->
            z.createSplitZipFile(
                listOf(src),
                net.lingala.zip4j.model.ZipParameters(),
                true,
                65_536,
            )
        }
        val z01 = File(root, "blob.z01")
        assertTrue(z01.isFile || ZipArchives.isSplitZip(zip))
        assertEquals(zip.canonicalFile, ZipArchives.archiveRoot(if (z01.isFile) z01 else zip).canonicalFile)
        val kids = ZipArchives.listChildren(if (z01.isFile) z01 else zip, "")
        assertTrue(kids.any { it.name == "blob.bin" })
        assertEquals(null, ZipArchives.safeInner("..\\ace"))
        assertTrue(!ZipArchives.isArchive(File(root, "x.ace").apply { writeText("x") }))
    }
}
