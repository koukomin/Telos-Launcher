package de.mm20.launcher2.downloads

import de.mm20.launcher2.downloads.logic.FileNames
import org.junit.Assert.*
import org.junit.Test

class FileNamesTest {
    @Test fun plainFilename() {
        assertEquals("a.zip", FileNames.fromContentDisposition("attachment; filename=\"a.zip\""))
        assertEquals("a.zip", FileNames.fromContentDisposition("attachment; filename=a.zip"))
    }

    @Test fun extendedFilenameWins() {
        val h = "attachment; filename=\"fallback.txt\"; filename*=UTF-8''%CE%B1%CF%81%CF%87%CE%B5%CE%AF%CE%BF.pdf"
        assertEquals("αρχείο.pdf", FileNames.fromContentDisposition(h))
    }

    @Test fun semicolonInsideQuotes() {
        assertEquals("a;b.zip", FileNames.fromContentDisposition("attachment; filename=\"a;b.zip\""))
    }

    @Test fun noFilename() {
        assertNull(FileNames.fromContentDisposition("inline"))
        assertNull(FileNames.fromContentDisposition(null))
    }

    @Test fun pathTraversalIsRemoved() {
        assertEquals("passwd", FileNames.fromContentDisposition("attachment; filename=\"../../etc/passwd\""))
        assertEquals("evil.sh", FileNames.fromContentDisposition("attachment; filename=\"..\\\\evil.sh\""))
    }

    @Test fun fromUrl() {
        assertEquals("file.iso", FileNames.fromUrl("https://example.com/dir/file.iso?token=1#x"))
        assertEquals("my file.zip", FileNames.fromUrl("https://example.com/my%20file.zip"))
        assertNull(FileNames.fromUrl("https://example.com/"))
        assertNull(FileNames.fromUrl("https://example.com"))
    }

    @Test fun resolveOrder() {
        assertEquals("cd.zip", FileNames.resolve("attachment; filename=cd.zip", "https://x.org/u.zip", null))
        assertEquals("u.zip", FileNames.resolve(null, "https://x.org/u.zip", null))
        assertEquals("download", FileNames.resolve(null, "https://x.org/", null))
    }

    @Test fun extensionFromMimeWhenMissing() {
        assertEquals("video.mp4", FileNames.resolve(null, "https://x.org/video", "video/mp4"))
        assertEquals("video", FileNames.resolve(null, "https://x.org/video", "application/octet-stream"))
        assertEquals("doc.pdf", FileNames.resolve(null, "https://x.org/doc", "application/pdf; charset=binary"))
    }

    @Test fun sanitize() {
        assertEquals("a_b_c", FileNames.sanitize("a:b*c"))
        assertEquals("hidden", FileNames.sanitize(".hidden"))
        assertTrue(FileNames.sanitize("x".repeat(500) + ".zip").length <= 200)
        assertTrue(FileNames.sanitize("x".repeat(500) + ".zip").endsWith(".zip"))
    }

    @Test fun unique() {
        val taken = setOf("a.zip", "a (1).zip")
        assertEquals("a (2).zip", FileNames.unique("a.zip") { it in taken })
        assertEquals("b.zip", FileNames.unique("b.zip") { it in taken })
        assertEquals("noext (1)", FileNames.unique("noext") { it == "noext" })
    }
}
