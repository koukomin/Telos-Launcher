package com.telos.desktop.sync

import com.telos.desktop.update.DesktopSignedUpdate
import com.telos.desktop.vault.LuksVaultManager
import com.telos.desktop.vault.PhantomVault
import com.telos.desktop.vault.PortableEnvironmentIsolator
import com.telos.desktop.vault.PortableTraceGuard
import com.telos.desktop.vault.TelosSessionAuditLog
import java.io.File
import java.util.UUID

/**
 * LUKS vault **image** restore (VOS-5): copy a user-selected `telos_vault.img`
 * backup into the portable/vault image path after confirmation and SHA-256 check.
 *
 * Requires the vault to be **locked**. Never mounts the restored image and never
 * accepts a password — unlock stays a separate, explicit user step.
 */
object DesktopLuksImageRestore {
    const val CONFIRM_PREFIX = "LUKS_RESTORE:"
    const val IMAGE_NAME = "telos_vault.img"

    data class Preview(
        val backup: File,
        val destImage: File,
        val backupSha256: String,
        val backupBytes: Long,
        /** Caller must pass this exact token to [applyConfirmed]. */
        val confirmToken: String,
    )

    data class RestoreResult(
        val destImage: File,
        val sha256: String,
        val bytes: Long,
    )

    fun defaultDestImage(
        vault: LuksVaultManager? = runCatching { PhantomVault.engine.luks }.getOrNull(),
    ): File {
        vault?.let { return it.vaultImage }
        if (PortableTraceGuard.isPortable()) {
            return File(PortableTraceGuard.ensurePortableDataRoot(), IMAGE_NAME)
        }
        return File(System.getProperty("user.home"), ".local/share/telos/$IMAGE_NAME")
    }

    fun isVaultUnlocked(
        unlockedFlag: Boolean? = null,
        vault: LuksVaultManager? = runCatching { PhantomVault.engine.luks }.getOrNull(),
    ): Boolean {
        if (unlockedFlag != null) return unlockedFlag
        return vault?.unlocked == true
    }

    fun preview(
        backup: File,
        destImage: File = defaultDestImage(),
        vaultUnlocked: Boolean,
    ): Result<Preview> = runCatching {
        require(!vaultUnlocked) { "Vault must be locked before restoring LUKS image" }
        require(backup.isFile) { "Missing LUKS image backup" }
        require(backup.length() > 0L) { "LUKS image backup is empty" }
        val sha = DesktopSignedUpdate.sha256Hex(backup)
        Preview(
            backup = backup,
            destImage = destImage,
            backupSha256 = sha,
            backupBytes = backup.length(),
            confirmToken = CONFIRM_PREFIX + UUID.randomUUID().toString(),
        )
    }

    fun applyConfirmed(
        backup: File,
        destImage: File,
        expectedSha256: String,
        confirmToken: String,
        expectedToken: String,
        vaultUnlocked: Boolean,
    ): Result<RestoreResult> = runCatching {
        require(!vaultUnlocked) { "Vault must be locked before restoring LUKS image" }
        require(confirmToken == expectedToken && confirmToken.startsWith(CONFIRM_PREFIX)) {
            "LUKS image restore confirmation required"
        }
        require(backup.isFile) { "Missing LUKS image backup" }
        val actual = DesktopSignedUpdate.sha256Hex(backup)
        require(actual.equals(expectedSha256, ignoreCase = true)) { "Backup SHA-256 mismatch" }
        destImage.parentFile?.mkdirs()
        val stagingParent = PortableEnvironmentIsolator.tmpDir
            ?: destImage.parentFile
            ?: File(System.getProperty("java.io.tmpdir"))
        val tmp = File(stagingParent, "$IMAGE_NAME.restore-${System.nanoTime()}.tmp")
        try {
            backup.copyTo(tmp, overwrite = true)
            val copied = DesktopSignedUpdate.sha256Hex(tmp)
            require(copied.equals(expectedSha256, ignoreCase = true)) { "Copied image SHA-256 mismatch" }
            if (destImage.exists()) {
                val bak = File(destImage.parentFile, "${destImage.name}.bak-${System.currentTimeMillis()}")
                destImage.copyTo(bak, overwrite = true)
            }
            if (!tmp.renameTo(destImage)) {
                tmp.copyTo(destImage, overwrite = true)
                tmp.delete()
            }
            TelosSessionAuditLog.append(
                "luks_image_restore",
                "dest=${destImage.name} bytes=${destImage.length()} sha=${actual.take(16)}",
            )
            RestoreResult(destImage = destImage, sha256 = actual, bytes = destImage.length())
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }
}
