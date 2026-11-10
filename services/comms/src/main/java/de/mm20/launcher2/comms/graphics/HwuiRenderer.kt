package de.mm20.launcher2.comms.graphics

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * System-wide Skia HWUI renderer: Vulkan (`skiavk`) or OpenGL (`opengl`).
 * Same `debug.hwui.renderer` property used by GAMA. Needs a privileged shell
 * (root, then Shizuku) or ADB. Android drops the property on reboot.
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
        val raw = privilegedShell("getprop $PROP")?.trim().orEmpty()
        parse(raw)
    }

    suspend fun apply(mode: String): Boolean = withContext(Dispatchers.IO) {
        val value = when (mode) {
            MODE_VULKAN -> VALUE_VULKAN
            MODE_OPENGL -> VALUE_OPENGL
            else -> ""
        }
        
        val cmd = if (value.isEmpty()) {
            "setprop $PROP \"\"; resetprop -n $PROP \"\""
        } else {
            "setprop $PROP $value; resetprop -n $PROP $value"
        }

        val res = privilegedShell(cmd)
        val success = res != null || current() == mode
        
        if (!success) {
            Log.w(TAG, "Could not set $PROP to $value")
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

    private suspend fun privilegedShell(cmd: String): String? {
        val result = de.mm20.launcher2.ktx.PrivilegedShell.run(cmd) ?: return null
        if (result.exitCode != 0) return null
        return result.output
    }

    private const val TAG = "HwuiRenderer"
}
