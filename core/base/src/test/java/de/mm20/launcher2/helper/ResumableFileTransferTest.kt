package de.mm20.launcher2.helper

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class ResumableFileTransferTest {
    private fun fixture(block: (java.io.File, java.io.File) -> Unit) {
        val root = Files.createTempDirectory("transfer-test").toFile()
        try { val src = root.resolve("source").apply { mkdir() }; val dst = root.resolve("destination").apply { mkdir() }; block(src, dst) }
        finally { root.deleteRecursively() }
    }
    @Test fun pausedFileResumesFromVerifiedPrefix() = fixture { src, dst ->
        val bytes = ByteArray(600_000) { (it % 251).toByte() }
        val source = src.resolve("sample").apply { writeBytes(bytes) }
        val plan = ResumableFileTransfer.plan(listOf(source), dst, false)
        var pause = false
        try { ResumableFileTransfer.execute(plan, { if (pause) throw ResumableFileTransfer.Paused() }, {}, { done, _ -> if (done > 130_000) pause = true }); fail() }
        catch (_: ResumableFileTransfer.Paused) { }
        assertFalse(dst.resolve("sample").exists())
        assertTrue(dst.listFiles().orEmpty().any { it.name.endsWith(".part") })
        ResumableFileTransfer.execute(plan, {}, {}, { _, _ -> })
        assertArrayEquals(bytes, dst.resolve("sample").readBytes())
        assertArrayEquals(bytes, source.readBytes())
    }
    @Test fun changedSourceIsNeverDeletedOnMove() = fixture { src, dst ->
        val source = src.resolve("sample").apply { writeText("original") }
        val plan = ResumableFileTransfer.plan(listOf(source), dst, true)
        source.writeText("changed source")
        try { ResumableFileTransfer.execute(plan, {}, {}, { _, _ -> }); fail() }
        catch (_: ResumableFileTransfer.SourceChanged) { }
        assertEquals("changed source", source.readText())
        assertFalse(dst.resolve("sample").exists())
    }
    @Test fun concurrentNewChildrenRemainInOriginalFolder() = fixture { src, dst ->
        src.resolve("old").writeText("old")
        val plan = ResumableFileTransfer.plan(listOf(src), dst, true)
        src.resolve("new").writeText("new")
        try { ResumableFileTransfer.execute(plan, {}, {}, { _, _ -> }); fail() }
        catch (_: ResumableFileTransfer.OriginalRetained) { }
        assertEquals("new", src.resolve("new").readText())
        assertFalse(src.resolve("old").exists())
        assertEquals("old", dst.resolve("source/old").readText())
    }
    @Test fun newDestinationNeverOverwritten() = fixture { src, dst ->
        val source = src.resolve("sample").apply { writeText("source") }
        val plan = ResumableFileTransfer.plan(listOf(source), dst, true)
        dst.resolve("sample").writeText("keep")
        try { ResumableFileTransfer.execute(plan, {}, {}, { _, _ -> }); fail() }
        catch (_: java.io.IOException) { }
        assertEquals("keep", dst.resolve("sample").readText())
        assertEquals("source", source.readText())
    }
    @Test fun mutatedResumePrefixFailsEvenWithSameSizeAndTimestamp() = fixture { src, dst ->
        val source = src.resolve("sample").apply { writeBytes(ByteArray(400_000) { 1 }) }
        val plan = ResumableFileTransfer.plan(listOf(source), dst, false)
        var pause = false
        try { ResumableFileTransfer.execute(plan, { if (pause) throw ResumableFileTransfer.Paused() }, {}, { _, _ -> pause = true }); fail() }
        catch (_: ResumableFileTransfer.Paused) { }
        val modified = source.lastModified()
        java.io.RandomAccessFile(source, "rw").use { it.write(2) }
        source.setLastModified(modified)
        try { ResumableFileTransfer.execute(plan, {}, {}, { _, _ -> }); fail() }
        catch (_: ResumableFileTransfer.SourceChanged) { }
        assertTrue(source.exists()); assertFalse(dst.resolve("sample").exists())
    }
    @Test fun symbolicLinksCannotEscapeManifest() = fixture { src, dst ->
        val outside = src.parentFile.resolve("outside").apply { writeText("keep") }
        Files.createSymbolicLink(src.resolve("link").toPath(), outside.toPath())
        try { ResumableFileTransfer.plan(listOf(src), dst, true); fail() }
        catch (_: java.io.IOException) { }
        assertEquals("keep", outside.readText())
    }
    @Test fun publishedFileWithPreparedDigestRecoversAfterJournalInterruption() = fixture { src, dst ->
        val source = src.resolve("sample").apply { writeText("original") }
        val plan = ResumableFileTransfer.plan(listOf(source), dst, true)
        var prepared: String? = null
        try {
            ResumableFileTransfer.execute(plan, {}, {
                val entry = plan.entries.single()
                if (!entry.committed) prepared = entry.digest else throw java.io.IOException("Process interrupted before commit log")
            }, { _, _ -> }); fail()
        } catch (_: java.io.IOException) { }
        assertTrue(source.exists())
        assertEquals("original", dst.resolve("sample").readText())
        val restored = plan.copy(entries = plan.entries.map { it.copy(committed = false, digest = prepared) })
        ResumableFileTransfer.execute(restored, {}, {}, { _, _ -> })
        assertFalse(source.exists())
        assertEquals("original", dst.resolve("sample").readText())
    }
    @Test fun ownedDirectoryRecoversAfterJournalInterruption() = fixture { src, dst ->
        src.resolve("sample").writeText("original")
        val plan = ResumableFileTransfer.plan(listOf(src), dst, false)
        try {
            ResumableFileTransfer.execute(plan, {}, { throw java.io.IOException("Process interrupted after directory creation") }, { _, _ -> }); fail()
        } catch (_: java.io.IOException) { }
        val restored = plan.copy(entries = plan.entries.map { it.copy(committed = false) })
        ResumableFileTransfer.execute(restored, {}, {}, { _, _ -> })
        assertEquals("original", dst.resolve("source/sample").readText())
        assertFalse(dst.resolve("source").listFiles().orEmpty().any { it.name.endsWith(".owner") })
    }

    @Test fun selectedTopLevelSymbolicLinkIsRejectedBeforeCanonicalization() = fixture { src, dst ->
        val outside = src.parentFile.resolve("outside").apply { writeText("keep") }
        val link = src.resolve("link")
        Files.createSymbolicLink(link.toPath(), outside.toPath())
        try { ResumableFileTransfer.plan(listOf(link), dst, true); fail() }
        catch (_: java.io.IOException) { }
        assertEquals("keep", outside.readText()); assertTrue(Files.isSymbolicLink(link.toPath()))
        assertTrue(dst.listFiles().orEmpty().isEmpty())
    }

}
