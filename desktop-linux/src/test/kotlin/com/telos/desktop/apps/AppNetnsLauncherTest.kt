package com.telos.desktop.apps

import com.telos.desktop.network.DesktopNetworkController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNetnsLauncherTest {
    @Test
    fun detectFromWhichPrefersUnshareThenIp() {
        assertEquals(
            AppNetnsCapability.UnshareNet,
            AppNetnsLauncher.detectFromWhich(
                mapOf("unshare" to "/usr/bin/unshare", "ip" to "/sbin/ip"),
                netnsName = null,
            ),
        )
        assertEquals(
            AppNetnsCapability.IpNetns,
            AppNetnsLauncher.detectFromWhich(
                mapOf("ip" to "/sbin/ip"),
                netnsName = "telos-iso",
            ),
        )
        assertEquals(
            AppNetnsCapability.Unavailable,
            AppNetnsLauncher.detectFromWhich(emptyMap(), netnsName = "telos-iso"),
        )
    }

    @Test
    fun shouldIsolateFromTierOrDenyRoute() {
        assertTrue(
            AppNetnsLauncher.shouldIsolate(
                "org.mozilla.firefox",
                flatpakTier = FlatpakSandboxTier.NetIsolated,
                routes = emptyList(),
            ),
        )
        assertTrue(
            AppNetnsLauncher.shouldIsolate(
                "firefox.desktop",
                flatpakTier = null,
                routes = listOf(
                    DesktopNetworkController.AppRoute(
                        "firefox.desktop",
                        DesktopNetworkController.AppRouteMode.Deny,
                    ),
                ),
            ),
        )
        assertTrue(
            AppNetnsLauncher.shouldIsolate(
                "firefox",
                routes = listOf(
                    DesktopNetworkController.AppRoute(
                        "firefox.desktop",
                        DesktopNetworkController.AppRouteMode.Deny,
                    ),
                ),
            ),
        )
        assertFalse(
            AppNetnsLauncher.shouldIsolate(
                "firefox.desktop",
                routes = listOf(
                    DesktopNetworkController.AppRoute(
                        "firefox.desktop",
                        DesktopNetworkController.AppRouteMode.Allow,
                    ),
                ),
            ),
        )
    }

    @Test
    fun buildWrapArgvHonestDegradeAndFlatpakOwned() {
        val argv = listOf("/usr/bin/firefox")
        val wrapped = AppNetnsLauncher.buildWrapArgv(
            argv = argv,
            capability = AppNetnsCapability.UnshareNet,
            netnsName = null,
            isolate = true,
            isFlatpakRun = false,
        )
        assertEquals(AppNetnsWrapKind.Wrapped, wrapped.kind)
        assertEquals(listOf("unshare", "--net", "--", "/usr/bin/firefox"), wrapped.argv)

        val flatpak = AppNetnsLauncher.buildWrapArgv(
            argv = listOf("flatpak", "run", "org.mozilla.firefox"),
            capability = AppNetnsCapability.UnshareNet,
            netnsName = null,
            isolate = true,
            isFlatpakRun = true,
        )
        assertEquals(AppNetnsWrapKind.FlatpakOwned, flatpak.kind)
        assertEquals(listOf("flatpak", "run", "org.mozilla.firefox"), flatpak.argv)

        val missing = AppNetnsLauncher.buildWrapArgv(
            argv = argv,
            capability = AppNetnsCapability.Unavailable,
            netnsName = null,
            isolate = true,
            isFlatpakRun = false,
        )
        assertEquals(AppNetnsWrapKind.PreferenceOnly, missing.kind)
        assertEquals(argv, missing.argv)

        val ip = AppNetnsLauncher.buildWrapArgv(
            argv = argv,
            capability = AppNetnsCapability.IpNetns,
            netnsName = "telos-iso",
            isolate = true,
            isFlatpakRun = false,
        )
        assertEquals(listOf("ip", "netns", "exec", "telos-iso", "/usr/bin/firefox"), ip.argv)
    }
}
