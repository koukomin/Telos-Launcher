package com.telos.desktop.overlay

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class CaptureLockTest {
    @Test
    fun privateModeOnOwnerOnly() {
        val file = Files.createTempFile("shot", ".png").toFile()
        TelosCaptureTool.lockPrivate(file)
        assertTrue(file.canRead())
        assertTrue(file.canWrite())
        val posix = java.nio.file.Files.getPosixFilePermissions(file.toPath())
        assertTrue(posix.contains(java.nio.file.attribute.PosixFilePermission.OWNER_READ))
        assertTrue(!posix.contains(java.nio.file.attribute.PosixFilePermission.GROUP_READ))
        assertTrue(!posix.contains(java.nio.file.attribute.PosixFilePermission.OTHERS_READ))
    }
}
