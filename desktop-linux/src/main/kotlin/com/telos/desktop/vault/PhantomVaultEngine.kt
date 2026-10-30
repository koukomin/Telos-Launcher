package com.telos.desktop.vault

import com.telos.desktop.linux.ProcMounts
import com.telos.desktop.linux.Sys
import kotlinx.coroutines.CoroutineScope
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path

enum class VolumeRole { Outer, Inner, Duress, Unknown }

object PhantomVault {
    lateinit var engine: PhantomVaultEngine
}

class PhantomVaultEngine(
    private val hostHome: Path = TelosSessionHome.hostHome,
) {
    val luks = LuksVaultManager(hostHome)
    val shards = StegoShardArrayManager()
    val cloud = VaultCloudSyncManager()
    val opsec = TelosOpSecGuard()
    val hw = HardwareCryptoAccelerator.probe()
    var expander: ElasticVaultExpander? = null
    var bootMode: BootMode = BootMode.UnlockExisting
    var lastRole: VolumeRole = VolumeRole.Unknown
    var carriers: List<File> = emptyList()
    var autoGrow: Boolean = true
    var isolateHome: Boolean = true
    val cryptName = "telos_crypt_active"
    val cryptPath get() = "/dev/mapper/$cryptName"

    val configDir: File get() = luks.configDir
    val mountPoint: File get() = luks.mountPoint
    val unlocked get() = luks.unlocked

    fun unlock(password: String, carrier: File?, initialize: Boolean): LuksVaultManager.UnlockResult {
        opsec.enableCoreDumpBlock()
        if (bootMode == BootMode.HostHome) {
            PortableEnvironmentIsolator.clear()
            luks.mountPoint.mkdirs()
            luks.unlock(password, initializeIfMissing = false)
            return LuksVaultManager.UnlockResult.Unlocked
        }
        if (bootMode == BootMode.EphemeralGhost) return ephemeral()
        val files = when {
            carrier == null -> listOf(luks.vaultImage)
            else -> shards.listCarriers(carrier).ifEmpty { listOf(carrier) }
        }
        carriers = files
        val salt = HardwareCryptoAccelerator.hmacSha512(password.toByteArray(), "telos-salt".toByteArray()).copyOf(16)
        val master = HybridPqcHeader.deriveVolumeKey(password.toCharArray(), salt)
        if (isDuress(master, files)) {
            lastRole = VolumeRole.Duress
            shards.wipeInnerHeaders(shards.preparePool(master, files, 8L * 1024 * 1024))
            return openOuter(master, files, password, decoy = true)
        }
        val inner = tryInner(master, files, password)
        if (inner.ok) {
            lastRole = VolumeRole.Inner
            return inner
        }
        lastRole = VolumeRole.Outer
        return openOuter(master, files, password, decoy = false)
    }

    fun lock(syncCloud: Boolean = true) {
        expander?.stop()
        runCatching { com.telos.desktop.session.SessionNetGuard.clear() }
        Sys.run("sync", timeoutSec = 30)
        Sys.run("pkexec", "umount", mountPoint.absolutePath)
        Sys.run("umount", mountPoint.absolutePath)
        Sys.run("pkexec", "cryptsetup", "close", cryptName)
        Sys.run("cryptsetup", "close", cryptName)
        if (syncCloud) cloud.syncCarriers(carriers)
        shards.detach()
        PortableEnvironmentIsolator.clear()
        luks.lock()
        opsec.stop()
    }

    fun panicUnmount() {
        shards.wipeInnerHeaders(shards.shards.toList())
        lock(syncCloud = false)
        Sys.run("labwc", "--exit", timeoutSec = 5)
    }

    fun expandManual() {
        expander?.expand(128L * 1024 * 1024)
    }

    fun compact() {
        expander?.compact()
    }

    fun startDaemons(scope: CoroutineScope) {
        expander?.autoGrowEnabled = autoGrow
        expander?.start(scope)
        opsec.panic = { panicUnmount() }
        opsec.startUsbDeadMan(scope)
    }

    fun createElastic(passwordOuter: String, passwordInner: String, passwordDuress: String, pool: File): LuksVaultManager.UnlockResult {
        val files = shards.listCarriers(pool).ifEmpty { listOf(pool) }
        files.forEach { if (!it.exists()) it.writeBytes(ByteArray(0)) }
        val salt = HardwareCryptoAccelerator.hmacSha512(passwordOuter.toByteArray(), "telos-salt".toByteArray()).copyOf(16)
        val outerKey = HybridPqcHeader.deriveVolumeKey(passwordOuter.toCharArray(), salt)
        markDuress(passwordDuress, files)
        val bindings = shards.preparePool(outerKey, files, 20L * 1024 * 1024)
        if (!shards.attachSpan(bindings)) return LuksVaultManager.UnlockResult.Failed("dmsetup span failed")
        formatCrypt(outerKey, passwordOuter)
        val innerOffset = innerOffsetSectors(passwordInner)
        Sys.run(
            "pkexec", "cryptsetup", "-q", "luksFormat",
            "--type", "luks2", "--cipher", HardwareCryptoAccelerator.cipherSpec(),
            "--key-size", "512", "--offset", innerOffset.toString(),
            shards.mapperPath,
            timeoutSec = 60,
            input = passwordInner + "\n" + passwordInner + "\n",
        )
        return unlock(passwordInner, pool, false)
    }

    private fun tryInner(master: ByteArray, files: List<File>, password: String): LuksVaultManager.UnlockResult {
        val bindings = shards.preparePool(master, files)
        if (!shards.attachSpan(bindings)) return LuksVaultManager.UnlockResult.Failed("span")
        val off = innerOffsetSectors(password)
        val open = Sys.run(
            *(listOf("pkexec", "cryptsetup", "open", shards.mapperPath, cryptName, "--offset", off.toString()) + HardwareCryptoAccelerator.cryptsetupPerfFlags()).toTypedArray(),
            timeoutSec = 40,
            input = password + "\n",
        )
        if (!open.ok) {
            shards.detach()
            return LuksVaultManager.UnlockResult.Failed("inner")
        }
        return afterOpen(decoy = false)
    }

    private fun openOuter(master: ByteArray, files: List<File>, password: String, decoy: Boolean): LuksVaultManager.UnlockResult {
        val bindings = shards.preparePool(master, files)
        if (!shards.attachSpan(bindings)) {
            return luks.unlock(password, initializeIfMissing = true)
        }
        val opened = HardwareCryptoAccelerator.pkcsOpen(shards.mapperPath, cryptName, password)
        if (!opened) {
            formatCrypt(master, password)
            HardwareCryptoAccelerator.pkcsOpen(shards.mapperPath, cryptName, password)
        }
        return afterOpen(decoy)
    }

    private fun afterOpen(decoy: Boolean): LuksVaultManager.UnlockResult {
        val device = cryptPath
        if (!File(device).exists()) return LuksVaultManager.UnlockResult.Failed("mapper missing")
        expander = ElasticVaultExpander(mountPoint, cryptName, shards)
        val mounted = isMounted() || expander!!.formatAndMount(device) || mountExisting(device)
        if (!mounted) return LuksVaultManager.UnlockResult.Failed("mount")
        mountPoint.mkdirs()
        File(mountPoint, "config").mkdirs()
        File(mountPoint, "state").mkdirs()
        File(mountPoint, "db").mkdirs()
        if (decoy) seedDecoy()
        if (isolateHome) PortableEnvironmentIsolator.apply(mountPoint, fullSessionHome = TelosSessionHome.full)
        luks.ensureLayout()
        luks.unlocked = true
        return LuksVaultManager.UnlockResult.Unlocked
    }

    private fun mountExisting(device: String): Boolean {
        mountPoint.mkdirs()
        return Sys.run("pkexec", "mount", "-o", "compress=zstd:3,noatime,ssd,discard=async", device, mountPoint.absolutePath).ok ||
            Sys.run("pkexec", "mount", device, mountPoint.absolutePath).ok
    }

    private fun formatCrypt(master: ByteArray, password: String) {
        val args = listOf(
            "cryptsetup", "-q", "luksFormat", "--type", "luks2",
            "--cipher", HardwareCryptoAccelerator.cipherSpec(), "--key-size", "512",
            "--pbkdf", "argon2id", shards.mapperPath,
        )
        Sys.run("pkexec", *args.toTypedArray(), timeoutSec = 60, input = password + "\n" + password + "\n")
    }

    private fun seedDecoy() {
        File(mountPoint, "home/Desktop").mkdirs()
        File(mountPoint, "home/Documents").mkdirs()
        File(mountPoint, "home/Documents/notes.txt").writeText("Shopping list\n- milk\n- bread\n")
    }

    private fun ephemeral(): LuksVaultManager.UnlockResult {
        val tmp = File("/dev/shm/telos-ghost")
        tmp.mkdirs()
        Sys.run("mount", "-t", "tmpfs", "-o", "size=512M", "tmpfs", tmp.absolutePath)
        PortableEnvironmentIsolator.apply(tmp)
        luks.ensureLayout()
        return LuksVaultManager.UnlockResult.Unlocked
    }

    private fun innerOffsetSectors(password: String): Long {
        val h = HardwareCryptoAccelerator.hmacSha512(password.toByteArray(), "inner-offset".toByteArray())
        val n = ((h[0].toLong() and 0xff) shl 8) or (h[1].toLong() and 0xff)
        return 4096L + (n % 1024) * 8
    }

    private fun isDuress(master: ByteArray, files: List<File>): Boolean {
        val mark = File(hostHome.toFile(), ".local/share/telos/.duress")
        if (!mark.isFile) return false
        val expect = mark.readBytes()
        val got = HardwareCryptoAccelerator.hmacSha512(master, "TELOS-DURESS".toByteArray())
        return expect.contentEquals(got)
    }

    private fun markDuress(password: String, files: List<File>) {
        val salt = HardwareCryptoAccelerator.hmacSha512(password.toByteArray(), "telos-salt".toByteArray()).copyOf(16)
        val master = HybridPqcHeader.deriveVolumeKey(password.toCharArray(), salt)
        val dir = File(hostHome.toFile(), ".local/share/telos")
        dir.mkdirs()
        File(dir, ".duress").writeBytes(HardwareCryptoAccelerator.hmacSha512(master, "TELOS-DURESS".toByteArray()))
    }

    fun attachMounted(): Boolean {
        if (!isMounted()) return false
        File(mountPoint, "config").mkdirs()
        File(mountPoint, "state").mkdirs()
        File(mountPoint, "db").mkdirs()
        if (isolateHome) PortableEnvironmentIsolator.apply(mountPoint, fullSessionHome = TelosSessionHome.full)
        luks.unlocked = true
        return true
    }

    private fun isMounted(): Boolean {
        val mounts = runCatching { Files.readString(Path("/proc/self/mounts")) }.getOrDefault("")
        return ProcMounts.containsPath(mounts, mountPoint.absolutePath) ||
            ProcMounts.containsPath(mounts, TelosSessionHome.vaultMount?.absolutePath)
    }
}
