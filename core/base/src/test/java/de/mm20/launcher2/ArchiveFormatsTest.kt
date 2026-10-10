package de.mm20.launcher2

import de.mm20.launcher2.helper.ArchiveFormats
import de.mm20.launcher2.helper.ArchiveKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveFormatsTest {
    @Test fun kinds() {
        assertEquals(ArchiveKind.Zip, ArchiveFormats.kindOf("a.ZIP"))
        assertEquals(ArchiveKind.Zip, ArchiveFormats.kindOf("book.epub"))
        assertEquals(ArchiveKind.SevenZ, ArchiveFormats.kindOf("a.7z"))
        assertEquals(ArchiveKind.TarGz, ArchiveFormats.kindOf("a.tar.gz"))
        assertEquals(ArchiveKind.TarGz, ArchiveFormats.kindOf("a.tgz"))
        assertEquals(ArchiveKind.Tar, ArchiveFormats.kindOf("a.tar"))
        assertEquals(ArchiveKind.Gz, ArchiveFormats.kindOf("log.txt.gz"))
        assertEquals(ArchiveKind.Ar, ArchiveFormats.kindOf("pkg.deb"))
        assertEquals(ArchiveKind.Cpio, ArchiveFormats.kindOf("a.cpio"))
        assertEquals(ArchiveKind.Arj, ArchiveFormats.kindOf("a.arj"))
    }

    @Test fun notOpened() {
        assertNull(ArchiveFormats.kindOf("a.rar"))
        assertNull(ArchiveFormats.kindOf("a.ace"))
        assertNull(ArchiveFormats.kindOf("a.iso"))
        assertNull(ArchiveFormats.kindOf("photo.jpg"))
        assertNull(ArchiveFormats.kindOf(".zip"))
        assertFalse(ArchiveFormats.canOpen("a.rar"))
        assertTrue(ArchiveFormats.isKnownUnsupported("a.RAR"))
        assertTrue(ArchiveFormats.isKnownUnsupported("a.ace"))
        assertFalse(ArchiveFormats.isKnownUnsupported("a.zip"))
    }

    @Test fun flags() {
        assertTrue(ArchiveKind.Gz.isSingleFile)
        assertFalse(ArchiveKind.TarGz.isSingleFile)
        assertTrue(ArchiveKind.TarXz.isTar)
        assertFalse(ArchiveKind.Zip.isTar)
    }

    @Test fun baseNames() {
        assertEquals("photos", ArchiveFormats.baseName("photos.tar.gz"))
        assertEquals("my.report", ArchiveFormats.baseName("my.report.zip"))
        assertEquals("x", ArchiveFormats.baseName("x.tgz"))
        assertEquals("log.txt", ArchiveFormats.baseName("log.txt.gz"))
        assertEquals("noext", ArchiveFormats.baseName("noext"))
    }

    @Test fun freeNames() {
        assertEquals("a.zip", ArchiveFormats.freeName("a.zip") { false })
        assertEquals("a (1).zip", ArchiveFormats.freeName("a.zip") { it == "a.zip" })
        assertEquals("a (2).zip", ArchiveFormats.freeName("a.zip") { it == "a.zip" || it == "a (1).zip" })
        assertEquals("a (1).tar.gz", ArchiveFormats.freeName("a.tar.gz") { it == "a.tar.gz" })
        assertEquals("noext (1)", ArchiveFormats.freeName("noext") { it == "noext" })
    }

    @Test fun extensions() {
        assertEquals("a.zip", ArchiveFormats.withExtension("a", ".zip"))
        assertEquals("a.zip", ArchiveFormats.withExtension("a.ZIP", ".zip"))
        assertEquals("a.tar.gz", ArchiveFormats.withExtension("a", ".tar.gz"))
        assertEquals("archive.7z", ArchiveFormats.withExtension("  ", ".7z"))
    }
}
