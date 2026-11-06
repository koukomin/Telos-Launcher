/* SPDX-License-Identifier: GPL-3.0-or-later */
package de.mm20.launcher2.appmanagement.startup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Drain both pipes without waiting for EOF: a detached child must not strand launcher work. */
internal object ScriptProcessRunner {
    suspend fun collect(id: String, process: Process, timeoutMs: Long = 35_000): ScriptRun = withContext(Dispatchers.IO) {
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        val deadline = System.nanoTime() + timeoutMs * 1_000_000L
        var observedExit: Int? = null
        var exitAt = 0L
        try {
            process.outputStream.close()
            while (true) {
                drain(process.inputStream, stdout, buffer)
                drain(process.errorStream, stderr, buffer)
                if (observedExit == null) {
                    observedExit = runCatching { process.exitValue() }.getOrNull()
                    if (observedExit != null) exitAt = System.nanoTime()
                }
                // Binder process streams are pumped asynchronously after the remote child exits.
                // Allow a bounded settling window, never an EOF wait on inherited pipes.
                val exit = observedExit
                if (exit != null && System.nanoTime() - exitAt >= 250_000_000L) {
                    return@withContext ScriptRun(id, exit, stdout.toString("UTF-8") + stderr.toString("UTF-8"), exit == 124 || exit == 137)
                }
                if (System.nanoTime() >= deadline) {
                    process.destroy()
                    return@withContext ScriptRun(id, -1, stdout.toString("UTF-8") + stderr.toString("UTF-8"), true)
                }
                delay(20)
            }
            @Suppress("UNREACHABLE_CODE")
            error("unreachable")
        } finally {
            runCatching { process.destroy() }
            runCatching { process.inputStream.close() }
            runCatching { process.errorStream.close() }
        }
    }

    private fun drain(input: InputStream, output: ByteArrayOutputStream, buffer: ByteArray) {
        // Per-tick bound also handles a command producing an endless stream of output.
        repeat(32) {
            val available = input.available()
            if (available <= 0) return
            val read = input.read(buffer, 0, minOf(available, buffer.size))
            if (read <= 0) return
            val left = 8192 - output.size()
            if (left > 0) output.write(buffer, 0, minOf(left, read))
        }
    }
}
