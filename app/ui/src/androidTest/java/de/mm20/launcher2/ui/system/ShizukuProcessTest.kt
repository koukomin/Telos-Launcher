package de.mm20.launcher2.ui.system

import de.mm20.launcher2.ktx.ShizukuProcesses
import de.mm20.launcher2.ktx.shizuku.ShizukuCommandService
import moe.shizuku.server.IRemoteProcess
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

/** Production UserService/descriptor code, exercised locally without changing device permissions. */
class ShizukuProcessTest {
    @Test(timeout = 10000) fun stdinEofStdoutStderrAndExitStatusCrossTheProcessBoundary() {
        val service = ShizukuCommandService()
        assertEquals(android.os.Process.myUid(), service.getUid())
        val binder = service.start(arrayOf("sh", "-c", "cat; printf diagnostic >&2; exit 7"), null, null)
        val process = ShizukuProcesses.fromRemoteProcess(IRemoteProcess.Stub.asInterface(binder))
        try {
            process.outputStream.use { it.write("test-payload".toByteArray()) }
            assertEquals("test-payload", process.inputStream.bufferedReader().use { it.readText() })
            assertEquals("diagnostic", process.errorStream.bufferedReader().use { it.readText() })
            assertTrue(process.waitFor(3, TimeUnit.SECONDS))
            assertEquals(7, process.exitValue())
            assertFalse(process.isAlive)
        } finally { process.destroy() }
    }
    @Test(timeout = 10000) fun timeoutAndDestroyDoNotLeaveTheCommandRunning() {
        val service = ShizukuCommandService()
        val binder = service.start(arrayOf("sleep", "10"), null, null)
        val process = ShizukuProcesses.fromRemoteProcess(IRemoteProcess.Stub.asInterface(binder))
        try {
            assertFalse(process.waitFor(25, TimeUnit.MILLISECONDS))
            assertTrue(process.isAlive)
        } finally { process.destroy() }
        assertTrue(process.waitFor(3, TimeUnit.SECONDS))
        assertFalse(process.isAlive)
    }
}
