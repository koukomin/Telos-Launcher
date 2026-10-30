package com.telos.desktop.session

import com.telos.desktop.vault.PhantomVault
import com.telos.desktop.vault.PhantomVaultEngine
import java.io.Console
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import kotlin.system.exitProcess

// === TELOS_PENDING_REVIEW_START: telos_labwc_session_p0p1 ===
object UnlockCli {
    fun run(fromStdin: Boolean = false) {
        System.setProperty("telos.session", "1")
        val hostHome = System.getProperty("user.home")
        val engine = PhantomVaultEngine().also { PhantomVault.engine = it }
        val console: Console? = System.console()
        val password = when {
            fromStdin -> readlnOrNull().orEmpty()
            console != null -> String(console.readPassword("Telos vault password: ") ?: CharArray(0))
            else -> {
                print("Telos vault password: ")
                readlnOrNull().orEmpty()
            }
        }
        if (password.isEmpty()) {
            System.err.println("telos-session: empty password")
            exitProcess(1)
        }
        val mixed = Fido2Unlock.mix(password)
        val result = engine.unlock(mixed, null, initialize = true)
        password.toCharArray().fill('\u0000')
        mixed.toCharArray().fill('\u0000')
        if (!result.ok) {
            System.err.println(result.toString())
            exitProcess(1)
        }
        if (!SessionNetGuard.apply()) {
            System.err.println("telos-session: nftables inet telos_session not applied (continuing)")
        }
        val home = engine.mountPoint
        val stamp = File(System.getenv("XDG_RUNTIME_DIR") ?: "/tmp", "telos-session.env")
        stamp.writeText(
            buildString {
                appendLine("export TELOS_SESSION=1")
                appendLine("export TELOS_VAULT_UNLOCKED=1")
                appendLine("export TELOS_SESSION_HOME=full")
                appendLine("export TELOS_HOST_HOME=${shQuote(hostHome)}")
                appendLine("export TELOS_VAULT_MOUNT=${shQuote(home.absolutePath)}")
                appendLine("export HOME=${shQuote(home.absolutePath)}")
                appendLine("export XDG_CONFIG_HOME=${shQuote(File(home, ".config").absolutePath)}")
                appendLine("export XDG_DATA_HOME=${shQuote(File(home, ".local/share").absolutePath)}")
                appendLine("export XDG_CACHE_HOME=${shQuote(File(home, ".cache").absolutePath)}")
                appendLine("export XDG_CURRENT_DESKTOP=Telos:labwc")
                appendLine("export XDG_SESSION_TYPE=wayland")
                appendLine("export XDG_SESSION_DESKTOP=Telos")
            },
        )
        runCatching {
            Files.setPosixFilePermissions(
                stamp.toPath(),
                setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
            )
        }
        println(stamp.absolutePath)
        exitProcess(0)
    }

    internal fun shQuote(value: String): String =
        "'" + value.replace("'", "'\\''") + "'"
}

object LockCli {
    fun run() {
        val engine = PhantomVaultEngine().also { PhantomVault.engine = it }
        engine.attachMounted()
        SessionNetGuard.clear()
        engine.lock(syncCloud = true)
        exitProcess(0)
    }
}
// === TELOS_PENDING_REVIEW_END: telos_labwc_session_p0p1 ===
