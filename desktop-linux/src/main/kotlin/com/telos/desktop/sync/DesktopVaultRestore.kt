package com.telos.desktop.sync

import com.telos.desktop.network.DesktopNetworkController
import com.telos.desktop.settings.TelosDesktopSettings
import com.telos.desktop.vault.PhantomVault
import com.telos.desktop.vault.PortableEnvironmentIsolator
import com.telos.desktop.vault.TelosSessionAuditLog
import net.lingala.zip4j.ZipFile
import java.io.File
import java.util.UUID

/**
 * Full vault restore foundations (VOS-5): apply an AES session backup/export bundle
 * into vault HOME / `~/.config/telos` only after an explicit confirmation token.
 *
 * WireGuard private keys may be present inside the encrypted zip; they are never
 * written to logs or the audit trail. Staging uses portable/vault temp when set.
 */
object DesktopVaultRestore {
    const val CONFIRM_PREFIX = "RESTORE:"

    data class RestorePreview(
        val bundle: File,
        val hasDesktopProperties: Boolean,
        val telosConfigFileCount: Int,
        val profileActive: String?,
        val exportedAt: String?,
        /** Caller must pass this exact token to [applyConfirmed]. */
        val confirmToken: String,
    )

    data class RestoreResult(
        val restoredDesktopProperties: Boolean,
        val restoredTelosConfigFiles: Int,
    )

    fun defaultBundle(): File =
        File(DesktopSessionSync.defaultExportPath(), DesktopSessionSync.BUNDLE_NAME)

    fun preview(
        src: File,
        passphrase: CharArray,
    ): Result<RestorePreview> = runCatching {
        require(src.isFile) { "Missing restore bundle" }
        val zip = ZipFile(src, passphrase)
        if (!zip.isEncrypted) error("Restore bundle is not encrypted")
        val extractRoot = stagingDir("telos-restore-preview")
        try {
            zip.extractAll(extractRoot.absolutePath)
            val hasProps = File(extractRoot, "config/desktop.properties").isFile
            val telosRoot = File(extractRoot, "telos-config")
            val telosCount = if (telosRoot.isDirectory) {
                telosRoot.walkTopDown().count { it.isFile }
            } else {
                0
            }
            var profile: String? = null
            var exportedAt: String? = null
            File(extractRoot, DesktopSessionSync.MANIFEST).takeIf { it.isFile }?.bufferedReader()?.use { reader ->
                reader.lineSequence().forEach { line ->
                    when {
                        line.startsWith("profile.active=") ->
                            profile = line.substringAfter('=').trim()
                        line.startsWith("exportedAt=") ->
                            exportedAt = line.substringAfter('=').trim()
                    }
                }
            }
            RestorePreview(
                bundle = src,
                hasDesktopProperties = hasProps,
                telosConfigFileCount = telosCount,
                profileActive = profile,
                exportedAt = exportedAt,
                confirmToken = CONFIRM_PREFIX + UUID.randomUUID().toString(),
            )
        } finally {
            extractRoot.deleteRecursively()
        }
    }

    fun applyConfirmed(
        src: File,
        passphrase: CharArray,
        confirmToken: String,
        expectedToken: String,
        vaultSettings: TelosDesktopSettings? = null,
    ): Result<RestoreResult> = runCatching {
        require(confirmToken == expectedToken && confirmToken.startsWith(CONFIRM_PREFIX)) {
            "Restore confirmation required"
        }
        val settings = vaultSettings
            ?: TelosDesktopSettings(PhantomVault.engine.configDir).also { it.load() }
        DesktopSessionSync.importBundle(src, passphrase, settings).getOrThrow()
        val telosCfg = DesktopNetworkController.configHome()
        val restoredCount = telosCfg.walkTopDown().count { it.isFile }
        val restoredProps = File(settings.configDir, "desktop.properties").isFile
        TelosSessionAuditLog.append(
            "vault_restore",
            "bundle=${src.name} desktopProps=$restoredProps telosFiles=$restoredCount",
        )
        RestoreResult(
            restoredDesktopProperties = restoredProps,
            restoredTelosConfigFiles = restoredCount,
        )
    }

    private fun stagingDir(prefix: String): File {
        val parent = PortableEnvironmentIsolator.tmpDir
            ?: File(System.getProperty("java.io.tmpdir"))
        return File(parent, "$prefix-${System.nanoTime()}").apply { mkdirs() }
    }
}
