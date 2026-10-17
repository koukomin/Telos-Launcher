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
    const val VALUE_OPENGL = "opengl"

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
        when {
            raw.contains("skiavk", ignoreCase = true) || raw.equals("vulkan", true) -> MODE_VULKAN
            raw.contains("opengl", ignoreCase = true) || raw.contains("skiagl", ignoreCase = true) -> MODE_OPENGL
            raw.isBlank() -> MODE_DEFAULT
            else -> raw
        }
    }

    suspend fun apply(mode: String): Boolean = withContext(Dispatchers.IO) {
        val cmd = when (mode) {
            MODE_VULKAN -> "setprop $PROP $VALUE_VULKAN"
            MODE_OPENGL -> "setprop $PROP $VALUE_OPENGL"
            else -> "setprop $PROP \"\""
        }
        val ok = privilegedShell(cmd) != null
        if (!ok) Log.w(TAG, "Could not set $PROP")
        ok
    }

    suspend fun privilegedAvailable(): Boolean = withContext(Dispatchers.IO) {
        privilegedShell("echo ok")?.contains("ok") == true
    }

    private fun privilegedShell(cmd: String): String? {
        runCatching {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            val text = p.inputStream.bufferedReader().readText()
            if (p.waitFor() == 0) return text
        }
        return runCatching {
            val shizuku = Class.forName("rikka.shizuku.Shizuku")
            if (shizuku.getMethod("pingBinder").invoke(null) != true) return@runCatching null
            if (shizuku.getMethod("checkSelfPermission").invoke(null) as Int != 0) return@runCatching null
            val method = shizuku.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java,
            )
            method.isAccessible = true
            val proc = method.invoke(null, arrayOf("sh", "-c", cmd), null, null) ?: return@runCatching null
            val getIn = proc.javaClass.methods.first { it.name == "getInputStream" }
            (getIn.invoke(proc) as java.io.InputStream).bufferedReader().readText()
        }.getOrNull()
    }

    private const val TAG = "HwuiRenderer"
}
