/* SPDX-License-Identifier: GPL-3.0-or-later */
package de.mm20.launcher2.appmanagement.startup

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class StartupScriptsTest {
    @Test fun draftsAreNotAutomaticallyEnabled() {
        assertFalse(StartupScript("id", "Name", "id").enabled)
    }
    @Test fun rejectsOversizedAndNulScripts() {
        listOf("a".repeat(16_385), "id\u0000whoami", " ").forEach { body ->
            assertTrue(runCatching { StartupScript("id", "Name", body).validated() }.isFailure)
        }
    }
    @Test fun disarmedOrEditedQueuedScriptsCannotRunAutomatically() {
        val expected = StartupScript("id", "Name", "id", enabled = true)
        assertTrue(expected.mayStartAutomatically(expected))
        assertFalse(expected.copy(enabled = false).mayStartAutomatically(expected))
        assertFalse(expected.copy(body = "whoami").mayStartAutomatically(expected))
        assertFalse(expected.copy(identity = ScriptIdentity.Root).mayStartAutomatically(expected))
    }
    @Test fun unicodeAndEscapedStorageFitsExplicitByteLimit() {
        val scripts = (1..10).map { StartupScript("id-$it", "Δοκιμή", "界".repeat(16_384)) }
        val bytes = encodeStartupScripts(scripts)
        assertTrue(bytes.size > 256 * 1024)
        assertTrue(bytes.size <= 1024 * 1024)
        assertTrue(runCatching { encodeStartupScripts(scripts + scripts.first()) }.isFailure)
    }
    @Test fun preservesExactScriptText() {
        val body = "printf '%s\\n' 'hello'\nexit 7"
        assertEquals(body, StartupScript("id", "Name", body).validated().body)
    }
    @Test fun preservesFailureAndDrainsBothStreams() = runBlocking {
        val process = ProcessBuilder("sh", "-c", "printf output; printf error >&2; exit 7").start()
        val result = ScriptProcessRunner.collect("id", process)
        assertEquals(7, result.exitCode)
        assertEquals("outputerror", result.output)
        assertFalse(result.timedOut)
    }
    @Test fun capsUnboundedOutputWithoutDeadlocking() = runBlocking {
        val process = ProcessBuilder("sh", "-c", "yes output | head -c 90000; yes error | head -c 90000 >&2").start()
        val result = ScriptProcessRunner.collect("id", process)
        assertEquals(0, result.exitCode)
        assertEquals(16_384, result.output.length)
    }
    @Test fun inheritedPipeDoesNotWaitForBackgroundChild() = runBlocking {
        val process = ProcessBuilder("sh", "-c", "sleep 1 & printf complete").start()
        val before = System.nanoTime()
        val result = ScriptProcessRunner.collect("id", process, timeoutMs = 500)
        assertEquals(0, result.exitCode)
        assertEquals("complete", result.output)
        assertTrue((System.nanoTime() - before) / 1_000_000 < 900)
    }
    @Test fun collectsDelayedBinderPipeOutputAfterRemoteExit() = runBlocking {
        val before = System.nanoTime()
        val bytes = "late output".toByteArray()
        var offset = 0
        val delayed = object : java.io.InputStream() {
            override fun available(): Int = if ((System.nanoTime() - before) / 1_000_000 >= 100) bytes.size - offset else 0
            override fun read(): Int = if (offset < bytes.size) bytes[offset++].toInt() and 255 else -1
        }
        val process = object : Process() {
            override fun getInputStream() = delayed
            override fun getErrorStream() = java.io.ByteArrayInputStream(byteArrayOf())
            override fun getOutputStream() = java.io.ByteArrayOutputStream()
            override fun waitFor() = 0
            override fun exitValue() = 0
            override fun destroy() {}
        }
        assertEquals("late output", ScriptProcessRunner.collect("id", process).output)
    }
    @Test fun destroysTimedOutProcess() = runBlocking {
        val process = ProcessBuilder("sh", "-c", "exec sleep 30").start()
        val result = ScriptProcessRunner.collect("id", process, timeoutMs = 100)
        assertTrue(result.timedOut)
        assertTrue(process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS))
    }
}
