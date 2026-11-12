package com.telos.desktop.apps

import com.telos.desktop.network.DesktopNetworkController
import com.telos.desktop.network.DesktopNetworkController.AppRoute
import com.telos.desktop.network.DesktopNetworkController.AppRouteMode
import com.telos.desktop.network.DesktopNetworkController.LaunchRouteDecision
import com.telos.desktop.platform.PlatformEnvironment
import com.telos.desktop.platform.TelosRuntimePlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DesktopFileLauncherRoutingTest {
    @Test
    fun desktopIdCandidatesFromFileNameAndFlatpakExec() {
        val ids = DesktopNetworkController.desktopIdCandidates(
            "org.mozilla.firefox.desktop",
            "flatpak run org.mozilla.firefox",
        )
        assertTrue(ids.contains("org.mozilla.firefox.desktop"))
        assertTrue(ids.contains("org.mozilla.firefox"))
    }

    @Test
    fun denyLaunchGateBlocksMatchingDesktopId() {
        val routes = listOf(
            AppRoute("evil.desktop", AppRouteMode.Deny),
            AppRoute("org.ok.app", AppRouteMode.Allow),
        )
        assertEquals(
            LaunchRouteDecision.Deny,
            DesktopNetworkController.launchRouteDecision(
                listOf("evil.desktop", "evil"),
                routes,
            ),
        )
        assertEquals(
            LaunchRouteDecision.Allow,
            DesktopNetworkController.launchRouteDecision(
                listOf("org.ok.app"),
                routes,
            ),
        )
        assertEquals(
            LaunchRouteDecision.Vpn,
            DesktopNetworkController.launchRouteDecision(
                listOf("vpn.app"),
                listOf(AppRoute("vpn.app", AppRouteMode.Vpn)),
            ),
        )
    }

    @Test
    fun appRoutingEnforcedWhenLinuxHasDenyRules() {
        val withDeny = listOf(AppRoute("blocked.desktop", AppRouteMode.Deny))
        val allowOnly = listOf(AppRoute("ok.desktop", AppRouteMode.Allow))
        if (PlatformEnvironment.current == TelosRuntimePlatform.LinuxDesktop) {
            assertTrue(DesktopNetworkController.appRoutingEnforced(withDeny))
            assertFalse(DesktopNetworkController.appRoutingEnforced(allowOnly))
            assertFalse(DesktopNetworkController.appRoutingEnforced(emptyList()))
        } else {
            assertFalse(DesktopNetworkController.appRoutingEnforced(withDeny))
        }
    }

    @Test
    fun launchReturnsFalseForDeniedDesktopFile() {
        val dir = File.createTempFile("telos-desk", ".dir").also {
            it.delete()
            it.mkdirs()
        }
        val desk = File(dir, "blocked-app.desktop")
        desk.writeText(
            """
            [Desktop Entry]
            Type=Application
            Name=Blocked
            Exec=/usr/bin/true
            """.trimIndent(),
        )
        // Persist deny rule into vault config path used by the controller.
        val prevHome = System.getProperty("user.home")
        val fakeHome = File.createTempFile("telos-home", ".dir").also {
            it.delete()
            it.mkdirs()
        }
        try {
            System.setProperty("user.home", fakeHome.absolutePath)
            DesktopNetworkController.writeAppRoutes(
                listOf(AppRoute("blocked-app.desktop", AppRouteMode.Deny)),
            )
            // Launch gate consults the table; deny must not start a process.
            assertFalse(DesktopFileLauncher.launch(desk))
        } finally {
            System.setProperty("user.home", prevHome)
            dir.deleteRecursively()
            fakeHome.deleteRecursively()
        }
    }
}
