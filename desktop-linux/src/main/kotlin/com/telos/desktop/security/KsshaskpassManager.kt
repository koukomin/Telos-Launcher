package com.telos.desktop.security

import java.io.File

/**
 * Replicates the behavior of KDE's ksshaskpass / kwallet.
 * When SSH, Git, or GPG requires a password prompt via SSH_ASKPASS in a GUI environment,
 * Telos intercepts the request and prompts the user using a Compose GUI dialog instead of terminal input,
 * utilizing the secure LuksVaultManager to read/store passphrases.
 */
class KsshaskpassManager {

    fun setupEnvironmentVariables() {
        // Expose a custom shell script that acts as SSH_ASKPASS.
        // In a real implementation, this script would communicate via IPC/sockets back to the Telos JVM,
        // which would then spawn an AuthScreen dialog.

        val askPassScript = File(System.getProperty("java.io.tmpdir"), "telos-askpass.sh")
        if (!askPassScript.exists()) {
            askPassScript.writeText(
                """
                #!/bin/sh
                # Telos Desktop SSH_ASKPASS Interceptor
                echo "Requesting GUI password prompt for: ${'$'}1" >&2
                # Send IPC signal to Telos Desktop JVM (placeholder for actual socket ping)
                # Read response from local secure vault pipe
                echo "dummy_password"
                """.trimIndent()
            )
            askPassScript.setExecutable(true)
        }

        // Export SSH_ASKPASS globally for child processes launched from Telos
        // Note: Java cannot modify the parent environment, so apps launched by DesktopFileLauncher
        // should inherit this environment variable explicitly.
    }

    /**
     * Injects SSH_ASKPASS environment variables into a ProcessBuilder context.
     */
    fun injectAskPassEnv(builder: ProcessBuilder) {
        val askPassScript = File(System.getProperty("java.io.tmpdir"), "telos-askpass.sh")
        builder.environment()["SSH_ASKPASS"] = askPassScript.absolutePath
        builder.environment()["SSH_ASKPASS_REQUIRE"] = "force" // Force GUI prompt
    }
}
