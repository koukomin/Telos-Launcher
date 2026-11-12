package com.telos.desktop.linux

import java.io.File
import java.util.concurrent.TimeUnit

object Sys {
    fun which(name: String): String? {
        val path = System.getenv("PATH") ?: return null
        val sep = File.pathSeparatorChar
        val candidates = path.split(sep).flatMap { dir ->
            val base = File(dir, name)
            // Windows package managers often ship as .exe / .cmd shims.
            listOf(base, File(dir, "$name.exe"), File(dir, "$name.cmd"), File(dir, "$name.bat"))
        }
        return candidates.firstOrNull { it.isFile && (it.canExecute() || it.extension.equals("cmd", true) || it.extension.equals("bat", true) || it.extension.equals("exe", true)) }
            ?.absolutePath
    }

    fun run(vararg args: String, timeoutSec: Long = 4, input: String? = null, dir: File? = null): Result {
        return try {
            val pb = ProcessBuilder(*args)
            if (dir != null) pb.directory(dir)
            pb.redirectErrorStream(true)
            val p = pb.start()
            p.outputStream.use { stream ->
                if (input != null) {
                    stream.write(input.toByteArray())
                    stream.flush()
                }
            }
            val out = p.inputStream.bufferedReader().use { it.readText() }
            if (!p.waitFor(timeoutSec, TimeUnit.SECONDS)) {
                p.destroyForcibly()
                p.waitFor(2, TimeUnit.SECONDS)
                return Result(-1, out)
            }
            Result(p.exitValue(), out)
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
