package com.telos.desktop.vault

import com.telos.desktop.network.DesktopNetworkController
import com.telos.desktop.platform.PlatformEnvironment
import java.io.File
import java.net.InetAddress
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import java.util.Properties

/**
 * VOS-1 vault profiles: named folders under config `profiles/`, active name only
 * (`profile.active` in vault `desktop.properties` + a plain marker file).
 * Never writes passwords or other secrets into properties.
 *
 * Lock-gated switch: when a LUKS/Phantom vault is unlocked, re-auth (password
 * callback) is required, then the vault is locked and the profile name / portable
 * root is updated. Remount of a different LUKS image is left to the existing
 * unlock path — this API never claims automatic remount without a password.
 */
object DesktopProfiles {
    private val NAME_PATTERN = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$")
    private const val ACTIVE_KEY = "profile.active"
    private const val PORTABLE_ROOTS_FILE = "portable-vaults.list"
    private const val HOST_ID_KEY = "host.id"
    private const val HOST_LABEL_KEY = "host.label"
    private const val META_NAME_KEY = "name"

    /** Public alias for exporters that need the active-profile property key. */
    const val ACTIVE_KEY_PUBLIC: String = ACTIVE_KEY

    data class ProfileFolder(val name: String, val directory: File)

    data class HostFingerprint(val id: String, val label: String)

    enum class HostAwareness { SameHost, DifferentHost, Unknown }

    enum class VaultKind { NamedProfile, PortableRoot }

    data class VaultEntry(
        val id: String,
        val displayName: String,
        val directory: File,
        val kind: VaultKind,
        val isActive: Boolean,
        val hostLabel: String? = null,
        val hostAwareness: HostAwareness = HostAwareness.Unknown,
    )

    sealed class ProfileError(message: String) : Exception(message) {
        class InvalidName(raw: String) : ProfileError("Invalid profile name: $raw")
        class CreateFailed(name: String) : ProfileError("Could not create profile: $name")
        class AuthRequired : ProfileError("Re-authentication required before switching while vault is unlocked")
        class AuthFailed : ProfileError("Re-authentication failed")
    }

    /**
     * Outcome of a lock-gated profile / portable-root switch.
     * [Switched.lockedFirst] is true when the vault was locked before the name change.
     * Callers must unlock again via the normal pre-boot path — no automatic remount.
     */
    sealed class SwitchOutcome {
        data class Switched(val folder: ProfileFolder?, val portableRoot: File?, val lockedFirst: Boolean) : SwitchOutcome()
        data class Failed(val error: ProfileError) : SwitchOutcome()
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
        writeProfileMeta(dir, safe)
        return Result.success(ProfileFolder(safe, dir))
    }

    fun setActiveProfile(name: String, vaultConfigDir: File? = null): Boolean {
        val safe = sanitizeProfileName(name) ?: return false
        writeActiveMarker(safe)
        val dir = vaultConfigDir ?: runCatching { PhantomVault.engine.configDir }.getOrNull()
        if (dir != null) {
            writeActiveIntoDesktopProperties(dir, safe)
        }
        listProfileFolders().find { it.name.equals(safe, ignoreCase = true) }?.let {
            touchHostMarker(it.directory)
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

    /**
     * Lock-gated named-profile switch.
     *
     * When [vaultUnlocked] is true: [passwordAuth] must return true (re-auth).
     * Then [lockVault] is invoked (typically [PhantomVaultEngine.lock]), the
     * profile name is switched, and the caller must unlock again — this does
     * **not** remount a different LUKS image without a password.
     */
    fun switchToProfileLockGated(
        name: String,
        vaultUnlocked: Boolean,
        passwordAuth: (() -> Boolean)? = null,
        lockVault: (() -> Unit)? = null,
        vaultConfigDir: File? = null,
    ): SwitchOutcome {
        if (vaultUnlocked) {
            if (passwordAuth == null) {
                return SwitchOutcome.Failed(ProfileError.AuthRequired())
            }
            val ok = runCatching { passwordAuth.invoke() }.getOrDefault(false)
            if (!ok) {
                runCatching { TelosSessionAuditLog.append("profile_switch_denied", "auth_failed") }
                return SwitchOutcome.Failed(ProfileError.AuthFailed())
            }
            runCatching { lockVault?.invoke() }
            runCatching { TelosSessionAuditLog.append("profile_switch_lock", "locked_before_switch") }
        }
        val result = switchToProfile(name, vaultConfigDir)
        val folder = result.getOrElse {
            return SwitchOutcome.Failed(it as? ProfileError ?: ProfileError.CreateFailed(name))
        }
        return SwitchOutcome.Switched(folder = folder, portableRoot = null, lockedFirst = vaultUnlocked)
    }

    fun selectPortableVaultRootLockGated(
        root: File,
        vaultUnlocked: Boolean,
        passwordAuth: (() -> Boolean)? = null,
        lockVault: (() -> Unit)? = null,
    ): SwitchOutcome {
        if (vaultUnlocked) {
            if (passwordAuth == null) {
                return SwitchOutcome.Failed(ProfileError.AuthRequired())
            }
            val ok = runCatching { passwordAuth.invoke() }.getOrDefault(false)
            if (!ok) {
                runCatching { TelosSessionAuditLog.append("profile_switch_denied", "portable_auth_failed") }
                return SwitchOutcome.Failed(ProfileError.AuthFailed())
            }
            runCatching { lockVault?.invoke() }
            runCatching { TelosSessionAuditLog.append("profile_switch_lock", "locked_before_portable") }
        }
        if (!selectPortableVaultRoot(root)) {
            return SwitchOutcome.Failed(ProfileError.CreateFailed(root.name.ifBlank { root.absolutePath }))
        }
        return SwitchOutcome.Switched(
            folder = null,
            portableRoot = runCatching { root.canonicalFile }.getOrElse { root.absoluteFile },
            lockedFirst = vaultUnlocked,
        )
    }

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
            val host = readHostFingerprint(folder.directory)
            VaultEntry(
                id = "profile:${folder.name}",
                displayName = folder.name,
                directory = folder.directory,
                kind = VaultKind.NamedProfile,
                isActive = active != null && active.equals(folder.name, ignoreCase = true),
                hostLabel = host?.label,
                hostAwareness = hostAwareness(folder.directory),
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

    fun currentHostFingerprint(): HostFingerprint {
        val hostname = resolveHostname()
        val os = System.getProperty("os.name")?.trim().orEmpty().ifBlank { "unknown" }
        val material = "$hostname|$os"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(material.toByteArray(StandardCharsets.UTF_8))
        val id = digest.joinToString("") { b -> "%02x".format(b) }.take(32)
        return HostFingerprint(id = id, label = "$hostname / $os")
    }

    fun touchHostMarker(profileDir: File) {
        writeProfileMeta(profileDir, profileDir.name)
    }

    fun readHostFingerprint(profileDir: File): HostFingerprint? {
        val meta = File(profileDir, "profile.meta")
        if (!meta.isFile) return null
        val p = Properties()
        meta.inputStream().use { p.load(it) }
        val id = p.getProperty(HOST_ID_KEY)?.trim()?.ifEmpty { null } ?: return null
        val label = p.getProperty(HOST_LABEL_KEY)?.trim()?.ifEmpty { null } ?: id
        return HostFingerprint(id = id, label = label)
    }

    fun hostAwareness(profileDir: File): HostAwareness {
        val stored = readHostFingerprint(profileDir) ?: return HostAwareness.Unknown
        val current = currentHostFingerprint()
        return if (stored.id == current.id) HostAwareness.SameHost else HostAwareness.DifferentHost
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

    private fun writeProfileMeta(dir: File, name: String) {
        dir.mkdirs()
        val meta = File(dir, "profile.meta")
        val p = Properties()
        if (meta.isFile) {
            meta.inputStream().use { p.load(it) }
        }
        SECRET_PROPERTY_KEYS.forEach { p.remove(it) }
        p.stringPropertyNames()
            .filter { key ->
                val k = key.lowercase()
                k.contains("pass") || k.contains("secret") || k.contains("token") || k.contains("private")
            }
            .forEach { p.remove(it) }
        p.setProperty(META_NAME_KEY, name)
        val host = currentHostFingerprint()
        p.setProperty(HOST_ID_KEY, host.id)
        p.setProperty(HOST_LABEL_KEY, host.label)
        meta.outputStream().use { p.store(it, "Telos vault profile (no secrets)") }
        tightenOwnerOnly(meta)
    }

    private fun resolveHostname(): String {
        System.getenv("HOSTNAME")?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        System.getenv("COMPUTERNAME")?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        return runCatching { InetAddress.getLocalHost().hostName }
            .getOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() && it != "localhost" }
            ?: "unknown"
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

    val SECRET_PROPERTY_KEYS_PUBLIC: Set<String> get() = SECRET_PROPERTY_KEYS
}
