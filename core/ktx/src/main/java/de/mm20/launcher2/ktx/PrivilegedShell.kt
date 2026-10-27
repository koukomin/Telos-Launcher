package de.mm20.launcher2.ktx

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Shared, bounded Root/Shizuku command path. A successful probe must actually execute as a privileged UID. */
object PrivilegedShell {
    data class Result(val exitCode: Int, val output: String, val error: String)
    suspend fun rootAvailable(): Boolean = withContext(Dispatchers.IO) {
        runCatching { collect(ProcessBuilder("su", "-c", "id").start())?.let { it.exitCode == 0 && it.output.contains("uid=0") } == true }.getOrDefault(false)
    }
    suspend fun available(): Boolean = ShizukuProcesses.available() || rootAvailable()
    suspend fun run(command: String): Result? = withContext(Dispatchers.IO) {
        require(command.length <= 65536)
        val shizuku = if (ShizukuProcesses.available())
            runCatching { collect(ShizukuProcesses.start(arrayOf("sh", "-c", command))) }.getOrNull() else null
        if (shizuku?.exitCode == 0) return@withContext shizuku
        val root = runCatching { collect(ProcessBuilder("su", "-c", command).start()) }.getOrNull()
        root?.takeIf { it.exitCode == 0 } ?: shizuku ?: root
    }
    private fun collect(process: Process): Result? {
        val executor = Executors.newFixedThreadPool(2) { task -> Thread(task, "TelosPrivilegedPipe").apply { isDaemon = true } }
        fun read(stream: InputStream): String = stream.use {
            val bytes = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            while (true) {
                val n = it.read(buffer)
                if (n < 0) break
                val remaining = 65536 - bytes.size()
                if (remaining > 0) bytes.write(buffer, 0, minOf(n, remaining))
            }
            bytes.toString(Charsets.UTF_8.name())
        }
        return try {
            process.outputStream.close()
            val output = executor.submit<String> { read(process.inputStream) }
            val error = executor.submit<String> { read(process.errorStream) }
            if (!process.waitFor(5, TimeUnit.SECONDS)) null else Result(process.exitValue(), output.get(1, TimeUnit.SECONDS), error.get(1, TimeUnit.SECONDS))
        } finally {
            process.destroy()
            runCatching { process.inputStream.close() }; runCatching { process.errorStream.close() }
            executor.shutdownNow()
        }
    }
}
