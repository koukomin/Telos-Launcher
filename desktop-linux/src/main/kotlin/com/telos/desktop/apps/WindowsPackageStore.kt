package com.telos.desktop.apps

import com.telos.desktop.linux.Sys
import com.telos.desktop.platform.PlatformEnvironment
import com.telos.desktop.platform.TelosRuntimePlatform

/**
 * VOS-3 Windows package backends (winget / scoop).
 * PreferenceOnly / unavailable on Linux and macOS — never fakes installs there.
 */
enum class WindowsPackageBackend {
    Winget,
    Scoop,
}

data class WindowsPackageRef(
    val id: String,
    val name: String,
    val version: String = "",
    val source: WindowsPackageBackend,
    val installed: Boolean,
)

enum class WindowsPackageStatus {
    Available,
    PreferenceOnly,
    Unavailable,
}

object WindowsPackageStore {
    private val isWindows: Boolean
        get() = PlatformEnvironment.current == TelosRuntimePlatform.WindowsDesktop

    fun status(): WindowsPackageStatus = when {
        !isWindows -> WindowsPackageStatus.PreferenceOnly
        availableBackends().isNotEmpty() -> WindowsPackageStatus.Available
        else -> WindowsPackageStatus.Unavailable
    }

    fun availableBackends(): List<WindowsPackageBackend> {
        if (!isWindows) return emptyList()
        val out = ArrayList<WindowsPackageBackend>(2)
        if (wingetPath() != null) out += WindowsPackageBackend.Winget
        if (scoopPath() != null) out += WindowsPackageBackend.Scoop
        return out
    }

    fun wingetAvailable(): Boolean = wingetPath() != null
    fun scoopAvailable(): Boolean = scoopPath() != null

    fun listInstalled(backend: WindowsPackageBackend): List<WindowsPackageRef> {
        if (!isWindows) return emptyList()
        return when (backend) {
            WindowsPackageBackend.Winget -> {
                val bin = wingetPath() ?: return emptyList()
                val r = Sys.run(bin, "list", "--disable-interactivity", timeoutSec = 45)
                if (!r.ok) return emptyList()
                parseWingetList(r.stdout, installed = true)
            }
            WindowsPackageBackend.Scoop -> {
                val bin = scoopPath() ?: return emptyList()
                val r = Sys.run(bin, "list", timeoutSec = 30)
                if (!r.ok) return emptyList()
                parseScoopList(r.stdout, installed = true)
            }
        }
    }

    fun search(query: String, backend: WindowsPackageBackend): List<WindowsPackageRef> {
        if (!isWindows || query.isBlank()) return emptyList()
        return when (backend) {
            WindowsPackageBackend.Winget -> {
                val bin = wingetPath() ?: return emptyList()
                val r = Sys.run(bin, "search", query, "--disable-interactivity", timeoutSec = 45)
                if (!r.ok) return emptyList()
                parseWingetSearch(r.stdout)
            }
            WindowsPackageBackend.Scoop -> {
                val bin = scoopPath() ?: return emptyList()
                val r = Sys.run(bin, "search", query, timeoutSec = 30)
                if (!r.ok) return emptyList()
                parseScoopSearch(r.stdout)
            }
        }
    }

    fun install(id: String, backend: WindowsPackageBackend): Boolean {
        if (!isWindows || id.isBlank()) return false
        return when (backend) {
            WindowsPackageBackend.Winget -> {
                val bin = wingetPath() ?: return false
                Sys.runBg(
                    bin, "install", "-e", "--id", id,
                    "--accept-package-agreements", "--accept-source-agreements",
                    "--disable-interactivity",
                )
            }
            WindowsPackageBackend.Scoop -> {
                val bin = scoopPath() ?: return false
                Sys.runBg(bin, "install", id)
            }
        }
    }

    internal fun wingetPath(): String? {
        if (!isWindows) return null
        return Sys.which("winget") ?: Sys.which("winget.exe")
    }

    internal fun scoopPath(): String? {
        if (!isWindows) return null
        return Sys.which("scoop") ?: Sys.which("scoop.cmd") ?: Sys.which("scoop.ps1")
    }

    /**
     * Detect backends from a mocked `which` map (unit tests). Keys are command basenames.
     */
    internal fun detectBackendsFromWhich(which: Map<String, String?>): List<WindowsPackageBackend> {
        val out = ArrayList<WindowsPackageBackend>(2)
        if (!which["winget"].isNullOrBlank() || !which["winget.exe"].isNullOrBlank()) {
            out += WindowsPackageBackend.Winget
        }
        if (!which["scoop"].isNullOrBlank() ||
            !which["scoop.cmd"].isNullOrBlank() ||
            !which["scoop.ps1"].isNullOrBlank()
        ) {
            out += WindowsPackageBackend.Scoop
        }
        return out
    }

    internal fun parseWingetList(stdout: String, installed: Boolean): List<WindowsPackageRef> =
        parseWingetTable(stdout, installed)

    internal fun parseWingetSearch(stdout: String): List<WindowsPackageRef> =
        parseWingetTable(stdout, installed = false)

    private fun parseWingetTable(stdout: String, installed: Boolean): List<WindowsPackageRef> {
        val out = ArrayList<WindowsPackageRef>()
        var headerSeen = false
        var nameIdx = 0
        var idIdx = 1
        var verIdx = 2
        for (raw in stdout.lineSequence()) {
            val line = raw.trimEnd()
            if (line.isBlank()) continue
            if (line.startsWith("No package", ignoreCase = true) ||
                line.startsWith("Failed", ignoreCase = true) ||
                line.startsWith("The `msstore`", ignoreCase = true)
            ) {
                continue
            }
            if (!headerSeen) {
                val lower = line.lowercase()
                if (lower.contains("name") && lower.contains("id")) {
                    val cols = splitColumns(line)
                    nameIdx = cols.indexOfFirst { it.equals("Name", true) }.takeIf { it >= 0 } ?: 0
                    idIdx = cols.indexOfFirst { it.equals("Id", true) }.takeIf { it >= 0 } ?: 1
                    verIdx = cols.indexOfFirst { it.equals("Version", true) }.takeIf { it >= 0 } ?: 2
                    headerSeen = true
                }
                continue
            }
            if (line.all { it == '-' || it == '─' || it.isWhitespace() }) continue
            val cols = splitColumns(line)
            if (cols.size <= maxOf(nameIdx, idIdx)) continue
            val id = cols.getOrNull(idIdx)?.trim().orEmpty()
            if (id.isEmpty() || id.equals("Id", true)) continue
            // Winget Ids are typically Publisher.Product; allow scoop-like ids too when present.
            val name = cols.getOrNull(nameIdx)?.trim()?.ifBlank { id } ?: id
            val version = cols.getOrNull(verIdx)?.trim().orEmpty()
            out += WindowsPackageRef(id, name, version, WindowsPackageBackend.Winget, installed)
        }
        return out.distinctBy { it.id }
    }

    internal fun parseScoopList(stdout: String, installed: Boolean): List<WindowsPackageRef> {
        val out = ArrayList<WindowsPackageRef>()
        var headerSeen = false
        for (raw in stdout.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            val cols = splitColumns(line)
            if (!headerSeen) {
                if (cols.any { it.equals("Name", true) }) {
                    headerSeen = true
                }
                continue
            }
            if (cols.isEmpty() || isAsciiRuleLine(cols[0])) continue
            val id = cols[0].trim()
            if (id.isEmpty() || id.equals("Name", true)) continue
            val version = cols.getOrNull(1)?.trim().orEmpty()
            out += WindowsPackageRef(id, id, version, WindowsPackageBackend.Scoop, installed)
        }
        return out.distinctBy { it.id }
    }

    internal fun parseScoopSearch(stdout: String): List<WindowsPackageRef> {
        val out = ArrayList<WindowsPackageRef>()
        var headerSeen = false
        for (raw in stdout.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            // Scoop search often prints "Results from other buckets…" section headers.
            if (line.endsWith(":") && !line.contains('\t') && splitColumns(line).size == 1) {
                headerSeen = false
                continue
            }
            val cols = splitColumns(line)
            if (!headerSeen) {
                if (cols.any { it.equals("Name", true) }) {
                    headerSeen = true
                } else if (cols.isNotEmpty() && !cols[0].equals("Name", true) &&
                    !isAsciiRuleLine(cols[0])
                ) {
                    // Some scoop versions omit the header and print name/version/source.
                    val id = cols[0]
                    val version = cols.getOrNull(1).orEmpty()
                    out += WindowsPackageRef(id, id, version, WindowsPackageBackend.Scoop, installed = false)
                }
                continue
            }
            if (cols.isEmpty() || isAsciiRuleLine(cols[0])) continue
            val id = cols[0].trim()
            if (id.isEmpty() || id.equals("Name", true)) continue
            val version = cols.getOrNull(1)?.trim().orEmpty()
            out += WindowsPackageRef(id, id, version, WindowsPackageBackend.Scoop, installed = false)
        }
        return out.distinctBy { it.id }
    }

    private fun isAsciiRuleLine(token: String): Boolean =
        token.isNotEmpty() && token.all { it == '-' || it == '─' || it == '=' }

    private fun splitColumns(line: String): List<String> {
        if (line.contains('\t')) {
            return line.split('\t').map { it.trim() }.filter { it.isNotEmpty() }
        }
        // Winget aligns columns with runs of 2+ spaces.
        return line.split(Regex("\\s{2,}")).map { it.trim() }.filter { it.isNotEmpty() }
    }
}
