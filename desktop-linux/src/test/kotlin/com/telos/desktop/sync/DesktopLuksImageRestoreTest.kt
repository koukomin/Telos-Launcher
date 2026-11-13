package com.telos.desktop.sync

import com.telos.desktop.update.DesktopSignedUpdate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class DesktopLuksImageRestoreTest {
    @Test
    fun refusesPreviewAndApplyWhenVaultUnlocked() {
        val root = createTempDirectory("telos-luks-restore").toFile()
        try {
            val backup = File(root, "backup-telos_vault.img").apply {
                writeBytes(ByteArray(4096) { 7 })
            }
            val dest = File(root, "telos_vault.img")
            val preview = DesktopLuksImageRestore.preview(
                backup = backup,
                destImage = dest,
                vaultUnlocked = true,
            )
            assertTrue(preview.isFailure)
            assertTrue(
                preview.exceptionOrNull()?.message?.contains("locked", ignoreCase = true) == true,
            )

            val apply = DesktopLuksImageRestore.applyConfirmed(
                backup = backup,
                destImage = dest,
                expectedSha256 = DesktopSignedUpdate.sha256Hex(backup),
                confirmToken = DesktopLuksImageRestore.CONFIRM_PREFIX + "x",
                expectedToken = DesktopLuksImageRestore.CONFIRM_PREFIX + "x",
                vaultUnlocked = true,
            )
            assertTrue(apply.isFailure)
            assertTrue(
                apply.exceptionOrNull()?.message?.contains("locked", ignoreCase = true) == true,
            )
            assertTrue(!dest.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun confirmedRestoreCopiesImageWithSha256WhenLocked() {
        val root = createTempDirectory("telos-luks-restore-ok").toFile()
        try {
            val payload = ByteArray(8192) { (it % 251).toByte() }
            val backup = File(root, "from-backup.img").apply { writeBytes(payload) }
            val dest = File(root, "portable/telos_vault.img")
            val sha = DesktopSignedUpdate.sha256Hex(backup)

            val preview = DesktopLuksImageRestore.preview(
                backup = backup,
                destImage = dest,
                vaultUnlocked = false,
            ).getOrThrow()
            assertEquals(sha, preview.backupSha256)
            assertTrue(preview.confirmToken.startsWith(DesktopLuksImageRestore.CONFIRM_PREFIX))

            val denied = DesktopLuksImageRestore.applyConfirmed(
                backup = backup,
                destImage = dest,
                expectedSha256 = sha,
                confirmToken = "wrong",
                expectedToken = preview.confirmToken,
                vaultUnlocked = false,
            )
            assertTrue(denied.isFailure)

            val ok = DesktopLuksImageRestore.applyConfirmed(
                backup = backup,
                destImage = dest,
                expectedSha256 = sha,
                confirmToken = preview.confirmToken,
                expectedToken = preview.confirmToken,
                vaultUnlocked = false,
            ).getOrThrow()
            assertTrue(dest.isFile)
            assertEquals(sha, ok.sha256)
            assertEquals(sha, DesktopSignedUpdate.sha256Hex(dest))
            assertEquals(payload.toList(), dest.readBytes().toList())
        } finally {
            root.deleteRecursively()
        }
    }
}
