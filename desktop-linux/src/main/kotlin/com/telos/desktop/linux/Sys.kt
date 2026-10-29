package com.telos.desktop.linux

import java.io.File
import java.util.concurrent.TimeUnit

object Sys {
    fun which(name: String): String? {
        val path = System.getenv("PATH") ?: return null
        return path.split(':').map { File(it, name) }.firstOrNull { it.isFile && it.canExecute() }?.absolutePath
    }

    fun run(vararg args: String, timeoutSec: Long = 4, input: String? = null): Result {
        return try {
            val pb = ProcessBuilder(*args)
            pb.redirectErrorStream(true)
            val p = pb.start()
            if (input != null) p.outputStream.bufferedWriter().use { it.write(input); it.flush() }
            val out = p.inputStream.bufferedReader().readText()
            p.waitFor(timeoutSec, TimeUnit.SECONDS)
            Result(if (p.isAlive) { p.destroyForcibly(); -1 } else p.exitValue(), out)
        } catch (e: Exception) {
            Result(-1, e.message ?: "exec failed")
        }
    }

    fun runBg(vararg args: String): Boolean = try {
        ProcessBuilder(*args).redirectErrorStream(true).start()
        true
    } catch (_: Exception) {
        false
    }

    fun readText(path: String): String? = runCatching { File(path).takeIf { it.isFile }?.readText()?.trim() }.getOrNull()

    fun readLong(path: String): Long? = readText(path)?.toLongOrNull()

    data class Result(val exit: Int, val stdout: String) {
        val ok get() = exit == 0
    }
}
