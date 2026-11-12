package com.telos.desktop.session

import com.telos.desktop.linux.Sys
import com.telos.desktop.platform.PlatformEnvironment
import com.telos.desktop.platform.TelosRuntimePlatform
import com.telos.desktop.vault.PortableEnvironmentIsolator
import java.io.File
import java.util.Properties

/**
 * VOS-3 xdg-desktop-portal Device/Camera/Microphone helper.
 *
 * Records requested preference under vault `~/.config/telos/portal-devices.properties`.
 * When portal tools exist (`xdg-desktop-portal` + `gdbus`/`dbus-send`), invokes an AccessDevice
 * ask. Otherwise returns PreferenceOnly — never claims a host grant without tools.
 */
enum class PortalDeviceKind {
    Camera,
    Microphone,
}

enum class PortalDeviceAskResult {
    /** Preference stored; portal dialog was started (response is async / host-owned). */
    Requested,
    /** Preference stored; no portal tooling on this host (honest degrade). */
    PreferenceOnly,
    Unavailable,
    Failed,
    Invalid,
}

object PortalDeviceAccess {
    private val isLinux: Boolean
        get() = PlatformEnvironment.current == TelosRuntimePlatform.LinuxDesktop

    fun configFile(): File {
        val home = PortableEnvironmentIsolator.vaultHome ?: File(System.getProperty("user.home"))
        return File(File(home, ".config/telos").apply { mkdirs() }, "portal-devices.properties")
    }

    fun portalBinaryPresent(): Boolean =
        Sys.which("xdg-desktop-portal") != null ||
            Sys.which("xdg-desktop-portal-gtk") != null ||
            Sys.which("xdg-desktop-portal-wlr") != null

    fun dbusToolPresent(): Boolean =
        Sys.which("gdbus") != null || Sys.which("dbus-send") != null

    /** Portal ask is only attempted on Linux when both a portal daemon binary and a D-Bus CLI exist. */
    fun available(): Boolean = isLinux && portalBinaryPresent() && dbusToolPresent()

    fun readRequested(kind: PortalDeviceKind): Boolean {
        val props = loadProps()
        return props.getProperty(kind.name.lowercase(), "false").equals("true", ignoreCase = true)
    }

    fun readAll(): Map<PortalDeviceKind, Boolean> =
        PortalDeviceKind.entries.associateWith { readRequested(it) }

    /**
     * Persist the user toggle and, when tools exist, invoke a portal AccessDevice ask.
     * Turning off only clears the preference (does not revoke host ACL — portals own that).
     */
    fun setRequested(kind: PortalDeviceKind, enabled: Boolean): PortalDeviceAskResult {
        if (!isLinux) {
            writeRequested(kind, enabled)
            return PortalDeviceAskResult.PreferenceOnly
        }
        writeRequested(kind, enabled)
        if (!enabled) return PortalDeviceAskResult.PreferenceOnly
        if (!available()) return PortalDeviceAskResult.PreferenceOnly
        return requestAccess(kind)
    }

    fun requestAccess(kind: PortalDeviceKind): PortalDeviceAskResult {
        if (!isLinux) return PortalDeviceAskResult.PreferenceOnly
        if (!available()) return PortalDeviceAskResult.PreferenceOnly
        val device = when (kind) {
            PortalDeviceKind.Camera -> "camera"
            PortalDeviceKind.Microphone -> "microphone"
        }
        val gdbus = Sys.which("gdbus")
        if (gdbus != null) {
            // AccessDevice(u pid, as devices, a{sv} options) → o handle
            val r = Sys.run(
                gdbus, "call", "--session",
                "--dest", "org.freedesktop.portal.Desktop",
                "--object-path", "/org/freedesktop/portal/desktop",
                "--method", "org.freedesktop.portal.Device.AccessDevice",
                "0", "['$device']", "{}",
                timeoutSec = 20,
            )
            return if (r.ok || r.stdout.contains("/org/freedesktop/portal/desktop/request")) {
                PortalDeviceAskResult.Requested
            } else {
                // Fall back to Camera portal for camera-only hosts.
                if (kind == PortalDeviceKind.Camera) requestCameraPortal() else PortalDeviceAskResult.Failed
            }
        }
        val dbusSend = Sys.which("dbus-send") ?: return PortalDeviceAskResult.Unavailable
        val r = Sys.run(
            dbusSend, "--session", "--type=method_call", "--print-reply",
            "--dest=org.freedesktop.portal.Desktop",
            "/org/freedesktop/portal/desktop",
            "org.freedesktop.portal.Device.AccessDevice",
            "uint32:0", "array:string:$device", "dict:string:string:",
            timeoutSec = 20,
        )
        return if (r.ok) PortalDeviceAskResult.Requested else PortalDeviceAskResult.Failed
    }

    private fun requestCameraPortal(): PortalDeviceAskResult {
        val gdbus = Sys.which("gdbus") ?: return PortalDeviceAskResult.Failed
        val r = Sys.run(
            gdbus, "call", "--session",
            "--dest", "org.freedesktop.portal.Desktop",
            "--object-path", "/org/freedesktop/portal/desktop",
            "--method", "org.freedesktop.portal.Camera.AccessCamera",
            "{}",
            timeoutSec = 20,
        )
        return if (r.ok || r.stdout.contains("/org/freedesktop/portal/desktop/request")) {
            PortalDeviceAskResult.Requested
        } else {
            PortalDeviceAskResult.Failed
        }
    }

    private fun writeRequested(kind: PortalDeviceKind, enabled: Boolean) {
        val props = loadProps()
        props.setProperty(kind.name.lowercase(), enabled.toString())
        configFile().outputStream().use { props.store(it, "Telos portal device preferences (VOS-3)") }
        runCatching {
            configFile().setReadable(true, true)
            configFile().setWritable(true, true)
        }
    }

    private fun loadProps(): Properties {
        val props = Properties()
        val file = configFile()
        if (file.isFile) {
            file.inputStream().use { props.load(it) }
        }
        return props
    }

    /**
     * Parse a mocked `which` map for unit tests (command → path or null).
     */
    internal fun detectFromWhich(which: Map<String, String?>): Boolean {
        val portal = !which["xdg-desktop-portal"].isNullOrBlank() ||
            !which["xdg-desktop-portal-gtk"].isNullOrBlank() ||
            !which["xdg-desktop-portal-wlr"].isNullOrBlank()
        val dbus = !which["gdbus"].isNullOrBlank() || !which["dbus-send"].isNullOrBlank()
        return portal && dbus
    }

    internal fun deviceToken(kind: PortalDeviceKind): String = when (kind) {
        PortalDeviceKind.Camera -> "camera"
        PortalDeviceKind.Microphone -> "microphone"
    }
}
