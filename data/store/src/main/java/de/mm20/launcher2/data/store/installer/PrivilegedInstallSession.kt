package de.mm20.launcher2.data.store.installer

import java.io.File

internal data class InstallCommandResult(val exitCode: Int, val output: String)

/** Explicit sessions keep command failures separate from APK streaming and final commit. */
internal suspend fun installPrivilegedSession(
    apk: File,
    userId: Int,
    preserveEnabled: Boolean = false,
    allowDowngrade: Boolean = false,
    run: suspend (List<String>, File?) -> InstallCommandResult,
): BackendInstallResult {
    require(userId >= 0 && apk.length() > 0)
    val args = mutableListOf("pm", "install-create", "-r", "-d", "--user", userId.toString(), "-S", apk.length().toString())
    var created = run(args + if (preserveEnabled) listOf("--skip-enable") else emptyList(), null)
    if (created.exitCode != 0 && preserveEnabled && unsupportedPreserveFlag(created.output)) created = run(args, null)
    val session = if (created.exitCode == 0) Regex("""\[(\d+)]""").find(created.output)?.groupValues?.get(1) else null
    if (session == null) return BackendInstallResult.Failed("install-create: ${created.output}")
    var committed = false
    try {
        val written = run(listOf("pm", "install-write", "-S", apk.length().toString(), session, "base.apk", "-"), apk)
        if (written.exitCode != 0 || !written.output.trimStart().startsWith("Success"))
            return BackendInstallResult.Failed("install-write: ${written.output}")
        val result = run(listOf("pm", "install-commit", session), null)
        committed = result.exitCode == 0 && result.output.lineSequence().any { it.trim() == "Success" }
        return if (committed) BackendInstallResult.Success else BackendInstallResult.Failed("install-commit: ${result.output}")
    } finally {
        if (!committed) kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
            runCatching { run(listOf("pm", "install-abandon", session), null) }
        }
    }
}
