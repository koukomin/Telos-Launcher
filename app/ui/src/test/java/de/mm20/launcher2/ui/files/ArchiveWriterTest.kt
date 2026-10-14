package de.mm20.launcher2.ui.files

import net.lingala.zip4j.ZipFile
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.nio.file.Files

class ArchiveWriterTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun write(format: CompressFormat, source: File, target: File, cancel: CancelFlag = CancelFlag(), progress: (Long) -> Unit = {}) {
        when (format) {
            CompressFormat.Zip -> ArchiveWriter.zipEncrypted(listOf(source), target, "password".toCharArray(), cancel, progress)
            CompressFormat.SevenZ -> ArchiveWriter.sevenZ(listOf(source), target, cancel, progress)
            CompressFormat.TarGz -> ArchiveWriter.tarGz(listOf(source), target, cancel, progress)
        }
    }

    @Test fun allFormatsRoundTrip() {
        val source = temporary.newFile("hello.txt").apply { writeText("Telos — Ελληνικά") }
        for (format in CompressFormat.values()) {
            val target = File(temporary.root, "archive${format.extension}")
            write(format, source, target)
            val bytes = when (format) {
                CompressFormat.Zip -> ZipFile(target, "password".toCharArray()).use { zip ->
                    zip.getInputStream(zip.getFileHeader(source.name)).use { it.readBytes() }
                }
                CompressFormat.SevenZ -> SevenZFile.builder().setFile(target).get().use { zip ->
                    assertEquals(source.name, zip.nextEntry.name)
                    ByteArray(source.length().toInt()).also { assertEquals(it.size, zip.read(it)) }
                }
                CompressFormat.TarGz -> TarArchiveInputStream(GzipCompressorInputStream(target.inputStream())).use { tar ->
                    assertEquals(source.name, tar.nextEntry.name)
                    tar.readBytes()
                }
            }
            assertArrayEquals(source.readBytes(), bytes)
        }
    }

    @Test fun everyFormatPreservesExistingDestination() {
        val source = temporary.newFile("source").apply { writeText("new contents") }
        for (format in CompressFormat.values()) {
            val target = temporary.newFile("archive${format.extension}").apply { writeText("keep") }
            try { write(format, source, target); fail("Existing destination accepted") } catch (_: IOException) { }
            assertEquals("keep", target.readText())
        }
    }

    @Test fun everyFormatRejectsLinksAndCleansStaging() {
        val outside = temporary.newFile("outside").apply { writeText("private") }
        val source = temporary.newFolder("folder")
        Files.createSymbolicLink(File(source, "link").toPath(), outside.toPath())
        for (format in CompressFormat.values()) {
            val target = File(temporary.root, "archive${format.extension}")
            try { write(format, source, target); fail("Symbolic link accepted") } catch (_: IOException) { }
            assertFalse(target.exists())
            assertFalse(temporary.root.list()!!.any { it.startsWith(".telos-archive-") })
            assertEquals("private", outside.readText())
        }
    }

    @Test fun everyFormatCleansCancelledWork() {
        val source = temporary.newFile("source").apply { writeBytes(ByteArray(200_000)) }
        for (format in CompressFormat.values()) {
            val target = File(temporary.root, "archive${format.extension}")
            val cancel = CancelFlag()
            try { write(format, source, target, cancel) { cancel.cancelled = true }; fail("Cancellation ignored") } catch (_: IOException) { }
            assertFalse(target.exists())
            assertFalse(temporary.root.list()!!.any { it.startsWith(".telos-archive-") })
        }
    }
}
