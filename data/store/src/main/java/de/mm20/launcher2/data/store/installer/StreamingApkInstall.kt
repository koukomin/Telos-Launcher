package de.mm20.launcher2.data.store.installer

import java.io.File
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** Stream private APK bytes to the privileged process; no public copy or APK-sized allocation. */
internal suspend fun streamApkInstall(apk: File, process: Process): Pair<Boolean, String> {
    val result = runInstallCommand(apk, process)
    return (result.exitCode == 0 && result.output.lineSequence().any { it.trim() == "Success" }) to result.output
}

internal suspend fun runInstallCommand(apk: File?, process: Process): InstallCommandResult = coroutineScope {
    fun readBounded(input: InputStream): String = input.bufferedReader().use { reader ->
        val text = StringBuilder()
        val buffer = CharArray(2048)
        while (true) {
            val count = reader.read(buffer)
            if (count < 0) break
            if (text.length < 8192) text.append(buffer, 0, minOf(count, 8192 - text.length))
        }
        text.toString()
    }
    val output = async(Dispatchers.IO) { readBounded(process.inputStream) }
    val errors = async(Dispatchers.IO) { readBounded(process.errorStream) }
    try {
        var writeFailed = false
        try {
            process.outputStream.use { target -> apk?.inputStream()?.use { it.copyTo(target, 64 * 1024) } }
        } catch (_: java.io.IOException) {
            // pm can reject an option before consuming stdin; still collect its diagnosis.
            writeFailed = true
        }
        val exit = process.waitFor()
        val stdout = output.await()
        val stderr = errors.await()
        InstallCommandResult(if (writeFailed && exit == 0) -1 else exit, (stdout + stderr).take(8192))
    } finally {
        // Remote Shizuku streams may still contain the result after waitFor returns.
        // Closing the process before readers finish can discard Success or throw Stream closed.
        process.destroy()
    }
}
