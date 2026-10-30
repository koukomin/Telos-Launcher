package com.telos.desktop.session

import com.telos.desktop.vault.PhantomVault
import com.telos.desktop.vault.PhantomVaultEngine
import com.telos.desktop.vault.TelosSessionHome
import java.io.Console
import java.io.File
import kotlin.system.exitProcess

// === TELOS_PENDING_REVIEW_START: telos_labwc_session_p0p1 ===
object UnlockCli {
    fun run() {
        System.setProperty("telos.session", "1")
        val engine = PhantomVaultEngine().also { PhantomVault.engine = it }
        val console: Console? = System.console()
        val password = if (console != null) {
            String(console.readPassword("Telos vault password: ") ?: CharArray(0))
        } else {
            print("Telos vault password: ")
            readlnOrNull().orEmpty()
        }
        val mixed = Fido2Unlock.mix(password)
        val result = engine.unlock(mixed, null, initialize = true)
        password.toCharArray().fill('\u0000')
        if (!result.ok) {
            System.err.println(result.toString())
            exitProcess(1)
        }
        SessionNetGuard.apply()
        val home = engine.mountPoint
        val runtime = File("/run/user/" + System.getProperty("user.name").hashCode())
        val stamp = File(System.getenv("XDG_RUNTIME_DIR") ?: "/tmp", "telos-session.env")
        stamp.writeText(
            buildString {
                appendLine("export TELOS_SESSION=1")
                appendLine("export TELOS_VAULT_UNLOCKED=1")
                appendLine("export TELOS_SESSION_HOME=full")
                appendLine("export HOME=${home.absolutePath}")
                appendLine("export XDG_CONFIG_HOME=${home.absolutePath}/.config")
                appendLine("export XDG_DATA_HOME=${home.absolutePath}/.local/share")
                appendLine("export XDG_CACHE_HOME=${home.absolutePath}/.cache")
                appendLine("export XDG_CURRENT_DESKTOP=Telos:labwc")
            },
        )
        println(stamp.absolutePath)
        if (TelosSessionHome.full) {
            // HOME for the following labwc process is the vault mount.
        }
        runtime.mkdirs()
        exitProcess(0)
    }
}
// === TELOS_PENDING_REVIEW_END: telos_labwc_session_p0p1 ===
