package com.telos.desktop.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlatpakSandboxTest {
    @Test
    fun parsesOverridePermissionsAndInfersHostTier() {
        val text = """
            [Context]
            shared=network;ipc;
            sockets=x11;wayland;pulseaudio;
            devices=dri;
            filesystems=home;host;

            [Session Bus Policy]
            org.freedesktop.Notifications=talk
        """.trimIndent()
        val state = FlatpakSandbox.parsePermissionText("org.mozilla.firefox", text)
        assertTrue(state.permissions[FlatpakPermission.Network] == true)
        assertTrue(state.permissions[FlatpakPermission.FilesystemHome] == true)
        assertTrue(state.permissions[FlatpakPermission.FilesystemHost] == true)
        assertTrue(state.permissions[FlatpakPermission.Wayland] == true)
        assertTrue(state.permissions[FlatpakPermission.Notifications] == true)
        assertEquals(FlatpakSandboxTier.Host, state.tier)
    }

    @Test
    fun infersNetIsolatedWhenNetworkMissing() {
        val text = """
            [Context]
            shared=ipc;
            sockets=wayland;
            filesystems=home;
        """.trimIndent()
        val state = FlatpakSandbox.parsePermissionText("org.example.app", text)
        assertFalse(state.permissions[FlatpakPermission.Network] == true)
        assertEquals(FlatpakSandboxTier.NetIsolated, state.tier)
    }

    @Test
    fun tierFlagsIncludeExpectedOverrides() {
        val vault = FlatpakSandbox.tierFlags(FlatpakSandboxTier.VaultFs)
        assertTrue(vault.contains("--nofilesystem=host"))
        assertTrue(vault.contains("--filesystem=home"))
        assertTrue(vault.contains("--share=network"))
        val net = FlatpakSandbox.tierFlags(FlatpakSandboxTier.NetIsolated)
        assertTrue(net.contains("--unshare=network"))
    }

    @Test
    fun permissionFlagsToggleNetwork() {
        assertEquals(listOf("--share=network"), FlatpakSandbox.overrideFlags(FlatpakPermission.Network, true))
        assertEquals(listOf("--unshare=network"), FlatpakSandbox.overrideFlags(FlatpakPermission.Network, false))
    }
}
