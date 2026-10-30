package com.telos.desktop.linux

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcMountsTest {
    @Test
    fun matchesMountPointColumn() {
        val mounts = "/dev/mapper/telos /home/user/.local/share/telos/vault_mnt btrfs rw 0 0\n"
        assertTrue(
            ProcMounts.containsPath(mounts, "/home/user/.local/share/telos/vault_mnt"),
        )
        assertFalse(
            ProcMounts.containsPath(mounts, "/home/user/.local/share/telos/vault_mnt_backup"),
        )
        assertFalse(
            ProcMounts.containsPath(mounts, "/home/user/.local/share/telos"),
        )
    }
}
