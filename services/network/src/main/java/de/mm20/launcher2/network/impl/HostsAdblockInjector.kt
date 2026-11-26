package de.mm20.launcher2.network.impl

import android.util.Log
import de.mm20.launcher2.ktx.PrivilegedShell

/**
 * Kernel-Level Hosts File Adblock Injector via Root.
 * Mounts a custom /etc/hosts file to block ads & telemetry system-wide
 * with zero VPN or background battery overhead.
 */
object HostsAdblockInjector {
    private const val TAG = "HostsAdblockInjector"

    suspend fun applyAdblockHosts(hostsPath: String): Boolean {
        if (!PrivilegedShell.rootAvailable()) return false
        return runCatching {
            PrivilegedShell.run("mount -o remount,rw /system 2>/dev/null || mount -o remount,rw /")
            PrivilegedShell.run("mount --bind $hostsPath /etc/hosts")
            Log.i(TAG, "Applied adblock hosts file from $hostsPath")
            true
        }.getOrDefault(false)
    }

    suspend fun unmountHosts(): Boolean {
        if (!PrivilegedShell.rootAvailable()) return false
        return runCatching {
            PrivilegedShell.run("umount /etc/hosts")
            Log.i(TAG, "Unmounted custom hosts file")
            true
        }.getOrDefault(false)
    }
}
