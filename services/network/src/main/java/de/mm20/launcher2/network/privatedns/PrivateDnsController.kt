package de.mm20.launcher2.network.privatedns

import android.content.pm.PackageManager
import android.util.Log
import de.mm20.launcher2.network.api.PrivateDnsMode
import de.mm20.launcher2.network.api.PrivateDnsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.util.concurrent.TimeUnit

enum class PrivateDnsError {
    SHIZUKU_UNAVAILABLE,
    PERMISSION_REQUIRED,
    INVALID_HOSTNAME,
    COMMAND_FAILED,
    STATE_NOT_APPLIED,
}

sealed interface PrivateDnsResult {
    data class Success(val state: PrivateDnsState) : PrivateDnsResult
    data class Failure(val reason: PrivateDnsError) : PrivateDnsResult
}

/**
 * Controls Android's system Private DNS separately from Telos Network's VPN DNS.
 *
 * This is always an explicit user action or a caller applying a saved Wi-Fi rule; it never
 * changes the device DNS merely because a profile was connected in the NextDNS editor.
 */
class PrivateDnsController internal constructor(
    private val commandRunner: PrivateDnsCommandRunner = ShizukuPrivateDnsCommandRunner,
    private val access: PrivateDnsAccess = ShizukuPrivateDnsAccess,
) {
    suspend fun observe(): PrivateDnsResult = withContext(Dispatchers.IO) {
        if (!access.hasPermission()) return@withContext permissionFailure()
        val mode = command("settings", "get", "global", MODE_KEY)
            ?: return@withContext PrivateDnsResult.Failure(PrivateDnsError.COMMAND_FAILED)
        val state = when (mode.trim()) {
            "hostname" -> {
                val hostname = command("settings", "get", "global", HOSTNAME_KEY)
                    ?.trim()
                    ?.takeUnless { it.isEmpty() || it == "null" }
                    ?: return@withContext PrivateDnsResult.Failure(PrivateDnsError.STATE_NOT_APPLIED)
                PrivateDnsHost.normalize(hostname)?.let { PrivateDnsState(PrivateDnsMode.PROVIDER, it) }
                    ?: return@withContext PrivateDnsResult.Failure(PrivateDnsError.STATE_NOT_APPLIED)
            }
            "opportunistic" -> PrivateDnsState(PrivateDnsMode.AUTOMATIC)
            "off", "null", "" -> PrivateDnsState(PrivateDnsMode.OFF)
            else -> return@withContext PrivateDnsResult.Failure(PrivateDnsError.STATE_NOT_APPLIED)
        }
        PrivateDnsResult.Success(state)
    }

    suspend fun setProvider(hostname: String): PrivateDnsResult = withContext(Dispatchers.IO) {
        val normalized = PrivateDnsHost.normalize(hostname)
            ?: return@withContext PrivateDnsResult.Failure(PrivateDnsError.INVALID_HOSTNAME)
        if (!access.hasPermission()) return@withContext permissionFailure()

        val previousMode = command("settings", "get", "global", MODE_KEY)
            ?: return@withContext PrivateDnsResult.Failure(PrivateDnsError.COMMAND_FAILED)
        val previousHostname = command("settings", "get", "global", HOSTNAME_KEY)
            ?: return@withContext PrivateDnsResult.Failure(PrivateDnsError.COMMAND_FAILED)

        if (!commandSucceeded("settings", "put", "global", HOSTNAME_KEY, normalized) ||
            !commandSucceeded("settings", "put", "global", MODE_KEY, MODE_HOSTNAME)
        ) {
            restore(previousMode, previousHostname)
            return@withContext PrivateDnsResult.Failure(PrivateDnsError.COMMAND_FAILED)
        }
        verify(PrivateDnsState(PrivateDnsMode.PROVIDER, normalized))
    }

    suspend fun setMode(mode: PrivateDnsMode): PrivateDnsResult = withContext(Dispatchers.IO) {
        if (mode == PrivateDnsMode.PROVIDER) {
            return@withContext PrivateDnsResult.Failure(PrivateDnsError.INVALID_HOSTNAME)
        }
        if (!access.hasPermission()) return@withContext permissionFailure()

        val encodedMode = if (mode == PrivateDnsMode.AUTOMATIC) MODE_AUTOMATIC else MODE_OFF
        if (!commandSucceeded("settings", "put", "global", MODE_KEY, encodedMode)) {
            return@withContext PrivateDnsResult.Failure(PrivateDnsError.COMMAND_FAILED)
        }
        verify(PrivateDnsState(mode))
    }

    private fun verify(expected: PrivateDnsState): PrivateDnsResult {
        val result = observeBlocking() ?: return PrivateDnsResult.Failure(PrivateDnsError.STATE_NOT_APPLIED)
        return if (result == expected) {
            PrivateDnsResult.Success(result)
        } else {
            PrivateDnsResult.Failure(PrivateDnsError.STATE_NOT_APPLIED)
        }
    }

    private fun observeBlocking(): PrivateDnsState? {
        val mode = command("settings", "get", "global", MODE_KEY)?.trim() ?: return null
        return when (mode) {
            "hostname" -> command("settings", "get", "global", HOSTNAME_KEY)
                ?.trim()
                ?.let(PrivateDnsHost::normalize)
                ?.let { PrivateDnsState(PrivateDnsMode.PROVIDER, it) }
            "opportunistic" -> PrivateDnsState(PrivateDnsMode.AUTOMATIC)
            "off", "null", "" -> PrivateDnsState(PrivateDnsMode.OFF)
            else -> null
        }
    }

    private fun restore(mode: String, hostname: String) {
        restoreKey(HOSTNAME_KEY, hostname)
        restoreKey(MODE_KEY, mode)
    }

    private fun restoreKey(key: String, value: String) {
        if (value.trim() == "null" || value.isBlank()) {
            commandSucceeded("settings", "delete", "global", key)
        } else {
            commandSucceeded("settings", "put", "global", key, value.trim())
        }
    }

    private fun commandSucceeded(vararg arguments: String): Boolean =
        commandRunner.execute(arguments)?.exitCode == 0

    private fun command(vararg arguments: String): String? =
        commandRunner.execute(arguments)?.takeIf { it.exitCode == 0 }?.output

    private fun permissionFailure() = PrivateDnsResult.Failure(
        if (access.isAvailable()) {
            PrivateDnsError.PERMISSION_REQUIRED
        } else {
            PrivateDnsError.SHIZUKU_UNAVAILABLE
        },
    )

    private companion object {
        const val TAG = "PrivateDns"
        const val MODE_KEY = "private_dns_mode"
        const val HOSTNAME_KEY = "private_dns_specifier"
        const val MODE_HOSTNAME = "hostname"
        const val MODE_AUTOMATIC = "opportunistic"
        const val MODE_OFF = "off"
    }
}

internal data class PrivateDnsCommandResult(val exitCode: Int, val output: String)

internal fun interface PrivateDnsCommandRunner {
    fun execute(arguments: Array<out String>): PrivateDnsCommandResult?
}

internal interface PrivateDnsAccess {
    fun isAvailable(): Boolean
    fun hasPermission(): Boolean
}

private object ShizukuPrivateDnsAccess : PrivateDnsAccess {
    override fun isAvailable(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Exception) {
        false
    }

    override fun hasPermission(): Boolean = isAvailable() && try {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Exception) {
        false
    }
}

private object ShizukuPrivateDnsCommandRunner : PrivateDnsCommandRunner {
    override fun execute(arguments: Array<out String>): PrivateDnsCommandResult? = try {

        val process = de.mm20.launcher2.ktx.ShizukuProcesses.start(arguments.toList().toTypedArray())
        try {
            if (!process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                null
            } else {
                val output = process.inputStream.bufferedReader().use {
                    it.readText().take(MAX_OUTPUT_CHARS)
                }
                PrivateDnsCommandResult(process.exitValue(), output)
            }
        } finally {
            process.destroy()
        }
    } catch (_: Exception) {
        Log.w(TAG, "Private DNS command failed")
        null
    }

    private const val TAG = "PrivateDns"
    private const val COMMAND_TIMEOUT_SECONDS = 5L
    private const val MAX_OUTPUT_CHARS = 512
}

object PrivateDnsHost {
    fun normalize(hostname: String): String? {
        val input = hostname.trim()
        if (input.endsWith("..")) return null
        val candidate = input.removeSuffix(".")
        if (candidate.isEmpty() || candidate.length > 253) return null
        return runCatching {
            java.net.IDN.toASCII(candidate, java.net.IDN.USE_STD3_ASCII_RULES)
                .lowercase()
                .takeIf { ascii ->
                    ascii.length <= 253 &&
                        ascii.split('.').all { label -> label.isNotEmpty() && label.length <= 63 } &&
                        '.' in ascii &&
                        !isIpv4Address(ascii)
                }
        }.getOrNull()
    }

    private fun isIpv4Address(hostname: String): Boolean {
        val parts = hostname.split('.')
        return parts.size == 4 && parts.all { part -> part.toIntOrNull()?.let { it in 0..255 } == true }
    }
}
