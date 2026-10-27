package dev.izumi.appopsnext.shizuku.process

import java.util.concurrent.TimeUnit

internal class RemoteProcessHandle(private val process: Process) {
    val stdin get() = process.outputStream
    val stdout get() = process.inputStream
    val stderr get() = process.errorStream
    fun waitFor(timeout: Long, unit: TimeUnit) = process.waitFor(timeout, unit)
    fun exitValue() = process.exitValue()
    fun isAlive() = process.isAlive
    fun destroy() = process.destroy()
}
