package com.telos.desktop.overlay

import com.telos.desktop.vault.PortableEnvironmentIsolator
import com.telos.desktop.vault.PortableTraceGuard
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermission

class CaptureLockTest {
    @After
    fun tearDown() {
        PortableEnvironmentIsolator.clear()
        PortableTraceGuard.clearSelectedRoot()
        TelosCaptureTool.copyToClipboard = TelosCaptureTool.envClipboardEnabled()
        TelosCaptureTool.redactBeforeSave = false
        TelosCaptureTool.menuOpen = false
        TelosCaptureTool.cancelPending()
    }

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
        assertTrue(posix.contains(PosixFilePermission.OWNER_WRITE))
        assertTrue(!posix.contains(PosixFilePermission.GROUP_READ))
        assertTrue(!posix.contains(PosixFilePermission.OTHERS_READ))
        assertTrue(!posix.contains(PosixFilePermission.GROUP_WRITE))
        assertTrue(!posix.contains(PosixFilePermission.OTHERS_WRITE))
    }

    @Test
    fun screenshotsDirUnderVaultHome() {
        val vault = Files.createTempDirectory("telos-vault-home").toFile()
        PortableEnvironmentIsolator.apply(vault, fullSessionHome = true)
        val dir = TelosCaptureTool.screenshotsDirectory()
        assertEquals(File(vault, "Pictures/Screenshots").canonicalFile, dir.canonicalFile)
        assertTrue(dir.isDirectory)
    }

    @Test
    fun screenshotsDirUnderPortableData() {
        val portable = Files.createTempDirectory("telos-portable").toFile()
        PortableTraceGuard.selectDataRoot(portable)
        System.setProperty("telos.portable.active", "1")
        val dir = TelosCaptureTool.screenshotsDirectory()
        assertEquals(File(portable, "Pictures/Screenshots").canonicalFile, dir.canonicalFile)
        assertTrue(dir.isDirectory)
    }

    @Test
    fun screenshotsDirHonorsHomeOverride() {
        val home = Files.createTempDirectory("telos-home-override").toFile()
        val dir = TelosCaptureTool.screenshotsDirectory(homeOverride = home)
        assertEquals(File(home, "Pictures/Screenshots").canonicalFile, dir.canonicalFile)
    }

    @Test
    fun clipboardDefaultOffWithoutEnv() {
        // Env is process-level; assert the UI toggle starts from env helper and default path is opt-in.
        TelosCaptureTool.copyToClipboard = false
        if (System.getenv("TELOS_SHOT_CLIPBOARD") == "1") {
            assertTrue(TelosCaptureTool.clipboardWanted())
        } else {
            assertFalse(TelosCaptureTool.clipboardWanted())
            TelosCaptureTool.copyToClipboard = true
            assertTrue(TelosCaptureTool.clipboardWanted())
            TelosCaptureTool.copyToClipboard = false
            assertFalse(TelosCaptureTool.clipboardWanted())
        }
    }

    @Test
    fun destCreatesOwnerOnlyPlaceholder() {
        val home = Files.createTempDirectory("telos-shot-dest").toFile()
        val file = TelosCaptureTool.dest(TelosCaptureTool.screenshotsDirectory(home))
        assertTrue(file.name.startsWith("telos-"))
        assertTrue(file.name.endsWith(".png"))
        assertTrue(file.parentFile!!.name == "Screenshots")
        assertTrue(file.parentFile!!.parentFile!!.name == "Pictures")
        val path = file.toPath()
        if (!Files.getFileStore(path).supportsFileAttributeView(PosixFileAttributeView::class.java)) {
            return
        }
        val posix = Files.getPosixFilePermissions(path)
        assertEquals(
            setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
            posix,
        )
    }
}
