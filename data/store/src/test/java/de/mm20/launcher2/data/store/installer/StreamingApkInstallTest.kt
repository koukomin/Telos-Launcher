package de.mm20.launcher2.data.store.installer

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class StreamingApkInstallTest {
    private class FakeProcess(val code: Int = 0, text: String = "Success\n", val errorBytes: Int = 0) : Process() {
        var received = 0L
        var closed = false
        var destroyed = false
        var drained = 0
        private val sink = object : OutputStream() {
            override fun write(b: Int) { received++ }
            override fun write(b: ByteArray, off: Int, len: Int) { received += len }
            override fun close() { closed = true }
        }
        private val stdout = ByteArrayInputStream(text.toByteArray())
        private val stderr = object : InputStream() {
            override fun read(): Int = if (drained++ < errorBytes) 'x'.code else -1
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                val count = minOf(length, errorBytes - drained)
                if (count <= 0) return -1
                buffer.fill('x'.code.toByte(), offset, offset + count); drained += count
                return count
            }
        }
        override fun getOutputStream() = sink
        override fun getInputStream() = stdout
        override fun getErrorStream() = stderr
        override fun waitFor(): Int { check(closed); return code }
        override fun exitValue() = code
        override fun destroy() { destroyed = true }
    }
    @Test fun largePrivateApkIsStreamedWithinConstrainedHeap() = runBlocking {
        val apk = Files.createTempFile("telos-apk", ".apk").toFile()
        try {
            RandomAccessFile(apk, "rw").use { it.setLength(170L * 1024 * 1024) }
            val process = FakeProcess()
            assertTrue(streamApkInstall(apk, process).first)
            assertEquals(apk.length(), process.received)
            assertTrue(process.closed); assertTrue(process.destroyed)
        } finally { apk.delete() }
    }
    @Test fun zeroExitWithoutSuccessIsNotAccepted() = runBlocking {
        val apk = Files.createTempFile("telos-apk", ".apk").toFile()
        try { assertFalse(streamApkInstall(apk, FakeProcess(text = "Failure [DENIED]")).first) }
        finally { apk.delete() }
    }
    @Test fun successTextWithFailureExitIsNotAccepted() = runBlocking {
        val apk = Files.createTempFile("telos-apk", ".apk").toFile()
        try { assertFalse(streamApkInstall(apk, FakeProcess(code = 1)).first) }
        finally { apk.delete() }
    }
    @Test fun diagnosticsAreBoundedButBothPipesAreDrained() = runBlocking {
        val apk = Files.createTempFile("telos-apk", ".apk").toFile()
        try {
            val process = FakeProcess(errorBytes = 2 * 1024 * 1024)
            val result = streamApkInstall(apk, process)
            assertTrue(result.first); assertTrue(result.second.length <= 8192)
            assertEquals(process.errorBytes, process.drained)
        } finally { apk.delete() }
    }
}
