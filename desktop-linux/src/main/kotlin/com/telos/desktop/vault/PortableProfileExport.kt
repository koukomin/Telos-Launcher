package com.telos.desktop.vault

import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.EncryptionMethod
import java.io.File
import java.time.Instant
import java.util.Properties

/**
 * VOS-1 encrypted portable profile export/import.
 *
 * AES-256 zip of the named profile folder plus a `desktop.properties` name marker
 * (`profile.active` only). Never includes WireGuard private keys or other secret
 * property keys. Passwords are never persisted — the passphrase CharArray is a
 * call-site concern (clear after use). Import requires an explicit [confirmed] flag.
 */
object PortableProfileExport {
    const val BUNDLE_SUFFIX = ".telos.profile.zip"
    const val MANIFEST = "telos-profile-manifest.properties"
    const val MARKER_ENTRY = "marker/desktop.properties"
    const val PROFILE_PREFIX = "profile/"

    sealed class ExportError(message: String) : Exception(message) {
        class InvalidName(raw: String) : ExportError("Invalid profile name: $raw")
        class MissingProfile(name: String) : ExportError("Profile not found: $name")
        class ConfirmationRequired : ExportError("Import requires confirmation")
        class NotEncrypted : ExportError("Profile bundle is not encrypted")
        class ImportFailed(detail: String) : ExportError(detail)
    }

    fun defaultExportFile(profileName: String): File {
        val safe = DesktopProfiles.sanitizeProfileName(profileName) ?: "profile"
        val dir = File(DesktopProfiles.configBase(), "exports").apply { mkdirs() }
        runCatching { PortableTraceGuard.assertNoHostWrites(dir.parentFile ?: dir) }
        return File(dir, "$safe$BUNDLE_SUFFIX")
    }

    fun exportProfile(
        profileName: String,
        dest: File,
        passphrase: CharArray,
        vaultConfigDir: File? = null,
    ): Result<File> = runCatching {
        require(passphrase.isNotEmpty()) { "Passphrase required" }
        val safe = DesktopProfiles.sanitizeProfileName(profileName)
            ?: throw ExportError.InvalidName(profileName)
        val folder = DesktopProfiles.listProfileFolders().find { it.name.equals(safe, ignoreCase = true) }
            ?: throw ExportError.MissingProfile(safe)
        dest.parentFile?.mkdirs()
        runCatching { dest.parentFile?.let { PortableTraceGuard.assertNoHostWrites(it) } }
        val stagingDir = PortableEnvironmentIsolator.tmpDir
            ?: File(System.getProperty("java.io.tmpdir"))
        val staging = File(stagingDir, "telos-profile-export-${System.nanoTime()}.zip")
        if (staging.exists()) staging.delete()
        val zip = ZipFile(staging, passphrase)
        folder.directory.walkTopDown()
            .filter { it.isFile }
            .forEach { file ->
                val rel = file.relativeTo(folder.directory).invariantSeparatorsPath
                if (isExcludedPath(rel)) return@forEach
                val params = encryptedParams().apply {
                    fileNameInZip = "$PROFILE_PREFIX$safe/$rel"
                }
                if (rel.endsWith(".properties", ignoreCase = true) || rel == "profile.meta") {
                    val scrubbed = scrubPropertiesFile(file)
                    val tmp = File(stagingDir, "telos-scrub-${System.nanoTime()}.properties")
                    scrubbed.store(tmp.outputStream(), "Telos profile export (no secrets)")
                    zip.addFile(tmp, params)
                    tmp.delete()
                } else {
                    zip.addFile(file, params)
                }
            }
        val markerProps = Properties()
        markerProps.setProperty(DesktopProfiles.ACTIVE_KEY_PUBLIC, safe)
        val markerFile = File(stagingDir, "telos-marker-${System.nanoTime()}.properties")
        markerProps.store(markerFile.outputStream(), "Telos profile name marker only")
        zip.addFile(markerFile, encryptedParams().apply { fileNameInZip = MARKER_ENTRY })
        markerFile.delete()

        val host = DesktopProfiles.currentHostFingerprint()
        val manifest = Properties()
        manifest["exportedAt"] = Instant.now().toString()
        manifest["profile.name"] = safe
        manifest["host.id"] = host.id
        manifest["host.label"] = host.label
        manifest["backup.group"] = "TelosPortableProfile"
        val active = DesktopProfiles.activeProfile(vaultConfigDir)
        if (!active.isNullOrEmpty()) manifest["profile.active"] = active
        val manifestFile = File(stagingDir, "telos-profile-manifest-${System.nanoTime()}.properties")
        manifest.store(manifestFile.outputStream(), "Telos portable profile export")
        zip.addFile(manifestFile, encryptedParams().apply { fileNameInZip = MANIFEST })
        manifestFile.delete()

        if (dest.exists()) dest.delete()
        staging.copyTo(dest, overwrite = true)
        staging.delete()
        runCatching { TelosSessionAuditLog.append("profile_export", safe) }
        dest
    }

    fun importProfile(
        src: File,
        passphrase: CharArray,
        confirmed: Boolean,
        vaultConfigDir: File? = null,
        activate: Boolean = false,
    ): Result<DesktopProfiles.ProfileFolder> = runCatching {
        if (!confirmed) throw ExportError.ConfirmationRequired()
        require(src.isFile) { "Missing profile bundle" }
        require(passphrase.isNotEmpty()) { "Passphrase required" }
        val zip = ZipFile(src, passphrase)
        if (!zip.isEncrypted) throw ExportError.NotEncrypted()
        val extractRoot = File(
            PortableEnvironmentIsolator.tmpDir ?: File(System.getProperty("java.io.tmpdir")),
            "telos-profile-in-${System.nanoTime()}",
        ).apply { mkdirs() }
        try {
            zip.extractAll(extractRoot.absolutePath)
            val manifestFile = File(extractRoot, MANIFEST)
            val manifest = Properties()
            if (manifestFile.isFile) {
                manifestFile.inputStream().use { manifest.load(it) }
            }
            val nameFromManifest = manifest.getProperty("profile.name")?.trim().orEmpty()
            val marker = Properties()
            File(extractRoot, MARKER_ENTRY).takeIf { it.isFile }?.inputStream()?.use { marker.load(it) }
            val nameFromMarker = marker.getProperty(DesktopProfiles.ACTIVE_KEY_PUBLIC)?.trim().orEmpty()
            val profileDirOnDisk = File(extractRoot, "profile").listFiles()?.firstOrNull { it.isDirectory }
            val rawName = nameFromManifest.ifEmpty { nameFromMarker }.ifEmpty { profileDirOnDisk?.name.orEmpty() }
            val safe = DesktopProfiles.sanitizeProfileName(rawName)
                ?: throw ExportError.ImportFailed("Invalid profile name in bundle")
            val created = DesktopProfiles.createProfile(safe).getOrElse {
                throw ExportError.ImportFailed(it.message ?: "create failed")
            }
            val sourceProfile = File(extractRoot, "$PROFILE_PREFIX$safe").takeIf { it.isDirectory }
                ?: profileDirOnDisk
            if (sourceProfile != null && sourceProfile.isDirectory) {
                sourceProfile.walkTopDown()
                    .filter { it.isFile }
                    .forEach { file ->
                        val rel = file.relativeTo(sourceProfile).invariantSeparatorsPath
                        if (isExcludedPath(rel)) return@forEach
                        val target = File(created.directory, rel)
                        target.parentFile?.mkdirs()
                        if (rel.endsWith(".properties", ignoreCase = true) || rel == "profile.meta") {
                            val scrubbed = scrubPropertiesFile(file)
                            target.outputStream().use { scrubbed.store(it, "Telos profile import (no secrets)") }
                        } else {
                            file.copyTo(target, overwrite = true)
                        }
                    }
            }
            DesktopProfiles.touchHostMarker(created.directory)
            if (activate) {
                DesktopProfiles.setActiveProfile(safe, vaultConfigDir)
            } else if (vaultConfigDir != null) {
                val markerOnly = File(vaultConfigDir, "imported-profile.marker")
                vaultConfigDir.mkdirs()
                markerOnly.writeText(safe)
            }
            runCatching { TelosSessionAuditLog.append("profile_import", safe) }
            created
        } finally {
            extractRoot.deleteRecursively()
        }
    }

    private fun encryptedParams(): ZipParameters {
        val p = ZipParameters()
        p.isEncryptFiles = true
        p.encryptionMethod = EncryptionMethod.AES
        p.aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
        return p
    }

    internal fun isExcludedPath(rel: String): Boolean {
        val lower = rel.lowercase()
        return lower.contains("privatekey") ||
            lower.contains("private_key") ||
            lower.contains("wireguard") ||
            lower.contains("/wg/") ||
            lower.endsWith(".key") ||
            lower.endsWith(".kdbx") ||
            lower.contains("passwd") ||
            lower.contains("password") ||
            lower.contains("secret") ||
            lower.contains("token") ||
            lower.contains("keepass") ||
            SECRET_BASENAMES.any { lower == it || lower.endsWith("/$it") }
    }

    private fun scrubPropertiesFile(file: File): Properties {
        val p = Properties()
        if (file.isFile) {
            file.inputStream().use { p.load(it) }
        }
        DesktopProfiles.SECRET_PROPERTY_KEYS_PUBLIC.forEach { p.remove(it) }
        p.stringPropertyNames()
            .filter { key ->
                val k = key.lowercase()
                k.contains("pass") || k.contains("secret") || k.contains("token") ||
                    k.contains("private") || k.contains("apikey") || k.contains("api_key")
            }
            .forEach { p.remove(it) }
        return p
    }

    private val SECRET_BASENAMES = setOf(
        "wg0.conf",
        "privatekey",
        "private_key",
        "id_rsa",
        "id_ed25519",
    )
}
