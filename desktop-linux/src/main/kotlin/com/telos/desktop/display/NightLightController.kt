package com.telos.desktop.display

import com.telos.desktop.linux.Sys
import java.io.File

/**
 * Local screen temperature via host tools only (gammastep / redshift / wlsunset).
 * Never uses geolocation, Geoclue, or network APIs — PreferenceOnly when no tool is present.
 */
object NightLightController {
    enum class Backend { Gammastep, Redshift, Wlsunset, None }

    enum class ApplyStatus {
        /** Temperature applied through a detected host tool. */
        Applied,
        /** Night Light cleared / reset through a detected host tool. */
        Cleared,
        /** Preference saved; no suitable host tool on PATH. */
        PreferenceOnly,
        /** Tool present but invocation failed. */
        Failed,
    }

    data class Snapshot(
        val enabled: Boolean,
        val temperatureKelvin: Int,
        val backend: Backend,
        val status: ApplyStatus,
        val toolPath: String?,
    )

    const val DEFAULT_TEMP_K = 4500
    const val MIN_TEMP_K = 1000
    const val MAX_TEMP_K = 6500

    private val pidFile: File
        get() = File(
            System.getenv("TELOS_CONFIG_HOME")
                ?: File(System.getProperty("user.home"), ".config/telos").absolutePath,
            "night-light.pid",
        )

    fun clampTemp(kelvin: Int): Int = kelvin.coerceIn(MIN_TEMP_K, MAX_TEMP_K)

    fun detectBackend(): Backend = when {
        Sys.which("gammastep") != null -> Backend.Gammastep
        Sys.which("redshift") != null -> Backend.Redshift
        Sys.which("wlsunset") != null -> Backend.Wlsunset
        else -> Backend.None
    }

    fun toolPath(backend: Backend = detectBackend()): String? = when (backend) {
        Backend.Gammastep -> Sys.which("gammastep")
        Backend.Redshift -> Sys.which("redshift")
        Backend.Wlsunset -> Sys.which("wlsunset")
        Backend.None -> null
    }

    fun snapshot(enabled: Boolean, temperatureKelvin: Int): Snapshot {
        val backend = detectBackend()
        val temp = clampTemp(temperatureKelvin)
        val status = when (backend) {
            Backend.None -> ApplyStatus.PreferenceOnly
            else -> if (enabled) ApplyStatus.Applied else ApplyStatus.Cleared
        }
        return Snapshot(enabled, temp, backend, status, toolPath(backend))
    }

    /**
     * Apply or clear night light. With no host tool, returns [ApplyStatus.PreferenceOnly]
     * without claiming the display changed.
     */
    fun apply(enabled: Boolean, temperatureKelvin: Int): ApplyStatus {
        val backend = detectBackend()
        val temp = clampTemp(temperatureKelvin)
        if (backend == Backend.None) return ApplyStatus.PreferenceOnly
        return if (enabled) setTemperature(backend, temp) else clear(backend)
    }

    private fun setTemperature(backend: Backend, temp: Int): ApplyStatus {
        val ok = when (backend) {
            Backend.Gammastep -> Sys.run("gammastep", "-O", temp.toString()).ok
            Backend.Redshift -> Sys.run("redshift", "-P", "-O", temp.toString()).ok
            Backend.Wlsunset -> startWlsunset(temp)
            Backend.None -> false
        }
        return if (ok) ApplyStatus.Applied else ApplyStatus.Failed
    }

    private fun clear(backend: Backend): ApplyStatus {
        val ok = when (backend) {
            Backend.Gammastep -> Sys.run("gammastep", "-x").ok
            Backend.Redshift -> Sys.run("redshift", "-x").ok
            Backend.Wlsunset -> stopWlsunset()
            Backend.None -> false
        }
        return if (ok) ApplyStatus.Cleared else ApplyStatus.Failed
    }

    /** Constant temperature via matching high/low; no location / Geoclue. */
    private fun startWlsunset(temp: Int): Boolean {
        stopWlsunset()
        return try {
            val p = ProcessBuilder(
                "wlsunset",
                "-t", temp.toString(),
                "-T", temp.toString(),
            ).redirectErrorStream(true).start()
            pidFile.parentFile?.mkdirs()
            pidFile.writeText(p.pid().toString())
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun stopWlsunset(): Boolean {
        val pid = pidFile.takeIf { it.isFile }?.readText()?.trim()?.toLongOrNull()
        pidFile.delete()
        if (pid != null) {
            return try {
                ProcessHandle.of(pid).ifPresent { it.destroy() }
                true
            } catch (_: Exception) {
                Sys.run("kill", pid.toString()).ok
            }
        }
        // Best-effort if we lost the pid file but started wlsunset earlier in-session.
        return Sys.run("pkill", "-x", "wlsunset").exit.let { it == 0 || it == 1 }
    }
}
