package com.telos.desktop.apps

import com.telos.desktop.linux.Sys
import com.telos.desktop.platform.PlatformEnvironment
import com.telos.desktop.platform.TelosRuntimePlatform

/**
 * VOS-3 Flatpak sandbox tiers and common permission overrides.
 * Degrades to no-ops when Flatpak is missing (portable Windows/macOS, or Linux without Flatpak).
 */
enum class FlatpakSandboxTier {
    /** Host filesystem + network — least sandboxed. */
    Host,
    /** Home/vault paths only; host FS revoked when overrides apply. */
    VaultFs,
    /** Network unshared; FS stays at current home-oriented defaults. */
    NetIsolated,
    /** Typical Flatpak: home + network, no host FS grant. */
    Standard,
}

enum class FlatpakPermission {
    FilesystemHome,
    FilesystemHost,
    Network,
    Gpu,
    Wayland,
    X11,
    PulseAudio,
    Notifications,
    Camera,
    Microphone,
}

data class FlatpakPermissionState(
    val appId: String,
    val permissions: Map<FlatpakPermission, Boolean>,
    val tier: FlatpakSandboxTier,
    val rawOverride: String,
) {
    companion object {
        fun empty(appId: String) = FlatpakPermissionState(
            appId = appId,
            permissions = FlatpakPermission.entries.associateWith { false },
            tier = FlatpakSandboxTier.Standard,
            rawOverride = "",
        )
    }
}

enum class FlatpakOverrideResult {
    Applied,
    Unavailable,
    Failed,
    Invalid,
}

object FlatpakSandbox {
    private val isLinux: Boolean
        get() = PlatformEnvironment.current == TelosRuntimePlatform.LinuxDesktop

    fun available(): Boolean = isLinux && FlatpakStore.available()

    fun read(appId: String): FlatpakPermissionState {
        if (!available() || appId.isBlank() || !appId.contains('.')) {
            return FlatpakPermissionState.empty(appId)
        }
        val shown = Sys.run("flatpak", "override", "--user", "--show", appId, timeoutSec = 10)
        val info = Sys.run("flatpak", "info", "--show-permissions", appId, timeoutSec = 10)
        val combined = buildString {
            if (info.ok) append(info.stdout).append('\n')
            if (shown.ok) append(shown.stdout)
        }
        return parsePermissionText(appId, combined)
    }

    fun applyPermission(appId: String, permission: FlatpakPermission, enabled: Boolean): FlatpakOverrideResult {
        if (!available()) return FlatpakOverrideResult.Unavailable
        if (appId.isBlank() || !appId.contains('.')) return FlatpakOverrideResult.Invalid
        val flags = overrideFlags(permission, enabled)
        if (flags.isEmpty()) return FlatpakOverrideResult.Invalid
        val args = mutableListOf("flatpak", "override", "--user")
        args += flags
        args += appId
        val r = Sys.run(*args.toTypedArray(), timeoutSec = 15)
        return if (r.ok) FlatpakOverrideResult.Applied else FlatpakOverrideResult.Failed
    }

    fun applyTier(appId: String, tier: FlatpakSandboxTier): FlatpakOverrideResult {
        if (!available()) return FlatpakOverrideResult.Unavailable
        if (appId.isBlank() || !appId.contains('.')) return FlatpakOverrideResult.Invalid
        val args = mutableListOf("flatpak", "override", "--user")
        args += tierFlags(tier)
        args += appId
        val r = Sys.run(*args.toTypedArray(), timeoutSec = 15)
        return if (r.ok) FlatpakOverrideResult.Applied else FlatpakOverrideResult.Failed
    }

    fun reset(appId: String): FlatpakOverrideResult {
        if (!available()) return FlatpakOverrideResult.Unavailable
        if (appId.isBlank() || !appId.contains('.')) return FlatpakOverrideResult.Invalid
        val r = Sys.run("flatpak", "override", "--user", "--reset", appId, timeoutSec = 15)
        return if (r.ok) FlatpakOverrideResult.Applied else FlatpakOverrideResult.Failed
    }

    internal fun overrideFlags(permission: FlatpakPermission, enabled: Boolean): List<String> = when (permission) {
        FlatpakPermission.FilesystemHome ->
            listOf(if (enabled) "--filesystem=home" else "--nofilesystem=home")
        FlatpakPermission.FilesystemHost ->
            listOf(if (enabled) "--filesystem=host" else "--nofilesystem=host")
        FlatpakPermission.Network ->
            listOf(if (enabled) "--share=network" else "--unshare=network")
        FlatpakPermission.Gpu ->
            listOf(if (enabled) "--device=dri" else "--nodevice=dri")
        FlatpakPermission.Wayland ->
            listOf(if (enabled) "--socket=wayland" else "--nosocket=wayland")
        FlatpakPermission.X11 ->
            listOf(if (enabled) "--socket=x11" else "--nosocket=x11")
        FlatpakPermission.PulseAudio ->
            listOf(if (enabled) "--socket=pulseaudio" else "--nosocket=pulseaudio")
        FlatpakPermission.Notifications ->
            if (enabled) {
                listOf("--talk-name=org.freedesktop.Notifications")
            } else {
                listOf("--no-talk-name=org.freedesktop.Notifications")
            }
        FlatpakPermission.Camera ->
            listOf(if (enabled) "--device=all" else "--nodevice=all")
        FlatpakPermission.Microphone ->
            listOf(if (enabled) "--socket=pulseaudio" else "--nosocket=pulseaudio")
    }

    internal fun tierFlags(tier: FlatpakSandboxTier): List<String> = when (tier) {
        FlatpakSandboxTier.Host -> listOf(
            "--filesystem=host",
            "--filesystem=home",
            "--share=network",
        )
        FlatpakSandboxTier.VaultFs -> listOf(
            "--nofilesystem=host",
            "--filesystem=home",
            "--share=network",
        )
        FlatpakSandboxTier.NetIsolated -> listOf(
            "--unshare=network",
            "--filesystem=home",
            "--nofilesystem=host",
        )
        FlatpakSandboxTier.Standard -> listOf(
            "--nofilesystem=host",
            "--filesystem=home",
            "--share=network",
        )
    }

    internal fun parsePermissionText(appId: String, text: String): FlatpakPermissionState {
        val shared = contextValues(text, "shared")
        val sockets = contextValues(text, "sockets")
        val devices = contextValues(text, "devices")
        val filesystems = contextValues(text, "filesystems")
        val talk = sessionBusTalk(text)

        val perms = linkedMapOf(
            FlatpakPermission.FilesystemHome to filesystems.any { it == "home" || it.startsWith("home:") },
            FlatpakPermission.FilesystemHost to filesystems.any { it == "host" || it.startsWith("host:") },
            FlatpakPermission.Network to shared.contains("network"),
            FlatpakPermission.Gpu to devices.contains("dri"),
            FlatpakPermission.Wayland to sockets.contains("wayland"),
            FlatpakPermission.X11 to sockets.contains("x11"),
            FlatpakPermission.PulseAudio to sockets.contains("pulseaudio"),
            FlatpakPermission.Notifications to talk.contains("org.freedesktop.Notifications"),
            FlatpakPermission.Camera to devices.contains("all"),
            FlatpakPermission.Microphone to sockets.contains("pulseaudio"),
        )
        return FlatpakPermissionState(
            appId = appId,
            permissions = perms,
            tier = inferTier(perms),
            rawOverride = text.trim(),
        )
    }

    internal fun inferTier(permissions: Map<FlatpakPermission, Boolean>): FlatpakSandboxTier {
        val host = permissions[FlatpakPermission.FilesystemHost] == true
        val net = permissions[FlatpakPermission.Network] == true
        return when {
            host -> FlatpakSandboxTier.Host
            !net -> FlatpakSandboxTier.NetIsolated
            permissions[FlatpakPermission.FilesystemHome] == true -> FlatpakSandboxTier.VaultFs
            else -> FlatpakSandboxTier.Standard
        }
    }

    private fun contextValues(text: String, key: String): Set<String> {
        val re = Regex("""(?im)^\s*$key\s*=\s*(.*)$""")
        val m = re.find(text) ?: return emptySet()
        return m.groupValues[1]
            .split(';', ',', ' ')
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    private fun sessionBusTalk(text: String): Set<String> {
        val out = LinkedHashSet<String>()
        var inSession = false
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            when {
                line.equals("[Session Bus Policy]", ignoreCase = true) -> inSession = true
                line.startsWith("[") -> inSession = false
                inSession && line.contains('=') -> {
                    val name = line.substringBefore('=').trim()
                    val policy = line.substringAfter('=').trim().lowercase()
                    if (policy == "talk" || policy == "own") out += name
                }
            }
        }
        Regex("""(?im)talk-name\s*=\s*([^\s;]+)""").findAll(text).forEach { out += it.groupValues[1] }
        return out
    }
}
