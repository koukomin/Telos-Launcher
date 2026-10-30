package com.telos.desktop.session

import com.telos.desktop.linux.Sys
import java.io.File

object PamAuth {
    fun authenticate(password: String): Boolean {
        if (password.isEmpty()) return false
        val script = scriptFile() ?: return false
        val result = Sys.run(script.absolutePath, timeoutSec = 8, input = password + "\n")
        return result.ok
    }

    internal fun scriptFile(): File? {
        val env = System.getenv("TELOS_PAM_AUTH")
        if (!env.isNullOrBlank()) {
            val f = File(env)
            if (f.isFile && f.canExecute()) return f
        }
        val runtime = System.getenv("XDG_RUNTIME_DIR")
        val candidates = listOfNotNull(
            runtime?.let { File(it, "telos-labwc/telos-pam-auth") },
            File("/opt/telos-desktop/session/telos-pam-auth"),
            System.getenv("TELOS_JAR")?.let { File(File(it).parentFile, "session/telos-pam-auth") },
        )
        return candidates.firstOrNull { it.isFile && it.canExecute() }
    }
}
