package com.telos.desktop.overlay

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermission

class CaptureLockTest {
    @Test
    fun privateModeOnOwnerOnly() {
        val file = Files.createTempFile("shot", ".png").toFile()
        TelosCaptureTool.lockPrivate(file)
        assertTrue(file.canRead())
        assertTrue(file.canWrite())
        val path = file.toPath()
        // Windows/NTFS (and some CI hosts) have no POSIX mode bits — lockPrivate is best-effort there.
        if (!Files.getFileStore(path).supportsFileAttributeView(PosixFileAttributeView::class.java)) {
            return
        }
        val posix = Files.getPosixFilePermissions(path)
        assertTrue(posix.contains(PosixFilePermission.OWNER_READ))
        assertTrue(!posix.contains(PosixFilePermission.GROUP_READ))
        assertTrue(!posix.contains(PosixFilePermission.OTHERS_READ))
    }
}
