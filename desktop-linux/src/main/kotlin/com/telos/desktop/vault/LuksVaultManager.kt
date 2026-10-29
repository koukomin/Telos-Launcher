package com.telos.desktop.vault

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.Path
import kotlin.io.path.exists

/**
 * Unlocks and mounts `telos_vault.img` with cryptsetup / udisksctl / pkexec
 * before any desktop state is written. All Telos Linux configs live under
 * the mounted vault path.
 */
class LuksVaultManager(
    private val home: Path = Path(System.getProperty("user.home")),
) {
    val vaultImage: File = home.resolve(".local/share/telos/telos_vault.img").toFile()
    val mountPoint: File = home.resolve(".local/share/telos/vault_mnt").toFile()
    val mapperName: String = "telos_vault"

    val configDir: File get() = File(mountPoint, "config")
    val stateDir: File get() = File(mountPoint, "state")
    val databaseDir: File get() = File(mountPoint, "db")

    @Volatile
    var unlocked: Boolean = false
        private set

    private var shutdownHookRegistered = false

    fun ensureLayout() {
        vaultImage.parentFile.mkdirs()
        mountPoint.mkdirs()
    }

    fun unlock(masterPassword: String, initializeIfMissing: Boolean = true): UnlockResult {
        ensureLayout()
        if (unlocked && isMounted()) {
            ensureVaultSubdirs()
            return UnlockResult.AlreadyUnlocked
        }
        if (!vaultImage.exists()) {
            if (!initializeIfMissing) return UnlockResult.MissingImage
            val created = initializeImage(masterPassword)
            if (!created.ok) return created
        }
        val unlockedMapper = unlockMapper(masterPassword)
        if (!unlockedMapper.ok) return unlockedMapper
        val mounted = mountMapper()
        if (!mounted.ok) {
            lockMapper()
            return mounted
        }
        ensureVaultSubdirs()
        unlocked = true
        registerShutdownHook()
        return UnlockResult.Unlocked
    }

    fun lock() {
        if (!unlocked && !isMounted()) return
        runCatching { run(listOf("sync"), timeoutSec = 30) }
        unmount()
        lockMapper()
        unlocked = false
    }

    private fun ensureVaultSubdirs() {
        configDir.mkdirs()
        stateDir.mkdirs()
        databaseDir.mkdirs()
    }

    private fun registerShutdownHook() {
        if (shutdownHookRegistered) return
        shutdownHookRegistered = true
        Runtime.getRuntime().addShutdownHook(Thread {
            try {
                lock()
            } catch (_: Throwable) {
            }
        })
    }

    private fun initializeImage(password: String): UnlockResult {
        vaultImage.parentFile.mkdirs()
        val dd = run(
            listOf("dd", "if=/dev/zero", "of=${vaultImage.absolutePath}", "bs=1M", "count=64", "status=none"),
            timeoutSec = 120,
        )
        if (dd.exit != 0) return UnlockResult.Failed("Could not create vault image: ${dd.stderr}")
        val format = runWithPassword(
            listOf("cryptsetup", "-q", "luksFormat", vaultImage.absolutePath),
            password,
        )
        if (format.exit != 0) {
            val pkexec = runWithPassword(
                listOf("pkexec", "cryptsetup", "-q", "luksFormat", vaultImage.absolutePath),
                password,
            )
            if (pkexec.exit != 0) return UnlockResult.Failed("LUKS format failed: ${pkexec.stderr.ifBlank { format.stderr }}")
        }
        return UnlockResult.Initialized
    }

    private fun unlockMapper(password: String): UnlockResult {
        val open = runWithPassword(
            listOf("cryptsetup", "open", vaultImage.absolutePath, mapperName),
            password,
        )
        if (open.exit == 0) return UnlockResult.Unlocked
        val pkexec = runWithPassword(
            listOf("pkexec", "cryptsetup", "open", vaultImage.absolutePath, mapperName),
            password,
        )
        if (pkexec.exit == 0) return UnlockResult.Unlocked
        val udisks = runWithPassword(
            listOf("udisksctl", "unlock", "-b", vaultImage.absolutePath),
            password,
        )
        if (udisks.exit == 0) return UnlockResult.Unlocked
        return UnlockResult.Failed("Unlock failed: ${open.stderr.ifBlank { pkexec.stderr.ifBlank { udisks.stderr } }}")
    }

    private fun mountMapper(): UnlockResult {
        val device = File("/dev/mapper/$mapperName")
        if (device.exists()) {
            maybeMakeFs(device)
            val mount = run(listOf("mount", device.absolutePath, mountPoint.absolutePath))
            if (mount.exit == 0) return UnlockResult.Unlocked
            val pkexec = run(listOf("pkexec", "mount", device.absolutePath, mountPoint.absolutePath))
            if (pkexec.exit == 0) return UnlockResult.Unlocked
            return UnlockResult.Failed("Mount failed: ${mount.stderr}")
        }
        val udisks = run(listOf("udisksctl", "mount", "-b", vaultImage.absolutePath))
        if (udisks.exit == 0) return UnlockResult.Unlocked
        return UnlockResult.Failed("No mapper device at ${device.absolutePath}")
    }

    private fun maybeMakeFs(device: File) {
        val blkid = run(listOf("blkid", "-o", "value", "-s", "TYPE", device.absolutePath))
        if (blkid.stdout.trim().isEmpty()) {
            run(listOf("mkfs.ext4", "-F", device.absolutePath), timeoutSec = 60)
        }
    }

    private fun unmount() {
        run(listOf("umount", mountPoint.absolutePath))
        run(listOf("pkexec", "umount", mountPoint.absolutePath))
        run(listOf("udisksctl", "unmount", "-b", "/dev/mapper/$mapperName"))
    }

    private fun lockMapper() {
        run(listOf("cryptsetup", "close", mapperName))
        run(listOf("pkexec", "cryptsetup", "close", mapperName))
        run(listOf("udisksctl", "lock", "-b", vaultImage.absolutePath))
    }

    private fun isMounted(): Boolean {
        val mounts = runCatching { Files.readString(Path("/proc/self/mounts")) }.getOrDefault("")
        return mounts.contains(mountPoint.absolutePath)
    }

    private fun run(cmd: List<String>, timeoutSec: Long = 20): Proc {
        return try {
            val pb = ProcessBuilder(cmd)
            pb.redirectErrorStream(false)
            val p = pb.start()
            val stdout = p.inputStream.bufferedReader().readText()
            val stderr = p.errorStream.bufferedReader().readText()
            p.waitFor(timeoutSec, TimeUnit.SECONDS)
            Proc(p.exitValue(), stdout, stderr)
        } catch (e: Exception) {
            Proc(-1, "", e.message ?: "exec failed")
        }
    }

    private fun runWithPassword(cmd: List<String>, password: String, timeoutSec: Long = 40): Proc {
        return try {
            val pb = ProcessBuilder(cmd)
            val p = pb.start()
            p.outputStream.bufferedWriter().use { it.write(password); it.write("\n"); it.flush() }
            val stdout = p.inputStream.bufferedReader().readText()
            val stderr = p.errorStream.bufferedReader().readText()
            p.waitFor(timeoutSec, TimeUnit.SECONDS)
            Proc(p.exitValue(), stdout, stderr)
        } catch (e: Exception) {
            Proc(-1, "", e.message ?: "exec failed")
        }
    }

    data class Proc(val exit: Int, val stdout: String, val stderr: String)

    sealed class UnlockResult(val ok: Boolean) {
        data object Unlocked : UnlockResult(true)
        data object AlreadyUnlocked : UnlockResult(true)
        data object Initialized : UnlockResult(true)
        data object MissingImage : UnlockResult(false)
        data class Failed(val message: String) : UnlockResult(false)
    }
}
