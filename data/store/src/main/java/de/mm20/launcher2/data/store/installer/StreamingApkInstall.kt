package de.mm20.launcher2.data.store.installer

import java.io.File
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** Stream private APK bytes to the privileged process; no public copy or APK-sized allocation. */
internal suspend fun streamApkInstall(apk: File, process: Process): Pair<Boolean, String> = coroutineScope {
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
    var writeFailed = false
    val exit = try {
        try {
            process.outputStream.use { target -> apk.inputStream().use { it.copyTo(target, 64 * 1024) } }
        } catch (_: java.io.IOException) {
            // An unsupported option may make pm close stdin before consuming the APK.
            writeFailed = true
        }
        process.waitFor()
    } finally { process.destroy() }
    val stdout = output.await()
    val stderr = errors.await()
    (!writeFailed && exit == 0 && stdout.lineSequence().any { it.trim() == "Success" }) to (stdout + stderr).take(8192)
}
