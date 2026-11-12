package com.telos.desktop.apps

import com.telos.desktop.linux.Sys
import com.telos.desktop.network.DesktopNetworkController
import com.telos.desktop.platform.PlatformEnvironment
import com.telos.desktop.platform.TelosRuntimePlatform

/**
 * VOS-3 optional Linux network-namespace launch wrapper.
 *
 * Isolates host launches when an app is marked net-isolated (Flatpak NetIsolated tier)
 * **or** app-routing is `deny` → treat as isolated. Uses `unshare --net` or
 * `ip netns exec <name>` only when those tools exist.
 *
 * Flatpak apps keep Flatpak's own `--unshare=network` override; this wrapper does **not**
 * claim a host netns for Flatpak without tools, and never double-wraps `flatpak run`.
 */
enum class AppNetnsCapability {
    Unavailable,
    UnshareNet,
    IpNetns,
}

enum class AppNetnsWrapKind {
    /** No isolation requested. */
    Unchanged,
    /** Argv rewritten with unshare/ip netns. */
    Wrapped,
    /** Isolation wanted but host tools missing — launch unchanged, honest degrade. */
    PreferenceOnly,
    /** Flatpak already owns sandbox network; do not claim host netns. */
    FlatpakOwned,
}

data class AppNetnsWrapResult(
    val kind: AppNetnsWrapKind,
    val argv: List<String>,
    val capability: AppNetnsCapability,
)

object AppNetnsLauncher {
    private val isLinux: Boolean
        get() = PlatformEnvironment.current == TelosRuntimePlatform.LinuxDesktop

    /** Optional named netns (vault preference). Empty → prefer `unshare --net`. */
    fun netnsNameFile(): java.io.File =
        java.io.File(DesktopNetworkController.configHome(), "app-netns.name")

    fun readNetnsName(): String? {
        val file = netnsNameFile()
        if (!file.isFile) return null
        return file.readText().trim().takeIf { it.matches(Regex("^[A-Za-z0-9_-]{1,32}$")) }
    }

    fun detectCapability(): AppNetnsCapability {
        if (!isLinux) return AppNetnsCapability.Unavailable
        val ns = readNetnsName()
        if (!ns.isNullOrBlank() && Sys.which("ip") != null) return AppNetnsCapability.IpNetns
        if (Sys.which("unshare") != null) return AppNetnsCapability.UnshareNet
        if (Sys.which("ip") != null) return AppNetnsCapability.IpNetns
        return AppNetnsCapability.Unavailable
    }

    /**
     * True when Flatpak tier is NetIsolated or app-routing mode is Deny for [appId].
     */
    fun shouldIsolate(
        appId: String?,
        flatpakTier: FlatpakSandboxTier? = null,
        routes: List<DesktopNetworkController.AppRoute> = DesktopNetworkController.readAppRoutes(),
    ): Boolean {
        if (flatpakTier == FlatpakSandboxTier.NetIsolated) return true
        if (appId.isNullOrBlank()) return false
        val id = appId.trim()
        val route = routes.firstOrNull { it.id.equals(id, ignoreCase = true) }
            ?: routes.firstOrNull {
                id.endsWith(".desktop", ignoreCase = true) &&
                    it.id.equals(id.removeSuffix(".desktop"), ignoreCase = true)
            }
            ?: routes.firstOrNull {
                !id.endsWith(".desktop", ignoreCase = true) &&
                    it.id.equals("$id.desktop", ignoreCase = true)
            }
        return route?.mode == DesktopNetworkController.AppRouteMode.Deny
    }

    fun maybeWrap(
        argv: List<String>,
        appId: String?,
        flatpakTier: FlatpakSandboxTier? = null,
        isFlatpakRun: Boolean = argv.firstOrNull()?.let { it.endsWith("flatpak") } == true &&
            argv.getOrNull(1) == "run",
    ): AppNetnsWrapResult {
        val cap = detectCapability()
        if (!shouldIsolate(appId, flatpakTier)) {
            return AppNetnsWrapResult(AppNetnsWrapKind.Unchanged, argv, cap)
        }
        if (isFlatpakRun) {
            // Flatpak override --unshare=network is the sandboxed path; do not claim host netns.
            return AppNetnsWrapResult(AppNetnsWrapKind.FlatpakOwned, argv, cap)
        }
        if (!isLinux || cap == AppNetnsCapability.Unavailable) {
            return AppNetnsWrapResult(AppNetnsWrapKind.PreferenceOnly, argv, cap)
        }
        val wrapped = when (cap) {
            AppNetnsCapability.UnshareNet -> listOf("unshare", "--net", "--") + argv
            AppNetnsCapability.IpNetns -> {
                val name = readNetnsName() ?: return AppNetnsWrapResult(
                    AppNetnsWrapKind.PreferenceOnly,
                    argv,
                    cap,
                )
                listOf("ip", "netns", "exec", name) + argv
            }
            AppNetnsCapability.Unavailable -> argv
        }
        return AppNetnsWrapResult(AppNetnsWrapKind.Wrapped, wrapped, cap)
    }

    /**
     * Build wrap argv for tests with injected capability / netns name / isolation flag.
     */
    internal fun buildWrapArgv(
        argv: List<String>,
        capability: AppNetnsCapability,
        netnsName: String?,
        isolate: Boolean,
        isFlatpakRun: Boolean,
    ): AppNetnsWrapResult {
        if (!isolate) return AppNetnsWrapResult(AppNetnsWrapKind.Unchanged, argv, capability)
        if (isFlatpakRun) return AppNetnsWrapResult(AppNetnsWrapKind.FlatpakOwned, argv, capability)
        if (capability == AppNetnsCapability.Unavailable) {
            return AppNetnsWrapResult(AppNetnsWrapKind.PreferenceOnly, argv, capability)
        }
        return when (capability) {
            AppNetnsCapability.UnshareNet ->
                AppNetnsWrapResult(
                    AppNetnsWrapKind.Wrapped,
                    listOf("unshare", "--net", "--") + argv,
                    capability,
                )
            AppNetnsCapability.IpNetns -> {
                if (netnsName.isNullOrBlank()) {
                    AppNetnsWrapResult(AppNetnsWrapKind.PreferenceOnly, argv, capability)
                } else {
                    AppNetnsWrapResult(
                        AppNetnsWrapKind.Wrapped,
                        listOf("ip", "netns", "exec", netnsName) + argv,
                        capability,
                    )
                }
            }
            AppNetnsCapability.Unavailable ->
                AppNetnsWrapResult(AppNetnsWrapKind.PreferenceOnly, argv, capability)
        }
    }

    /** Detect capability from a mocked which() map (unit tests). */
    internal fun detectFromWhich(which: Map<String, String?>, netnsName: String?): AppNetnsCapability {
        if (!netnsName.isNullOrBlank() && !which["ip"].isNullOrBlank()) {
            return AppNetnsCapability.IpNetns
        }
        if (!which["unshare"].isNullOrBlank()) return AppNetnsCapability.UnshareNet
        if (!which["ip"].isNullOrBlank()) return AppNetnsCapability.IpNetns
        return AppNetnsCapability.Unavailable
    }
}
