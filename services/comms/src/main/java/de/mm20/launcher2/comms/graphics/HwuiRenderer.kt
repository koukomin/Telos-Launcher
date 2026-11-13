package de.mm20.launcher2.comms.graphics

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * System-wide Skia HWUI renderer: Vulkan (`skiavk`) or OpenGL (`skiagl`).
 * Same `debug.hwui.renderer` property used by GAMA. Needs a privileged shell
 * (root, then Shizuku) or ADB. Android drops the property on reboot.
 *
 * Setting the property does not switch an already-running process: each process
 * picks its pipeline once at HWUI init. Callers must explain an app restart.
 */
object HwuiRenderer {
    const val MODE_DEFAULT = "default"
    const val MODE_VULKAN = "vulkan"
    const val MODE_OPENGL = "opengl"

    const val PROP = "debug.hwui.renderer"
    const val VALUE_VULKAN = "skiavk"
    const val VALUE_OPENGL = "skiagl"

    /** Shown when Shizuku and root are missing. */
    fun adbCommand(mode: String): String {
        val value = when (mode) {
            MODE_VULKAN -> VALUE_VULKAN
            MODE_OPENGL -> VALUE_OPENGL
            else -> ""
        }
        return if (value.isEmpty()) {
            "adb shell setprop $PROP \"\""
        } else {
            "adb shell setprop $PROP $value"
        }
    }

    suspend fun current(): String = withContext(Dispatchers.IO) {
        val raw = readProp().orEmpty()
        parse(raw)
    }

    suspend fun apply(mode: String): Boolean = withContext(Dispatchers.IO) {
        val value = when (mode) {
            MODE_VULKAN -> VALUE_VULKAN
            MODE_OPENGL -> VALUE_OPENGL
            else -> ""
        }

        // Run Magisk resetprop and setprop separately so a missing resetprop binary
        // cannot fail the whole chain after a successful setprop.
        if (value.isEmpty()) {
            runProp("resetprop --delete $PROP")
            runProp("resetprop -n $PROP \"\"")
            runProp("setprop $PROP \"\"")
        } else {
            runProp("resetprop -n $PROP $value")
            runProp("setprop $PROP $value")
        }

        val now = current()
        val success = when (mode) {
            MODE_DEFAULT -> now == MODE_DEFAULT
            else -> now == mode
        }
        if (!success) {
            Log.w(TAG, "Could not set $PROP to '$value' (live='$now')")
        }
        success
    }

    private fun parse(raw: String): String = when {
        raw.contains("skiavk", ignoreCase = true) || raw.equals("vulkan", true) -> MODE_VULKAN
        raw.contains("opengl", ignoreCase = true) || raw.contains("skiagl", ignoreCase = true) -> MODE_OPENGL
        raw.isBlank() -> MODE_DEFAULT
        else -> raw
    }

    suspend fun privilegedAvailable(): Boolean = withContext(Dispatchers.IO) {
        de.mm20.launcher2.ktx.PrivilegedShell.available()
    }

    private suspend fun readProp(): String? {
        val result = de.mm20.launcher2.ktx.PrivilegedShell.run("getprop $PROP") ?: return null
        // getprop prints empty string with exit 0 when unset; non-zero means shell failure.
        if (result.exitCode != 0 && result.output.isBlank()) return null
        return result.output.trim()
    }

    private suspend fun runProp(cmd: String): Boolean {
        val result = de.mm20.launcher2.ktx.PrivilegedShell.run(cmd) ?: return false
        // Some shells return non-zero for unknown resetprop while still printing nothing useful.
        return result.exitCode == 0
    }

    private const val TAG = "HwuiRenderer"
}
