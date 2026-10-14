package de.mm20.launcher2.helper

import net.lingala.zip4j.ZipFile
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.Random

class SplitZipArchiveTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun fixture(): Pair<File, ByteArray> {
        val bytes = ByteArray(220_000).also { Random(29).nextBytes(it) }
        return temporary.newFile("input.bin").apply { writeBytes(bytes) } to bytes
    }

    @Test fun multipleVolumesRoundTripByStreamingEntry() {
        val (source, expected) = fixture()
        val target = File(temporary.root, "bundle.zip")
        var copied = 0L
        val volumes = SplitZipArchive.create(listOf(source), target, 65_536, progress = { copied += it })
        assertTrue(volumes.size >= 4)
        assertEquals(expected.size.toLong(), copied)
        assertTrue(File(temporary.root, "bundle.z01").exists())
        ZipFile(target).use { zip ->
            assertTrue(zip.isSplitArchive)
            zip.getInputStream(zip.getFileHeader(source.name)).use { assertArrayEquals(expected, it.readBytes()) }
        }
    }

    @Test fun aesEncryptedSplitZipRoundTrips() {
        val (source, expected) = fixture()
        val target = File(temporary.root, "secret.zip")
        val password = "A long temporary passphrase".toCharArray()
        SplitZipArchive.create(listOf(source), target, 65_536, password)
        assertEquals('A', password.first()) // caller keeps ownership of its password
        ZipFile(target, password).use { zip ->
            assertTrue(zip.isEncrypted)
            zip.getInputStream(zip.getFileHeader(source.name)).use { assertArrayEquals(expected, it.readBytes()) }
        }
    }

    @Test fun existingVolumeIsNeverOverwritten() {
        val (source, _) = fixture()
        val existing = temporary.newFile("bundle.z01").apply { writeText("keep me") }
        try {
            SplitZipArchive.create(listOf(source), File(temporary.root, "bundle.zip"), 65_536)
            fail("Existing volume accepted")
        } catch (_: IOException) { }
        assertEquals("keep me", existing.readText())
        assertFalse(File(temporary.root, "bundle.zip").exists())
    }

    @Test fun cancellationRemovesOnlyItsOwnStagedVolumes() {
        val (source, _) = fixture()
        val other = temporary.newFile("other.zip").apply { writeText("unrelated") }
        var copied = 0L
        try {
            SplitZipArchive.create(listOf(source), File(temporary.root, "bundle.zip"), 65_536,
                checkpoint = { if (copied >= 65_536) throw IOException("cancelled") }, progress = { copied += it })
            fail("Cancellation ignored")
        } catch (_: IOException) { }
        assertEquals("unrelated", other.readText())
        assertEquals(setOf(source.name, other.name), temporary.root.list()!!.toSet())
    }

    @Test fun sourceDirectoryWithLinkIsRejectedWithoutFollowingTarget() {
        val outside = temporary.newFile("outside").apply { writeText("never follow") }
        val source = temporary.newFolder("folder")
        Files.createSymbolicLink(File(source, "link").toPath(), outside.toPath())
        try {
            SplitZipArchive.create(listOf(source), File(temporary.root, "bundle.zip"), 65_536)
            fail("Symbolic link accepted")
        } catch (_: IOException) { }
        assertEquals("never follow", outside.readText())
        assertFalse(File(temporary.root, "bundle.zip").exists())
    }

    @Test fun outputInsideSourceDirectoryIsRejected() {
        val source = temporary.newFolder("folder")
        try {
            SplitZipArchive.create(listOf(source), File(source, "bundle.zip"), 65_536)
            fail("Recursive output accepted")
        } catch (_: IllegalArgumentException) { }
        assertTrue(source.list()!!.isEmpty())
    }

    @Test fun cancellationDuringPublicationPreservesAnotherWritersReplacement() {
        val (source, _) = fixture()
        val replacement = temporary.newFile("replacement.tmp").apply { writeText("another writer") }
        var changed: File? = null
        var failure: IOException? = null
        try {
            SplitZipArchive.create(listOf(source), File(temporary.root, "bundle.zip"), 65_536, checkpoint = {
                val published = temporary.root.listFiles()!!.firstOrNull { it.name.matches(Regex("bundle\\.z[0-9]+")) }
                if (published != null) {
                    Files.move(replacement.toPath(), published.toPath(), REPLACE_EXISTING)
                    changed = published
                    throw IOException("cancelled after publication")
                }
            })
            fail("Cancellation ignored")
        } catch (error: IOException) { failure = error }
        assertNotNull(changed)
        assertEquals("another writer", changed!!.readText())
        assertTrue(failure!!.suppressed.any { it.message!!.contains("retained") })
        assertFalse(File(temporary.root, "bundle.zip").exists())
    }
}
