package com.telos.desktop.display

import com.telos.desktop.settings.TelosDesktopSettings
import java.io.File
import java.util.concurrent.TimeUnit

enum class DisplayBackend { WaylandWlr, KScreen, X11, Unknown }

data class DisplayMode(
    val width: Int,
    val height: Int,
    val refreshHz: Double,
) {
    val label: String get() = "${width}x${height} @ ${"%.0f".format(refreshHz)}Hz"
}

data class DisplayOutput(
    val name: String,
    val connected: Boolean,
    val current: DisplayMode?,
    val modes: List<DisplayMode>,
    val scale: Double,
)

class LinuxDisplayManager(
    private val settingsStore: TelosDesktopSettings,
) {
    fun detectBackend(): DisplayBackend {
        val session = System.getenv("XDG_SESSION_TYPE")?.lowercase().orEmpty()
        if (onPath("kscreen-doctor")) return DisplayBackend.KScreen
        if (session == "wayland" && onPath("wlr-randr")) return DisplayBackend.WaylandWlr
        if (onPath("xrandr")) return DisplayBackend.X11
        if (session == "wayland") return DisplayBackend.WaylandWlr
        if (session == "x11") return DisplayBackend.X11
        return DisplayBackend.Unknown
    }

    fun queryOutputs(): List<DisplayOutput> {
        return when (detectBackend()) {
            DisplayBackend.KScreen -> parseKscreen()
            DisplayBackend.WaylandWlr -> parseWlr()
            DisplayBackend.X11 -> parseXrandr()
            DisplayBackend.Unknown -> emptyList()
        }
    }

    fun applyDisplayMode(output: String, resolution: String, refreshRate: Double, scale: Double): Boolean {
        val ok = when (detectBackend()) {
            DisplayBackend.KScreen -> {
                val mode = "${resolution}@${refreshRate}"
                run("kscreen-doctor", "output.$output.mode.$mode") &&
                    run("kscreen-doctor", "output.$output.scale.$scale")
            }
            DisplayBackend.WaylandWlr -> {
                run("wlr-randr", "--output", output, "--mode", "${resolution}@${refreshRate}", "--scale", scale.toString())
            }
            DisplayBackend.X11 -> {
                val rate = refreshRate.toInt().toString()
                run("xrandr", "--output", output, "--mode", resolution, "--rate", rate)
            }
            DisplayBackend.Unknown -> false
        }
        if (ok) {
            settingsStore.rememberOutput(output, resolution, refreshRate, scale)
            settingsStore.save()
        }
        return ok
    }

    fun applyPersistedOnStartup() {
        if (settingsStore.outputPrefs.isEmpty()) {
            val out = settingsStore.preferredOutput ?: return
            val res = settingsStore.preferredResolution ?: return
            val hz = settingsStore.preferredRefresh ?: return
            val scale = settingsStore.preferredScale ?: 1.0
            applyDisplayMode(out, res, hz, scale)
            return
        }
        settingsStore.outputPrefs.forEach { (name, spec) ->
            val parts = spec.split('@')
            if (parts.size < 3) return@forEach
            val res = parts[0]
            val hz = parts[1].toDoubleOrNull() ?: return@forEach
            val scale = parts[2].toDoubleOrNull() ?: 1.0
            applyDisplayMode(name, res, hz, scale)
        }
    }

    private fun parseXrandr(): List<DisplayOutput> {
        val text = capture("xrandr") ?: return emptyList()
        val outputs = ArrayList<DisplayOutput>()
        var currentName: String? = null
        var connected = false
        var currentMode: DisplayMode? = null
        val modes = ArrayList<DisplayMode>()
        val header = Regex("""^(\S+)\s+(connected|disconnected)""")
        val modeLine = Regex("""^\s+(\d+)x(\d+)\s+([\d.]+)""")
        for (line in text.lineSequence()) {
            val h = header.find(line)
            if (h != null) {
                if (currentName != null) {
                    outputs += DisplayOutput(currentName!!, connected, currentMode, modes.toList(), 1.0)
                }
                currentName = h.groupValues[1]
                connected = h.groupValues[2] == "connected"
                currentMode = null
                modes.clear()
                continue
            }
            val m = modeLine.find(line) ?: continue
            val mode = DisplayMode(
                m.groupValues[1].toInt(),
                m.groupValues[2].toInt(),
                m.groupValues[3].toDouble(),
            )
            modes += mode
            if (line.contains('*')) currentMode = mode
        }
        if (currentName != null) {
            outputs += DisplayOutput(currentName!!, connected, currentMode, modes.toList(), 1.0)
        }
        return outputs.filter { it.connected }
    }

    private fun parseWlr(): List<DisplayOutput> {
        val text = capture("wlr-randr") ?: return emptyList()
        val outputs = ArrayList<DisplayOutput>()
        var name: String? = null
        var current: DisplayMode? = null
        val modes = ArrayList<DisplayMode>()
        var scale = 1.0
        val nameRe = Regex("""^(\S+)\s""")
        val modeRe = Regex("""(\d+)x(\d+)\s+px,\s+([\d.]+)\s+Hz""")
        for (line in text.lineSequence()) {
            if (!line.startsWith(" ") && !line.startsWith("\t") && line.isNotBlank()) {
                if (name != null) outputs += DisplayOutput(name!!, true, current, modes.toList(), scale)
                name = nameRe.find(line)?.groupValues?.get(1) ?: line.trim()
                current = null
                modes.clear()
                scale = 1.0
                continue
            }
            if (line.contains("Scale:")) {
                scale = line.substringAfter("Scale:").trim().toDoubleOrNull() ?: 1.0
            }
            val m = modeRe.find(line) ?: continue
            val mode = DisplayMode(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toDouble())
            modes += mode
            if (line.contains("current")) current = mode
        }
        if (name != null) outputs += DisplayOutput(name!!, true, current, modes.toList(), scale)
        return outputs
    }

    private fun parseKscreen(): List<DisplayOutput> {
        val text = capture("kscreen-doctor", "-o") ?: return emptyList()
        return parseWlrFromGeneric(text)
    }

    private fun parseWlrFromGeneric(text: String): List<DisplayOutput> {
        val outputs = ArrayList<DisplayOutput>()
        val outputRe = Regex("""Output:\s+(\S+).*?Modes:([\s\S]*?)(?=Output:|$)""")
        for (m in outputRe.findAll(text)) {
            val name = m.groupValues[1]
            val modes = ArrayList<DisplayMode>()
            var current: DisplayMode? = null
            val modeRe = Regex("""(\d+)x(\d+)@([\d.]+)""")
            for (mm in modeRe.findAll(m.groupValues[2])) {
                val mode = DisplayMode(mm.groupValues[1].toInt(), mm.groupValues[2].toInt(), mm.groupValues[3].toDouble())
                modes += mode
                current = current ?: mode
            }
            outputs += DisplayOutput(name, true, current, modes, 1.0)
        }
        return outputs.ifEmpty { parseXrandr() }
    }

    private fun onPath(name: String): Boolean {
        val path = System.getenv("PATH") ?: return false
        return path.split(':').any { File(it, name).canExecute() }
    }

    private fun run(vararg args: String): Boolean = try {
        val p = ProcessBuilder(*args).start()
        p.waitFor(8, TimeUnit.SECONDS)
        p.exitValue() == 0
    } catch (_: Exception) {
        false
    }

    private fun capture(vararg args: String): String? = try {
        val p = ProcessBuilder(*args).start()
        val text = p.inputStream.bufferedReader().readText()
        p.waitFor(8, TimeUnit.SECONDS)
        if (p.exitValue() == 0) text else null
    } catch (_: Exception) {
        null
    }
}
