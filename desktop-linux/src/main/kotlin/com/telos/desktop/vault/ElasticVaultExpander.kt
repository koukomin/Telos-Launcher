package com.telos.desktop.vault

import com.telos.desktop.linux.Sys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.nio.file.FileStore
import java.nio.file.Files

class ElasticVaultExpander(
    private val mountPoint: File,
    private val cryptName: String,
    private val shards: StegoShardArrayManager,
) {
    var autoGrowEnabled: Boolean = true
    var incrementBytes: Long = 64L * 1024 * 1024
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        stop()
        job = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(15_000)
                if (autoGrowEnabled) maybeGrow()
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    fun maybeGrow(): Boolean {
        val store = store() ?: return false
        val usable = store.usableSpace
        val total = store.totalSpace.coerceAtLeast(1)
        val lowPct = usable.toDouble() / total < 0.15
        val lowAbs = usable < 256L * 1024 * 1024
        if (!lowPct && !lowAbs) return false
        return expand(incrementBytes)
    }

    fun expand(bytes: Long): Boolean {
        shards.expandEvenly(bytes)
        Sys.run("pkexec", "cryptsetup", "resize", cryptName, timeoutSec = 30)
        Sys.run("cryptsetup", "resize", cryptName, timeoutSec = 30)
        val mp = mountPoint.absolutePath
        val btrfs = Sys.run("pkexec", "btrfs", "filesystem", "resize", "max", mp, timeoutSec = 30)
        if (!btrfs.ok) {
            Sys.run("pkexec", "resize2fs", "/dev/mapper/$cryptName", timeoutSec = 40)
        }
        return true
    }

    fun compact() {
        Sys.run("pkexec", "fstrim", "-v", mountPoint.absolutePath, timeoutSec = 60)
        Sys.run("fstrim", "-v", mountPoint.absolutePath, timeoutSec = 30)
        Sys.run("sync")
    }

    fun formatAndMount(device: String): Boolean {
        mountPoint.mkdirs()
        val btrfs = Sys.which("mkfs.btrfs") != null
        val mk = if (btrfs) {
            Sys.run("pkexec", "mkfs.btrfs", "-f", device, timeoutSec = 40)
        } else {
            Sys.run("pkexec", "mkfs.ext4", "-F", device, timeoutSec = 40)
        }
        if (!mk.ok && btrfs) Sys.run("mkfs.btrfs", "-f", device, timeoutSec = 40)
        val opts = if (btrfs) "compress=zstd:3,noatime,ssd,discard=async" else "noatime,discard"
        val mount = Sys.run("pkexec", "mount", "-o", opts, device, mountPoint.absolutePath, timeoutSec = 20)
        return mount.ok || Sys.run("mount", "-o", opts, device, mountPoint.absolutePath, timeoutSec = 20).ok
    }

    private fun store(): FileStore? = runCatching { Files.getFileStore(mountPoint.toPath()) }.getOrNull()
}
