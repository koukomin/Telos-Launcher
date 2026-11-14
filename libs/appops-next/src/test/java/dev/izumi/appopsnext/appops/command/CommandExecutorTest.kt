package dev.izumi.appopsnext.appops.command

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class CommandExecutorTest {
    @Test
    fun commandsWaitingForInputReceiveEof() {
        val executor = CommandExecutor()
        try {
            val result = executor.execute(listOf("/bin/sh", "-c", "read input; printf done"))
            assertFalse(result.timedOut)
            assertEquals(0, result.exitCode)
            assertEquals("done", result.stdout)
        } finally {
            executor.close()
        }
    }

    @Test
    fun concurrentCommandsDrainBothPipesWithoutStarvingReaders() {
        val executor = CommandExecutor()
        val callers = Executors.newFixedThreadPool(2)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        try {
            val results =
                (1..2).map {
                    callers.submit<dev.izumi.appopsnext.appops.model.ShellCommandResult> {
                        ready.countDown()
                        check(start.await(5, TimeUnit.SECONDS))
                        executor.execute(
                            listOf(
                                "/bin/sh",
                                "-c",
                                "head -c 131072 /dev/zero >&2; printf done",
                            )
                        )
                    }
                }
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            start.countDown()
            results.forEach {
                val result = it.get(15, TimeUnit.SECONDS)
                assertFalse(result.timedOut)
                assertEquals(0, result.exitCode)
                assertEquals("done", result.stdout)
                assertEquals(131072, result.stderr.length)
            }
        } finally {
            executor.close()
            callers.shutdownNow()
        }
    }
}
