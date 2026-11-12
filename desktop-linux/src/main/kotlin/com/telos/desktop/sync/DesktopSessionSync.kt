package com.telos.desktop.sync

import com.telos.desktop.network.DesktopNetworkController
import com.telos.desktop.settings.TelosDesktopSettings
import com.telos.desktop.vault.DesktopProfiles
import com.telos.desktop.vault.PhantomVault
import com.telos.desktop.vault.PortableEnvironmentIsolator
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.EncryptionMethod
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.Properties

/**
 * Encrypted export/import for desktop session prefs (VOS continuity).
 * Aligns with Android backup group naming; no WireGuard private keys in logs.
 */
object DesktopSessionSync {
    const val BUNDLE_NAME = "telos-desktop-session.telos.zip"
    const val DEFAULT_KEEPASS_ENTRY = "Telos Sync"
    const val MANIFEST = "telos-sync-manifest.properties"
    const val AUTO_IMPORT_FLAG = "desktop.session.autoImport"

    fun defaultExportPath(): File =
        File(DesktopNetworkController.configHome(), "sync").apply { mkdirs() }

    fun exportBundle(
        dest: File,
        passphrase: CharArray,
        vaultSettings: TelosDesktopSettings? = null,
    ): Result<Unit> = runCatching {
        dest.parentFile?.mkdirs()
        val stagingDir = PortableEnvironmentIsolator.tmpDir ?: File(System.getProperty("java.io.tmpdir"))
        val staging = File(stagingDir, "telos-sync-stage-${System.nanoTime()}.zip")
        val zip = ZipFile(staging, passphrase)
        val configDir = vaultSettings?.configDir ?: PhantomVault.engine.configDir
        val desktopProps = File(configDir, "desktop.properties")
        if (desktopProps.isFile) {
            val params = encryptedParams().apply { fileNameInZip = "config/desktop.properties" }
            zip.addFile(desktopProps, params)
        }
        val telosCfg = DesktopNetworkController.configHome()
        telosCfg.walkTopDown()
            .filter { it.isFile }
            .forEach { file ->
                val rel = "telos-config/${file.relativeTo(telosCfg).invariantSeparatorsPath}"
                if (isSensitiveLogPath(rel)) return@forEach
                val params = encryptedParams().apply { fileNameInZip = rel }
                zip.addFile(file, params)
            }
        val manifest = Properties()
        manifest["exportedAt"] = Instant.now().toString()
        manifest["profile.active"] = DesktopProfiles.activeProfile(configDir).orEmpty()
        manifest["backup.group"] = "TelosDesktopSession"
        vaultSettings?.let { s ->
            manifest["helix.panelArgb"] = s.panelArgb.toString()
            manifest["helix.panelAlpha"] = s.panelAlpha.toString()
            manifest["helix.panelBlur"] = s.panelBlur.toString()
            manifest["helix.panelSurfaceMode"] = s.panelSurfaceMode
            manifest["helix.startFullscreen"] = s.startFullscreen.toString()
        }
        val manifestFile = File(stagingDir, "telos-manifest-${System.nanoTime()}.properties")
        manifestFile.outputStream().use { manifest.store(it, "Telos Desktop session sync") }
        zip.addFile(manifestFile, encryptedParams().apply { fileNameInZip = MANIFEST })
        manifestFile.delete()
        if (dest.exists()) dest.delete()
        staging.copyTo(dest, overwrite = true)
        staging.delete()
    }

    fun importBundle(
        src: File,
        passphrase: CharArray,
        vaultSettings: TelosDesktopSettings? = null,
    ): Result<Unit> = runCatching {
        require(src.isFile) { "Missing sync bundle" }
        val zip = ZipFile(src, passphrase)
        if (!zip.isEncrypted) error("Sync bundle is not encrypted")
        val extractRoot = File(
            PortableEnvironmentIsolator.tmpDir ?: File(System.getProperty("java.io.tmpdir")),
            "telos-sync-in-${System.nanoTime()}",
        ).apply { mkdirs() }
        zip.extractAll(extractRoot.absolutePath)
        val configDir = vaultSettings?.configDir ?: PhantomVault.engine.configDir
        configDir.mkdirs()
        File(extractRoot, "config/desktop.properties").takeIf { it.isFile }
            ?.copyTo(File(configDir, "desktop.properties"), overwrite = true)
        val telosCfg = DesktopNetworkController.configHome()
        File(extractRoot, "telos-config").takeIf { it.isDirectory }
            ?.walkTopDown()
            ?.filter { it.isFile }
            ?.forEach { file ->
                val rel = file.relativeTo(File(extractRoot, "telos-config"))
                val target = File(telosCfg, rel.path)
                target.parentFile.mkdirs()
                file.copyTo(target, overwrite = true)
            }
        vaultSettings?.load()
        extractRoot.deleteRecursively()
    }

    fun tryKeePassSyncKey(kdbx: File, masterPassword: CharArray, entryTitle: String = DEFAULT_KEEPASS_ENTRY): CharArray? =
        DesktopKeePassSyncKey.readSyncSecret(kdbx, masterPassword, entryTitle)

    /**
     * Boot / auto-import path. Prefer an already-stored sync/session key so KeePass
     * does not need a master-password prompt. Never invents credentials: without a
     * stored key (and without an explicit KeePass master password) this is a no-op.
     */
    fun maybeAutoImport(
        kdbx: File?,
        masterPassword: CharArray?,
        vaultSettings: TelosDesktopSettings,
    ): Boolean {
        val prefs = File(DesktopNetworkController.configHome(), AUTO_IMPORT_FLAG)
        if (!prefs.isFile || prefs.readText().trim() != "true") return false
        val bundle = File(defaultExportPath(), BUNDLE_NAME)
        if (!bundle.isFile) return false
        val configDir = vaultSettings.configDir
        val pass = when {
            DesktopStoredSyncKey.hasStoredKey(configDir) ->
                DesktopStoredSyncKey.load(configDir) ?: return false
            kdbx != null && masterPassword != null -> {
                val extracted = tryKeePassSyncKey(kdbx, masterPassword) ?: return false
                // Once KeePass yields the sync secret, vault it for subsequent boots
                // without another master-password prompt.
                DesktopStoredSyncKey.store(extracted, configDir)
                extracted
            }
            else -> return false
        }
        return try {
            importBundle(bundle, pass, vaultSettings).isSuccess
        } finally {
            pass.fill('\u0000')
        }
    }

    /** Persist a user-supplied sync passphrase for prompt-free boot auto-import. */
    fun rememberSyncKey(passphrase: CharArray, vaultSettings: TelosDesktopSettings? = null): Boolean =
        DesktopStoredSyncKey.store(passphrase, vaultSettings?.configDir)

    fun hasRememberedSyncKey(vaultSettings: TelosDesktopSettings? = null): Boolean =
        DesktopStoredSyncKey.hasStoredKey(vaultSettings?.configDir)

    fun forgetSyncKey(vaultSettings: TelosDesktopSettings? = null) {
        DesktopStoredSyncKey.clear(vaultSettings?.configDir)
    }

    private fun encryptedParams(): ZipParameters {
        val p = ZipParameters()
        p.isEncryptFiles = true
        p.encryptionMethod = EncryptionMethod.AES
        p.aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
        return p
    }

    private fun isSensitiveLogPath(rel: String): Boolean =
        rel.contains("PrivateKey", ignoreCase = true)

    fun deriveFallbackKey(passphrase: CharArray): ByteArray =
        MessageDigest.getInstance("SHA-256")
            .digest(String(passphrase).toByteArray(StandardCharsets.UTF_8))
}
