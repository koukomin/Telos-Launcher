package com.telos.desktop.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UnlockTheatreTest {
    @Test
    fun lockedBootNeverAllowsWallpaper() {
        assertFalse(UnlockTheatre.allowsUserWallpaper(unlocked = false))
        assertTrue(UnlockTheatre.allowsUserWallpaper(unlocked = true))
    }

    @Test
    fun resolveWallpaperRejectsWhileLocked() {
        val root = File.createTempFile("telos-vault-root", "").also {
            it.delete()
            it.mkdirs()
        }
        try {
            val img = File(root, "wall.png").also { it.writeBytes(byteArrayOf(1, 2, 3)) }
            assertNull(
                UnlockTheatre.resolveWallpaperFile(
                    unlocked = false,
                    vaultRoot = root,
                    wallpaperPath = img.name,
                ),
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun resolveWallpaperAcceptsVaultRelativeImage() {
        val root = File.createTempFile("telos-vault-root", "").also {
            it.delete()
            it.mkdirs()
        }
        try {
            val img = File(root, "Pictures/wall.jpg").also {
                it.parentFile?.mkdirs()
                it.writeBytes(byteArrayOf(1, 2, 3, 4))
            }
            val resolved = UnlockTheatre.resolveWallpaperFile(
                unlocked = true,
                vaultRoot = root,
                wallpaperPath = "Pictures/wall.jpg",
            )
            assertNotNull(resolved)
            assertEquals(img.canonicalFile, resolved!!.canonicalFile)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun resolveWallpaperRejectsHostEscape() {
        val root = File.createTempFile("telos-vault-root", "").also {
            it.delete()
            it.mkdirs()
        }
        val outside = File.createTempFile("telos-outside", ".png").also {
            it.writeBytes(byteArrayOf(9))
        }
        try {
            assertNull(
                UnlockTheatre.resolveWallpaperFile(
                    unlocked = true,
                    vaultRoot = root,
                    wallpaperPath = outside.absolutePath,
                ),
            )
            assertNull(
                UnlockTheatre.resolveWallpaperFile(
                    unlocked = true,
                    vaultRoot = root,
                    wallpaperPath = "../${outside.name}",
                ),
            )
        } finally {
            outside.delete()
            root.deleteRecursively()
        }
    }

    @Test
    fun revealAlphasCrossfadeWithoutParticles() {
        val start = UnlockTheatre.revealAlphas(0f)
        assertEquals(1f, start.quietOpsScrim, 0.001f)
        assertEquals(0f, start.wallpaper, 0.001f)
        val mid = UnlockTheatre.revealAlphas(0.5f)
        assertTrue(mid.quietOpsScrim in 0.4f..0.6f)
        assertTrue(mid.wallpaper in 0.4f..0.6f)
        val end = UnlockTheatre.revealAlphas(1f)
        assertEquals(0f, end.quietOpsScrim, 0.001f)
        assertEquals(1f, end.wallpaper, 0.001f)
        assertEquals(1f, end.intensityMix, 0.001f)
    }
}
