package com.telos.desktop.vault

import com.telos.desktop.linux.ProcMounts
import com.telos.desktop.linux.Sys
import com.telos.desktop.platform.PlatformEnvironment
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
        when (bootMode) {
            BootMode.PortableData -> return unlockPortableData()
            BootMode.PortableVault -> return unlockPortableVault(password, initialize)
            BootMode.HostHome -> {
                PortableEnvironmentIsolator.clear()
                luks.clearPortableSessionRoot()
                luks.mountPoint.mkdirs()
                luks.unlock(password, initializeIfMissing = false)
                return LuksVaultManager.UnlockResult.Unlocked
            }
            BootMode.EphemeralGhost -> return ephemeral()
            else -> Unit
        }
        val pool = resolveCarrierPool(carrier)
        val files = when {
            pool == null -> listOf(luks.vaultImage)
            else -> shards.listCarriers(pool).ifEmpty { listOf(pool) }
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

    /**
     * Assemble media carriers (manifest + payloads) and open via dm-linear when
     * available, otherwise a portable linear `.tvault` under the allowed root.
     * Does not claim full VOS-6 OpSec; Linux dm path still needs root helpers.
     */
    fun openStegoCarriers(
        password: String,
        pool: File,
        perShardBytes: Long = 20L * 1024 * 1024,
    ): LuksVaultManager.UnlockResult {
        opsec.enableCoreDumpBlock()
        val salt = HardwareCryptoAccelerator.hmacSha512(password.toByteArray(), "telos-salt".toByteArray()).copyOf(16)
        val master = HybridPqcHeader.deriveVolumeKey(password.toCharArray(), salt)
        val linearOut = if (PortableTraceGuard.isPortable() || bootMode == BootMode.PortableVault) {
            val root = PortableTraceGuard.ensurePortableDataRoot()
            PortableTraceGuard.assertNoHostWrites(root)
            PortableStegoFs.assembledImage(root)
        } else {
            File(if (pool.isDirectory) pool else pool.parentFile ?: pool, PortableStegoFs.ASSEMBLED_IMAGE)
        }
        val assembled = shards.assembleAndOpen(
            masterKey = master,
            pool = pool,
            perShardBytes = perShardBytes,
            preferDeviceMapper = PlatformEnvironment.settings.showLuksVault,
            linearImageOut = linearOut,
        )
        carriers = when (assembled) {
            is StegoAssembleResult.DeviceMapper -> assembled.bindings.map { it.file }
            is StegoAssembleResult.LinearImage -> assembled.bindings.map { it.file }
            is StegoAssembleResult.Prepared -> assembled.bindings.map { it.file }
            is StegoAssembleResult.Failed -> emptyList()
        }
        return when (assembled) {
            is StegoAssembleResult.Failed -> LuksVaultManager.UnlockResult.Failed(assembled.message)
            is StegoAssembleResult.Prepared ->
                LuksVaultManager.UnlockResult.Failed("prepared only; open requires dm or linear image")
            is StegoAssembleResult.DeviceMapper -> {
                val opened = HardwareCryptoAccelerator.pkcsOpen(assembled.mapperPath, cryptName, password)
                if (!opened) {
                    formatCrypt(master, password)
                    HardwareCryptoAccelerator.pkcsOpen(assembled.mapperPath, cryptName, password)
                }
                afterOpen(decoy = false)
            }
            is StegoAssembleResult.LinearImage -> openAssembledLinearImage(assembled.image, password, initialize = false)
        }
    }

    fun lock(syncCloud: Boolean = true) {
        expander?.stop()
        runCatching { com.telos.desktop.session.SessionNetGuard.clear() }
        runCatching { com.telos.desktop.files.DavSecrets.clear() }
        runCatching { com.telos.desktop.files.OAuthSecrets.clear() }
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
        val resolvedPool = if (pool.isDirectory) pool else pool.parentFile ?: pool
        if (PortableTraceGuard.isPortable() || bootMode == BootMode.PortableVault || bootMode == BootMode.PortableData) {
            PortableTraceGuard.assertNoHostWrites(PortableTraceGuard.ensurePortableDataRoot())
        }
        val files = shards.listCarriers(pool).ifEmpty {
            if (pool.isFile) listOf(pool) else {
                val seed = File(resolvedPool.apply { mkdirs() }, "telos-carrier.tvault")
                if (!seed.exists()) seed.writeBytes(ByteArray(0))
                listOf(seed)
            }
        }
        files.forEach { if (!it.exists()) it.writeBytes(ByteArray(0)) }
        val salt = HardwareCryptoAccelerator.hmacSha512(passwordOuter.toByteArray(), "telos-salt".toByteArray()).copyOf(16)
        val outerKey = HybridPqcHeader.deriveVolumeKey(passwordOuter.toCharArray(), salt)
        markDuress(passwordDuress, files)
        val assembled = shards.assembleAndOpen(
            masterKey = outerKey,
            pool = if (pool.isDirectory) pool else resolvedPool,
            perShardBytes = 20L * 1024 * 1024,
            preferDeviceMapper = PlatformEnvironment.settings.showLuksVault,
            linearImageOut = if (PortableTraceGuard.isPortable()) {
                PortableStegoFs.assembledImage(PortableTraceGuard.ensurePortableDataRoot())
            } else {
                null
            },
        )
        return when (assembled) {
            is StegoAssembleResult.Failed -> LuksVaultManager.UnlockResult.Failed(assembled.message)
            is StegoAssembleResult.DeviceMapper -> {
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
                unlock(passwordInner, pool, false)
            }
            is StegoAssembleResult.LinearImage -> {
                carriers = assembled.bindings.map { it.file }
                openAssembledLinearImage(assembled.image, passwordInner, initialize = true)
            }
            is StegoAssembleResult.Prepared -> LuksVaultManager.UnlockResult.Failed("prepared only")
        }
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

    private fun unlockPortableData(): LuksVaultManager.UnlockResult {
        val root = PortableTraceGuard.ensurePortableDataRoot()
        PortableTraceGuard.assertNoHostWrites(root)
        PortableStegoFs.ensureCarriersRoot()
        val session = File(root, "session").apply { mkdirs() }
        luks.bindPortableSessionRoot(session)
        File(session, "config").mkdirs()
        File(session, "state").mkdirs()
        File(session, "db").mkdirs()
        PortableEnvironmentIsolator.apply(session, fullSessionHome = false)
        luks.unlocked = true
        return LuksVaultManager.UnlockResult.Unlocked
    }

    private fun unlockPortableVault(password: String, initialize: Boolean): LuksVaultManager.UnlockResult {
        val root = PortableTraceGuard.ensurePortableDataRoot()
        PortableTraceGuard.assertNoHostWrites(root)
        val session = File(root, "session").apply { mkdirs() }
        luks.bindPortableSessionRoot(session)
        luks.ensureLayout()
        val carriersDir = PortableStegoFs.ensureCarriersRoot()
        val hasCarriers = shards.listCarriers(carriersDir).isNotEmpty() ||
            PortableStegoFs.manifestFile(root).isFile
        if (hasCarriers && password.isNotEmpty()) {
            bootMode = BootMode.PortableVault
            val stego = openStegoCarriers(password, carriersDir)
            if (stego.ok) return stego
        }
        val result = luks.unlock(password, initializeIfMissing = initialize)
        if (result.ok) {
            PortableEnvironmentIsolator.apply(session, fullSessionHome = false)
        }
        return result
    }

    private fun resolveCarrierPool(carrier: File?): File? {
        if (carrier != null) return carrier
        if (PortableTraceGuard.isPortable() ||
            bootMode == BootMode.PortableData ||
            bootMode == BootMode.PortableVault
        ) {
            val portable = PortableStegoFs.carriersRoot()
            if (portable.isDirectory &&
                (shards.listCarriers(portable).isNotEmpty() || PortableStegoFs.manifestFile().isFile)
            ) {
                return portable
            }
        }
        return null
    }

    private fun openAssembledLinearImage(
        image: File,
        password: String,
        initialize: Boolean = false,
    ): LuksVaultManager.UnlockResult {
        if (PortableTraceGuard.isPortable() || bootMode == BootMode.PortableVault) {
            val root = PortableTraceGuard.ensurePortableDataRoot()
            PortableTraceGuard.assertNoHostWrites(root)
            val session = File(root, "session").apply { mkdirs() }
            luks.bindPortableSessionRoot(session)
        }
        val target = luks.vaultImage
        if (!image.exists()) return LuksVaultManager.UnlockResult.Failed("assembled image missing")
        if (image.canonicalFile != target.canonicalFile) {
            target.parentFile?.mkdirs()
            if (!(target.exists() && target.length() == image.length())) {
                image.copyTo(target, overwrite = true)
            }
        }
        if (!PlatformEnvironment.settings.showLuksVault) {
            File(luks.mountPoint, "config").mkdirs()
            File(luks.mountPoint, "state").mkdirs()
            File(luks.mountPoint, "db").mkdirs()
            PortableEnvironmentIsolator.apply(luks.mountPoint, fullSessionHome = false)
            luks.unlocked = true
            return LuksVaultManager.UnlockResult.Unlocked
        }
        if (initialize) {
            val opened = HardwareCryptoAccelerator.pkcsOpen(target.absolutePath, cryptName, password)
            if (!opened) {
                Sys.run(
                    "pkexec", "cryptsetup", "-q", "luksFormat", "--type", "luks2",
                    "--cipher", HardwareCryptoAccelerator.cipherSpec(), "--key-size", "512",
                    "--pbkdf", "argon2id", target.absolutePath,
                    timeoutSec = 60,
                    input = password + "\n" + password + "\n",
                )
                HardwareCryptoAccelerator.pkcsOpen(target.absolutePath, cryptName, password)
            }
            return afterOpen(decoy = false)
        }
        val result = luks.unlock(password, initializeIfMissing = false)
        if (result.ok) {
            PortableEnvironmentIsolator.apply(
                luks.mountPoint,
                fullSessionHome = TelosSessionHome.full && !PortableTraceGuard.isPortable(),
            )
        }
        return result
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
        val candidates = buildList {
            if (PortableTraceGuard.isPortable() ||
                bootMode == BootMode.PortableData ||
                bootMode == BootMode.PortableVault
            ) {
                add(File(PortableTraceGuard.dataRoot(), "state/.duress"))
            }
            add(File(hostHome.toFile(), ".local/share/telos/.duress"))
        }
        val got = HardwareCryptoAccelerator.hmacSha512(master, "TELOS-DURESS".toByteArray())
        return candidates.any { mark ->
            mark.isFile && mark.readBytes().contentEquals(got)
        }
    }

    private fun markDuress(password: String, files: List<File>) {
        val salt = HardwareCryptoAccelerator.hmacSha512(password.toByteArray(), "telos-salt".toByteArray()).copyOf(16)
        val master = HybridPqcHeader.deriveVolumeKey(password.toCharArray(), salt)
        val dir = if (PortableTraceGuard.isPortable() ||
            bootMode == BootMode.PortableData ||
            bootMode == BootMode.PortableVault
        ) {
            File(PortableTraceGuard.ensurePortableDataRoot(), "state").apply { mkdirs() }
        } else {
            File(hostHome.toFile(), ".local/share/telos").apply { mkdirs() }
        }
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
