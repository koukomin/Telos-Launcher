package dev.izumi.appopsnext.appops.command

import dev.izumi.appopsnext.appops.model.ShellCommandResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ReadWithFallbackTest {
    private val success = ShellCommandResult(0, "CAMERA: allow", "", false)

    @Test
    fun successfulReadDoesNotRunAnotherCommand() = runBlocking {
        val result = readWithFallback({ success }, { error("Unexpected retry") })
        assertSame(success, result)
    }

    @Test
    fun userServiceFailureRetriesAndReturnsRealShellResult() = runBlocking {
        for (failure in
            listOf(
                ShellCommandResult(1, "", "Denied", false),
                ShellCommandResult(-1, "", "", true),
            )) {
            var retries = 0
            val result =
                readWithFallback(
                    { failure },
                    {
                        retries++
                        success
                    },
                )
            assertEquals(1, retries)
            assertSame(success, result)
        }
    }

    @Test
    fun binderExceptionRetriesButCancellationDoesNot() = runBlocking {
        assertSame(
            success,
            readWithFallback({ throw IllegalStateException("Binder died") }, { success }),
        )
        try {
            readWithFallback({ throw CancellationException() }, { error("Unexpected retry") })
            fail("Cancellation must propagate")
        } catch (_: CancellationException) {}
    }

    @Test
    fun bothTransportsFailWithoutReportingSuccess() = runBlocking {
        val failure = ShellCommandResult(1, "", "Permission denied", false)
        assertSame(failure, readWithFallback({ failure }, { failure }))
    }
}
