package dev.izumi.appopsnext.appops.command

import dev.izumi.appopsnext.appops.model.ShellCommandResult
import kotlinx.coroutines.CancellationException

/** Retry reads only; callers must never pass a state-changing command. */
internal suspend fun readWithFallback(
    primary: suspend () -> ShellCommandResult?,
    fallback: suspend () -> ShellCommandResult,
    onFailure: (ShellCommandResult?, Exception?) -> Unit = { _, _ -> },
): ShellCommandResult {
    val result =
        try {
            primary()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            onFailure(null, error)
            return fallback()
        }
    if (result != null && result.exitCode == 0 && !result.timedOut) return result
    if (result != null) onFailure(result, null)
    return fallback()
}
