package com.telos.desktop.quake

import com.telos.desktop.linux.Sys
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CopyOnWriteArrayList

class PtySession(
    val cwd: File = File(System.getProperty("user.home")),
    val shell: String = listOf("/bin/zsh", "/bin/bash", "/bin/sh").first { File(it).canExecute() },
) {
    private var process: Process? = null
    val listeners = CopyOnWriteArrayList<(ByteArray) -> Unit>()
    val history = ArrayList<String>()
    var historyIndex = -1
    var live = false
        private set

    fun start() {
        if (live) return
        val env = HashMap(System.getenv())
        env["TERM"] = "xterm-256color"
        env["COLORTERM"] = "truecolor"
        val cmd = when {
            Sys.which("script") != null -> listOf("script", "-qefc", shell, "/dev/null")
            else -> listOf(shell, "-i")
        }
        val pb = ProcessBuilder(cmd)
        pb.directory(cwd)
        pb.environment().putAll(env)
        // === TELOS_PENDING_REVIEW_START: telos_elastic_sharded_pqc_vault ===
        com.telos.desktop.vault.PortableEnvironmentIsolator.inject(pb)
        // === TELOS_PENDING_REVIEW_END: telos_elastic_sharded_pqc_vault ===
        pb.redirectErrorStream(true)
        process = pb.start()
        live = true
        Thread({
            val buf = ByteArray(4096)
            val ins: InputStream = process?.inputStream ?: return@Thread
            while (live) {
                val n = ins.read(buf)
                if (n < 0) break
                val copy = buf.copyOf(n)
                listeners.forEach { it(copy) }
            }
            live = false
        }, "telos-pty").apply { isDaemon = true; start() }
    }

    fun write(bytes: ByteArray) {
        val os: OutputStream = process?.outputStream ?: return
        os.write(bytes)
        os.flush()
    }

    fun write(text: String) = write(text.toByteArray())

    fun sigint() {
        process?.pid()?.let { Sys.run("kill", "-INT", it.toString()) }
        write("\u0003")
    }

    fun destroy() {
        live = false
        process?.destroy()
        process = null
    }
}
