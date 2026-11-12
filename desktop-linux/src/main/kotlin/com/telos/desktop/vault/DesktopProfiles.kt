package com.telos.desktop.vault

import com.telos.desktop.network.DesktopNetworkController
import com.telos.desktop.platform.PlatformEnvironment
import java.io.File
import java.util.Locale
import java.util.Properties

/**
 * VOS-1 vault profiles: named folders under config `profiles/`, active name only
 * (`profile.active` in vault `desktop.properties` + a plain marker file).
 * Never writes passwords or other secrets into properties.
 */
object DesktopProfiles {
    private val NAME_PATTERN = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$")
    private const val ACTIVE_KEY = "profile.active"
    private const val PORTABLE_ROOTS_FILE = "portable-vaults.list"

    data class ProfileFolder(val name: String, val directory: File)

    enum class VaultKind { NamedProfile, PortableRoot }

    data class VaultEntry(
        val id: String,
        val displayName: String,
        val directory: File,
        val kind: VaultKind,
        val isActive: Boolean,
    )

    sealed class ProfileError(message: String) : Exception(message) {
        class InvalidName(raw: String) : ProfileError("Invalid profile name: $raw")
        class CreateFailed(name: String) : ProfileError("Could not create profile: $name")
    }

    /**
     * Config base that prefers the portable session layout when the portable shell
     * is active, so Windows Portable Data never writes profile markers into the host home.
     */
    fun configBase(): File {
        PortableEnvironmentIsolator.vaultHome?.let {
            return File(it, ".config/telos").apply { mkdirs() }
        }
        if (preferPortableConfig()) {
            val sessionHome = File(PortableTraceGuard.dataRoot(), "session/home").apply { mkdirs() }
            return File(sessionHome, ".config/telos").apply { mkdirs() }
        }
        return DesktopNetworkController.configHome()
    }

    fun profilesRoot(): File = File(configBase(), "profiles").apply { mkdirs() }

    fun listProfileFolders(): List<ProfileFolder> {
        val root = profilesRoot()
        if (!root.isDirectory) return emptyList()
        return root.listFiles()
            ?.filter { it.isDirectory && !it.name.startsWith(".") && isValidProfileName(it.name) }
            ?.sortedBy { it.name.lowercase(Locale.ROOT) }
            ?.map { ProfileFolder(it.name, it) }
            .orEmpty()
    }

    fun activeProfile(vaultConfigDir: File? = null): String? {
        val dir = vaultConfigDir ?: runCatching { PhantomVault.engine.configDir }.getOrNull()
        val fromVault = readActiveFromDesktopProperties(dir)
        if (!fromVault.isNullOrEmpty()) return fromVault
        val fromController = DesktopNetworkController.activeProfileName(dir)
        if (!fromController.isNullOrEmpty()) return fromController
        return activeMarkerFile().takeIf { it.isFile }?.readText()?.trim()?.ifEmpty { null }
    }

    fun isValidProfileName(name: String): Boolean = NAME_PATTERN.matches(name)

    fun sanitizeProfileName(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        // Reject path-like input before stripping separators.
        if (trimmed.contains('/') || trimmed.contains('\\') || trimmed.contains("..")) return null
        val collapsed = trimmed
            .replace(Regex("\\s+"), "-")
            .filter { it.isLetterOrDigit() || it == '.' || it == '_' || it == '-' }
        if (collapsed.isEmpty()) return null
        val candidate = if (collapsed.first().isLetterOrDigit()) collapsed else "p$collapsed"
        return candidate.take(64).takeIf { isValidProfileName(it) }
    }

    fun createProfile(name: String): Result<ProfileFolder> {
        val safe = sanitizeProfileName(name) ?: return Result.failure(ProfileError.InvalidName(name))
        val dir = File(profilesRoot(), safe)
        if (!dir.exists() && !dir.mkdirs()) {
            return Result.failure(ProfileError.CreateFailed(safe))
        }
        // Placeholder only — never store secrets here.
        File(dir, "profile.meta").takeIf { !it.exists() }?.writeText("name=$safe\n")
        return Result.success(ProfileFolder(safe, dir))
    }

    /**
     * Persist the active profile **name** only into vault `desktop.properties` (when available)
     * and the config-home marker. Does not touch passwords or other secret keys.
     * Appends a `profile_switch` audit event (no secrets).
     */
    fun setActiveProfile(name: String, vaultConfigDir: File? = null): Boolean {
        val safe = sanitizeProfileName(name) ?: return false
        writeActiveMarker(safe)
        val dir = vaultConfigDir ?: runCatching { PhantomVault.engine.configDir }.getOrNull()
        if (dir != null) {
            writeActiveIntoDesktopProperties(dir, safe)
        }
        runCatching { TelosSessionAuditLog.append("profile_switch", safe) }
        return true
    }

    fun switchToProfile(name: String, vaultConfigDir: File? = null): Result<ProfileFolder> {
        val created = createProfile(name)
        val folder = created.getOrElse { return Result.failure(it) }
        if (!setActiveProfile(folder.name, vaultConfigDir)) {
            return Result.failure(ProfileError.CreateFailed(folder.name))
        }
        return Result.success(folder)
    }

    /** Known portable data roots (paths only; no credentials). */
    fun listPortableVaultRoots(): List<File> {
        val seen = LinkedHashSet<String>()
        val out = ArrayList<File>()
        fun add(root: File?) {
            if (root == null) return
            val canonical = runCatching { root.canonicalFile }.getOrElse { root.absoluteFile }
            if (!seen.add(canonical.absolutePath)) return
            out += canonical
        }
        add(PortableTraceGuard.explicitOverride())
        add(PortableTraceGuard.dataRoot())
        add(PortableTraceGuard.jarSiblingDir()?.let { File(it, "TelosPortableData") })
        readRegisteredPortableRoots().forEach { add(it) }
        return out
    }

    fun registerPortableVaultRoot(root: File): Boolean {
        // Portable vault roots must never be the host profile home (no-host-trace).
        runCatching { PortableTraceGuard.assertNoHostWrites(root) }.getOrElse { return false }
        val canonical = runCatching { root.canonicalFile }.getOrElse { root.absoluteFile }
        canonical.mkdirs()
        val list = readRegisteredPortableRoots().toMutableList()
        if (list.none { it.absolutePath == canonical.absolutePath }) {
            list += canonical
            writeRegisteredPortableRoots(list)
        }
        return true
    }

    fun selectPortableVaultRoot(root: File): Boolean {
        if (!registerPortableVaultRoot(root)) return false
        PortableTraceGuard.selectDataRoot(root)
        System.setProperty("telos.portable.active", "1")
        return true
    }

    fun listVaultEntries(vaultConfigDir: File? = null): List<VaultEntry> {
        val active = activeProfile(vaultConfigDir)
        val named = listProfileFolders().map { folder ->
            VaultEntry(
                id = "profile:${folder.name}",
                displayName = folder.name,
                directory = folder.directory,
                kind = VaultKind.NamedProfile,
                isActive = active != null && active.equals(folder.name, ignoreCase = true),
            )
        }
        if (!preferPortableConfig() && !PortableTraceGuard.isPortable()) {
            return named
        }
        val currentPortable = runCatching { PortableTraceGuard.dataRoot().canonicalFile }
            .getOrElse { PortableTraceGuard.dataRoot().absoluteFile }
        val portable = listPortableVaultRoots().map { root ->
            VaultEntry(
                id = "portable:${root.absolutePath}",
                displayName = root.name.ifBlank { root.absolutePath },
                directory = root,
                kind = VaultKind.PortableRoot,
                isActive = root.absolutePath == currentPortable.absolutePath,
            )
        }
        return named + portable
    }

    private fun preferPortableConfig(): Boolean =
        PortableTraceGuard.isPortable() ||
            PlatformEnvironment.settings.showPortableDesktopShell

    private fun activeMarkerFile(): File = File(configBase(), ACTIVE_KEY)

    private fun writeActiveMarker(name: String) {
        val marker = activeMarkerFile()
        marker.parentFile?.mkdirs()
        marker.writeText(name)
        tightenOwnerOnly(marker)
    }

    private fun readActiveFromDesktopProperties(vaultConfigDir: File?): String? {
        if (vaultConfigDir == null) return null
        val file = File(vaultConfigDir, "desktop.properties")
        if (!file.isFile) return null
        val p = Properties()
        file.inputStream().use { p.load(it) }
        return p.getProperty(ACTIVE_KEY)?.trim()?.ifEmpty { null }
    }

    private fun writeActiveIntoDesktopProperties(vaultConfigDir: File, name: String) {
        vaultConfigDir.mkdirs()
        val file = File(vaultConfigDir, "desktop.properties")
        val p = Properties()
        if (file.isFile) {
            file.inputStream().use { p.load(it) }
        }
        SECRET_PROPERTY_KEYS.forEach { p.remove(it) }
        p.setProperty(ACTIVE_KEY, name)
        file.outputStream().use { p.store(it, "Telos Linux Desktop") }
        tightenOwnerOnly(file)
    }

    private fun tightenOwnerOnly(file: File) {
        runCatching {
            file.setReadable(false, false)
            file.setWritable(false, false)
            file.setReadable(true, true)
            file.setWritable(true, true)
        }
    }

    private fun portableRootsRegistryFile(): File = File(configBase(), PORTABLE_ROOTS_FILE)

    private fun readRegisteredPortableRoots(): List<File> {
        val file = portableRootsRegistryFile()
        if (!file.isFile) return emptyList()
        return file.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { File(it) }
            .filter { it.isDirectory || !it.exists() }
    }

    private fun writeRegisteredPortableRoots(roots: List<File>) {
        val file = portableRootsRegistryFile()
        file.parentFile?.mkdirs()
        file.writeText(
            roots.joinToString("\n") { root ->
                runCatching { root.canonicalFile.absolutePath }.getOrElse { root.absolutePath }
            } + "\n",
        )
    }

    private val SECRET_PROPERTY_KEYS = setOf(
        "password",
        "passwd",
        "secret",
        "token",
        "apiKey",
        "api_key",
        "wireguard.privateKey",
        "wg.privateKey",
        "sync.passphrase",
        "keepass.password",
    )
}
