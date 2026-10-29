package de.mm20.launcher2.ktx

/**
 * Shared, bounded Root/Shizuku command facade delegating directly to [TelosShellEngine]
 * for zero duplication, unified 2-permit concurrency gating, and leak-free stream handling.
 */
object PrivilegedShell {
    data class Result(val exitCode: Int, val output: String, val error: String)

    suspend fun rootAvailable(): Boolean = TelosShellEngine.isRootAvailable()

    suspend fun available(): Boolean = TelosShellEngine.isPrivilegedAvailable()

    suspend fun run(command: String): Result? {
        require(command.length <= 65_536)
        val res = TelosShellEngine.execResult(
            command = command,
            timeoutMs = 5_000L,
            maxOutputBytes = 65_536,
        )
        if (res.timedOut || res.exitCode == -1 && res.stdout.isEmpty() && res.stderr.isEmpty()) {
            return null
        }
        return Result(
            exitCode = res.exitCode,
            output = res.stdout,
            error = res.stderr,
        )
    }
}
