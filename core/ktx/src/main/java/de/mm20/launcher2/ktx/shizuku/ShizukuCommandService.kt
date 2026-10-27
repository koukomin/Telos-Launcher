package de.mm20.launcher2.ktx.shizuku

import android.os.IBinder
import android.os.ParcelFileDescriptor
import moe.shizuku.server.IRemoteProcess
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Loaded by Shizuku in its authorized UserService process, never as an Android foreground service. */
class ShizukuCommandService : ICommandService.Stub() {
    private val processes = ConcurrentHashMap.newKeySet<Process>()
    override fun getUid(): Int = android.os.Process.myUid()
    @Synchronized override fun start(command: Array<out String>, environment: Array<out String>?, directory: String?): IBinder {
        require(command.isNotEmpty() && command.size <= 128 && command.sumOf { it.length } <= 65536)
        processes.removeAll { !it.isAlive }
        check(processes.size < 8) { "Too many active privileged processes" }
        val process = Runtime.getRuntime().exec(command, environment, directory?.let { java.io.File(it) })
        processes.add(process)
        return OwnedProcess(process) { processes.remove(process) }
    }
    override fun destroy() {
        processes.forEach { runCatching { it.destroyForcibly() } }
        kotlin.system.exitProcess(0)
    }
}

/** Pipe descriptors cross Binder; Java Process objects never cross a class loader or IPC boundary. */
private class OwnedProcess(private val process: Process, private val onExit: () -> Unit) : IRemoteProcess.Stub() {
    private val stdout = ParcelFileDescriptor.createPipe()
    private val stderr = ParcelFileDescriptor.createPipe()
    private val stdin = ParcelFileDescriptor.createPipe()
    init {
        copy(process.inputStream, ParcelFileDescriptor.AutoCloseOutputStream(stdout[1]))
        copy(process.errorStream, ParcelFileDescriptor.AutoCloseOutputStream(stderr[1]))
        copy(ParcelFileDescriptor.AutoCloseInputStream(stdin[0]), process.outputStream)
    }
    private fun copy(input: java.io.InputStream, output: java.io.OutputStream) {
        Thread({ runCatching { input.use { source -> output.use { target -> source.copyTo(target, 8192) } } } },
            "TelosCommandPipe").apply { isDaemon = true; start() }
    }
    // Binder duplicates the returned descriptors. Keep ownership here until destroy().
    override fun getInputStream(): ParcelFileDescriptor = stdout[0]
    override fun getErrorStream(): ParcelFileDescriptor = stderr[0]
    override fun getOutputStream(): ParcelFileDescriptor = stdin[1]
    override fun waitFor(): Int = process.waitFor().also { onExit() }
    override fun waitForTimeout(timeout: Long, unit: String): Boolean =
        process.waitFor(timeout, TimeUnit.valueOf(unit)).also { if (it) onExit() }
    override fun exitValue(): Int = process.exitValue().also { onExit() }
    override fun alive(): Boolean = process.isAlive
    override fun destroy() {
        process.destroy()
        runCatching { stdout[0].close() }; runCatching { stderr[0].close() }; runCatching { stdin[1].close() }
        runCatching { process.inputStream.close() }; runCatching { process.errorStream.close() }; runCatching { process.outputStream.close() }
        onExit()
    }
}
