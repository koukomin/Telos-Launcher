package com.telos.desktop.panel

import com.telos.desktop.linux.Sys
import java.io.File

data class BatteryState(
    val percent: Int,
    val charging: Boolean,
    val watts: Double?,
    val hoursLeft: Double?,
    val present: Boolean,
)

data class PowerState(
    val battery: BatteryState,
    val profile: String,
    val profiles: List<String>,
    val gpuMode: String?,
    val gpuModes: List<String>,
    val screenBrightness: Int,
    val kbdBrightness: Int,
)

object LaptopPowerManager {
    fun snapshot(): PowerState {
        val batDir = listOf("BAT0", "BAT1").map { File("/sys/class/power_supply/$it") }.firstOrNull { it.isDirectory }
        val ac = listOf("AC", "ACAD", "ADP1").map { File("/sys/class/power_supply/$it") }.firstOrNull { it.isDirectory }
        val percent = batDir?.let { Sys.readText("${it.path}/capacity")?.toIntOrNull() } ?: 100
        val status = batDir?.let { Sys.readText("${it.path}/status") }.orEmpty()
        val charging = status.equals("Charging", true) || Sys.readText("${ac?.path}/online") == "1"
        val powerNow = batDir?.let { Sys.readLong("${it.path}/power_now") }
        val currentNow = batDir?.let { Sys.readLong("${it.path}/current_now") }
        val voltage = batDir?.let { Sys.readLong("${it.path}/voltage_now") }
        val watts = when {
            powerNow != null && powerNow > 0 -> powerNow / 1_000_000.0
            currentNow != null && voltage != null -> (currentNow / 1_000_000.0) * (voltage / 1_000_000.0)
            else -> null
        }
        val energy = batDir?.let { Sys.readLong("${it.path}/energy_now") }
        val hours = if (watts != null && watts > 0.1 && energy != null) (energy / 1_000_000.0) / watts else null
        val profile = Sys.run("powerprofilesctl", "get").stdout.trim().ifBlank {
            Regex("Active profile: (\\S+)").find(Sys.run("asusctl", "profile", "-p").stdout)?.groupValues?.get(1) ?: "balanced"
        }
        val profiles = listOf("power-saver", "balanced", "performance")
        val gpu = Sys.run("supergfxctl", "-g").stdout.trim().ifBlank { null }
        val gpuModes = Sys.run("supergfxctl", "-s").stdout.lines().map { it.trim() }.filter { it.isNotBlank() }
            .ifEmpty { listOf("Integrated", "Hybrid", "Dedicated", "AsusMuxDgpu") }
        val screen = brightness("intel_backlight") ?: brightness("amdgpu_bl0") ?: brightness("acpi_video0") ?: 50
        val kbd = brightness("asus::kbd_backlight") ?: 0
        return PowerState(
            BatteryState(percent, charging, watts, hours, batDir != null),
            profile,
            profiles,
            gpu,
            gpuModes,
            screen,
            kbd,
        )
    }

    fun setProfile(name: String) {
        if (!Sys.run("powerprofilesctl", "set", name).ok) Sys.run("asusctl", "profile", "-P", name)
    }

    fun setGpuMode(mode: String) { Sys.run("supergfxctl", "-m", mode, timeoutSec = 8) }

    fun setScreenBrightness(percent: Int) {
        val p = percent.coerceIn(0, 100)
        if (!Sys.run("brightnessctl", "set", "$p%").ok) {
            Sys.run("brightnessctl", "-d", "amdgpu_bl1", "set", "$p%")
        }
    }

    fun setKbdBrightness(percent: Int) {
        Sys.run("brightnessctl", "-d", "asus::kbd_backlight", "set", "${percent.coerceIn(0, 100)}%")
    }

    private fun brightness(device: String): Int? {
        val dir = File("/sys/class/backlight/$device")
        val kbd = File("/sys/class/leds/$device")
        val d = if (dir.isDirectory) dir else if (kbd.isDirectory) kbd else return null
        val now = Sys.readLong("${d.path}/brightness") ?: return null
        val max = Sys.readLong("${d.path}/max_brightness") ?: return null
        if (max <= 0) return null
        return ((now * 100) / max).toInt()
    }
}
