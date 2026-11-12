package com.telos.desktop.update

import com.telos.desktop.settings.TelosDesktopSettings
import com.telos.desktop.vault.PortableEnvironmentIsolator
import com.telos.desktop.vault.PortableTraceGuard
import com.telos.desktop.vault.TelosSessionAuditLog
import java.io.File
import java.io.InputStream
import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.PublicKey
import java.util.Base64
import java.util.Properties

/**
 * Signed desktop update check (VOS-5 foundations).
 *
 * Verifies an update jar + manifest against the Telos Ed25519 public key before
 * offering install. Downloads land under portable/vault temp staging. Never
 * auto-installs — callers must obtain user confirmation, then call [installConfirmed].
 *
 * Release endpoint is optional: prefer a local `file://` or filesystem path, or
 * an HTTPS URL stored in vault `desktop.properties` (`desktop.update.manifestUrl`).
 */
object DesktopSignedUpdate {
    const val PREF_MANIFEST_URL = "desktop.update.manifestUrl"
    const val MANIFEST_NAME = "telos-desktop-update.manifest"
    const val STAGING_DIR_NAME = "telos-update-staging"

    data class Manifest(
        val version: String,
        val artifactName: String,
        val sha256: String,
        val artifactUri: String,
        val algorithm: String,
        val signatureBase64: String,
        val rawCanonical: ByteArray,
    )

    data class VerifiedOffer(
        val version: String,
        val artifactName: String,
        val sha256: String,
        val stagedArtifact: File,
        val manifestSource: String,
        val confirmToken: String,
    )

    enum class CheckStatus {
        NoUrlConfigured,
        ManifestMissing,
        SignatureInvalid,
        HashMismatch,
        DownloadFailed,
        ReadyForConfirm,
    }

    data class CheckResult(
        val status: CheckStatus,
        val offer: VerifiedOffer? = null,
        val detail: String = "",
    )

    fun preferredManifestUrl(settings: TelosDesktopSettings?): String? {
        val fromProps = settings?.let { loadUpdateUrl(it) }
        if (!fromProps.isNullOrBlank()) return fromProps.trim()
        System.getProperty("telos.desktop.update.manifest")?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        System.getenv("TELOS_DESKTOP_UPDATE_MANIFEST")?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        return null
    }

    fun saveManifestUrl(settings: TelosDesktopSettings, url: String) {
        settings.load()
        val file = File(settings.configDir, "desktop.properties")
        val p = Properties()
        if (file.isFile) file.inputStream().use { p.load(it) }
        if (url.isBlank()) p.remove(PREF_MANIFEST_URL) else p[PREF_MANIFEST_URL] = url.trim()
        file.parentFile?.mkdirs()
        file.outputStream().use { p.store(it, "Telos Desktop settings") }
        settings.load()
    }

    fun check(
        manifestUri: String,
        publicKey: PublicKey = DesktopUpdateTrust.embeddedPublicKey(),
    ): CheckResult {
        val resolved = runCatching { resolveManifest(manifestUri) }.getOrElse {
            return CheckResult(CheckStatus.ManifestMissing, detail = it.message.orEmpty())
        }
        val manifest = runCatching { parseManifest(resolved.bytes) }.getOrElse {
            return CheckResult(CheckStatus.ManifestMissing, detail = it.message.orEmpty())
        }
        val sig = runCatching { Base64.getDecoder().decode(manifest.signatureBase64) }.getOrElse {
            return CheckResult(CheckStatus.SignatureInvalid, detail = "bad signature encoding")
        }
        if (!DesktopUpdateTrust.verify(manifest.rawCanonical, sig, publicKey)) {
            TelosSessionAuditLog.append("update_check", "signature_invalid source=${safeSource(manifestUri)}")
            return CheckResult(CheckStatus.SignatureInvalid, detail = "Ed25519 signature mismatch")
        }
        val staging = stagingRoot().apply { mkdirs() }
        val staged = File(staging, manifest.artifactName)
        val download = runCatching {
            downloadTo(manifest.artifactUri, staged)
        }.getOrElse {
            return CheckResult(CheckStatus.DownloadFailed, detail = it.message.orEmpty())
        }
        if (!download) {
            return CheckResult(CheckStatus.DownloadFailed, detail = "artifact missing")
        }
        val actual = sha256Hex(staged)
        if (!actual.equals(manifest.sha256, ignoreCase = true)) {
            staged.delete()
            TelosSessionAuditLog.append("update_check", "hash_mismatch version=${manifest.version}")
            return CheckResult(CheckStatus.HashMismatch, detail = "sha256 mismatch")
        }
        val token = "INSTALL:${manifest.version}:${actual.take(16)}"
        TelosSessionAuditLog.append(
            "update_check",
            "verified version=${manifest.version} artifact=${manifest.artifactName}",
        )
        return CheckResult(
            status = CheckStatus.ReadyForConfirm,
            offer = VerifiedOffer(
                version = manifest.version,
                artifactName = manifest.artifactName,
                sha256 = actual,
                stagedArtifact = staged,
                manifestSource = safeSource(manifestUri),
                confirmToken = token,
            ),
        )
    }

    fun installConfirmed(
        offer: VerifiedOffer,
        confirmToken: String,
        destDir: File? = null,
    ): Result<File> = runCatching {
        require(confirmToken == offer.confirmToken && confirmToken.startsWith("INSTALL:")) {
            "Update install confirmation required"
        }
        require(offer.stagedArtifact.isFile) { "Staged artifact missing" }
        val actual = sha256Hex(offer.stagedArtifact)
        require(actual.equals(offer.sha256, ignoreCase = true)) { "Staged hash changed" }
        val targetDir = destDir ?: defaultInstallDir()
        targetDir.mkdirs()
        val dest = File(targetDir, offer.artifactName)
        offer.stagedArtifact.copyTo(dest, overwrite = true)
        TelosSessionAuditLog.append(
            "update_staged",
            "version=${offer.version} dest=${dest.name}",
        )
        dest
    }

    fun stagingRoot(): File {
        PortableEnvironmentIsolator.tmpDir?.let { return File(it, STAGING_DIR_NAME) }
        if (PortableTraceGuard.isPortable()) {
            return File(PortableTraceGuard.ensurePortableDataRoot(), "tmp/$STAGING_DIR_NAME")
        }
        return File(System.getProperty("java.io.tmpdir"), STAGING_DIR_NAME)
    }

    fun defaultInstallDir(): File {
        if (PortableTraceGuard.isPortable()) {
            return File(PortableTraceGuard.ensurePortableDataRoot(), "updates").apply { mkdirs() }
        }
        PortableTraceGuard.jarSiblingDir()?.let { return File(it, "updates").apply { mkdirs() } }
        return File(PortableTraceGuard.dataRoot(), "updates").apply { mkdirs() }
    }

    fun canonicalPayload(version: String, artifactName: String, sha256: String, artifactUri: String): ByteArray {
        val text = buildString {
            append("telos-desktop-update/v1\n")
            append("version=").append(version.trim()).append('\n')
            append("artifact=").append(artifactName.trim()).append('\n')
            append("sha256=").append(sha256.trim().lowercase()).append('\n')
            append("uri=").append(artifactUri.trim()).append('\n')
        }
        return text.toByteArray(StandardCharsets.UTF_8)
    }

    fun parseManifest(bytes: ByteArray): Manifest {
        val p = Properties()
        bytes.inputStream().use { p.load(it) }
        val version = p.getProperty("version")?.trim().orEmpty()
        val artifact = p.getProperty("artifact")?.trim().orEmpty()
        val sha = p.getProperty("sha256")?.trim().orEmpty()
        val uri = p.getProperty("uri")?.trim().orEmpty()
        val algo = p.getProperty("algorithm")?.trim() ?: DesktopUpdateTrust.ALGORITHM
        val sig = p.getProperty("signature")?.trim().orEmpty()
        require(version.isNotEmpty() && artifact.isNotEmpty() && sha.length == 64 && uri.isNotEmpty()) {
            "Incomplete update manifest"
        }
        require(algo.equals(DesktopUpdateTrust.ALGORITHM, ignoreCase = true)) {
            "Unsupported update algorithm"
        }
        require(sig.isNotEmpty()) { "Missing signature" }
        return Manifest(
            version = version,
            artifactName = artifact,
            sha256 = sha.lowercase(),
            artifactUri = uri,
            algorithm = algo,
            signatureBase64 = sig,
            rawCanonical = canonicalPayload(version, artifact, sha, uri),
        )
    }

    fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(8192)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { b -> "%02x".format(b) }
    }

    private fun loadUpdateUrl(settings: TelosDesktopSettings): String? {
        val file = File(settings.configDir, "desktop.properties")
        if (!file.isFile) return null
        val p = Properties()
        file.inputStream().use { p.load(it) }
        return p.getProperty(PREF_MANIFEST_URL)
    }

    private data class ResolvedManifest(val bytes: ByteArray)

    private fun resolveManifest(uriOrPath: String): ResolvedManifest {
        val trimmed = uriOrPath.trim()
        if (trimmed.startsWith("http://")) error("HTTP (cleartext) update manifests are refused; use HTTPS or file://")
        if (trimmed.startsWith("https://") || trimmed.startsWith("file:")) {
            val uri = URI(trimmed)
            openStream(uri).use { return ResolvedManifest(it.readBytes()) }
        }
        val f = File(trimmed)
        require(f.isFile) { "Manifest not found: ${f.name}" }
        return ResolvedManifest(f.readBytes())
    }

    private fun downloadTo(uriOrPath: String, dest: File): Boolean {
        dest.parentFile?.mkdirs()
        if (dest.exists()) dest.delete()
        val trimmed = uriOrPath.trim()
        when {
            trimmed.startsWith("http://") -> error("HTTP (cleartext) artifact download refused")
            trimmed.startsWith("https://") || trimmed.startsWith("file:") -> {
                openStream(URI(trimmed)).use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
            }
            else -> {
                val src = File(trimmed)
                if (!src.isFile) return false
                src.copyTo(dest, overwrite = true)
            }
        }
        return dest.isFile && dest.length() > 0L
    }

    private fun openStream(uri: URI): InputStream {
        if (uri.scheme.equals("file", ignoreCase = true)) {
            return File(uri).inputStream()
        }
        if (uri.scheme.equals("https", ignoreCase = true)) {
            return uri.toURL().openStream()
        }
        error("Unsupported URI scheme: ${uri.scheme}")
    }

    private fun safeSource(uri: String): String =
        uri.trim().take(120).replace(Regex("://[^/]+@"), "://")
}
