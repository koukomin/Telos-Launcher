package com.telos.desktop.search

import com.telos.desktop.apps.DesktopEntry
import com.telos.desktop.apps.TelosVirtualApp
import com.telos.desktop.vault.PortableTraceGuard
import com.telos.desktop.vault.TelosSessionHome
import com.telos.desktop.xdg.XdgUserDirsManager
import java.io.File

/** Kinds shown in the Helix global command palette (KRunner / PowerToys Run class). */
enum class HelixPaletteKind {
    Calculator,
    Window,
    Command,
    TelosApp,
    Desktop,
    File,
}

data class HelixPaletteItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val kind: HelixPaletteKind,
    val score: Int,
    val windowId: Long? = null,
    val telosDomain: String? = null,
    val desktopPath: String? = null,
    val filePath: String? = null,
    val commandId: String? = null,
    val calcValue: String? = null,
)

data class HelixPaletteWindow(
    val id: Long,
    val title: String,
)

/**
 * Privacy-safe, rankable actions for [com.telos.desktop.ui.HelixCommandPalette].
 * Pure functions — no cloud, no Compose, unit-testable.
 */
object HelixPaletteActions {
    const val CMD_FILES = "open_files"
    const val CMD_SETTINGS = "open_settings"
    const val CMD_NETWORK = "open_network"
    const val CMD_KILL = "kill_focused"
    const val CMD_PASTE_VAULT = "paste_vault_clip"

    /**
     * File roots for palette file hits: vault session home / portable data only.
     * Never indexes [PortableTraceGuard.hostProfileHome] when it differs from the session home.
     */
    fun privacySafeFileRoots(
        xdg: () -> List<File> = { DesktopSearch.defaultFileRoots() },
        portable: Boolean = PortableTraceGuard.isPortable(),
        portableRoot: () -> File = { PortableTraceGuard.dataRoot() },
        sessionHome: () -> File = { File(System.getProperty("user.home")) },
        hostHome: () -> File = {
            System.getenv("TELOS_HOST_HOME")?.takeIf { it.isNotBlank() }?.let { File(it) }
                ?: PortableTraceGuard.hostProfileHome()
        },
        vaultMount: () -> File? = { TelosSessionHome.vaultMount },
    ): List<File> {
        val out = ArrayList<File>(8)
        if (portable) {
            val root = portableRoot()
            out += root
            out += File(root, "session/home")
            out += File(root, "Desktop")
            out += File(root, "Downloads")
            out += File(root, "Documents")
            return out.distinctBy { it.absolutePath }.filter { it.isDirectory }
        }
        vaultMount()?.takeIf { it.isDirectory }?.let { out += it }
        val session = sessionHome()
        val hostRaw = hostHome()
        val home = runCatching { session.canonicalFile }.getOrElse { session }
        val host = runCatching { hostRaw.canonicalFile }.getOrElse { hostRaw }
        if (home != host) {
            // Session $HOME is vault (P1) — XDG dirs under vault are OK.
            out += xdg().filter { file ->
                val c = runCatching { file.canonicalFile }.getOrElse { file }
                c == home || c.absolutePath.startsWith(home.absolutePath + File.separator)
            }
        }
        // Explicit Host Home session: still allow XDG under that home (user opted in).
        if (home == host && System.getenv("TELOS_SESSION") != "1" && !TelosSessionHome.full) {
            // Windowed / unlocked without full session: do not walk host home.
            return out.distinctBy { it.absolutePath }.filter { it.isDirectory }
        }
        if (home == host) {
            out += xdg()
        }
        return out.distinctBy { it.absolutePath }.filter { it.isDirectory }
    }

    fun rank(query: String, title: String, keywords: String = ""): Int =
        DesktopSearch.rank(query, title, keywords)

    /**
     * Builds ranked palette rows for [query]. Empty query shows builtins + open windows + Telos apps.
     */
    fun build(
        query: String,
        windows: List<HelixPaletteWindow> = emptyList(),
        telos: List<TelosVirtualApp> = emptyList(),
        apps: List<DesktopEntry> = emptyList(),
        filesRoot: List<File> = emptyList(),
        hasFocusedWindow: Boolean = false,
        hasVaultClip: Boolean = false,
        commandLabels: CommandLabels = CommandLabels(),
        limit: Int = 24,
    ): List<HelixPaletteItem> {
        val q = query.trim()
        val out = ArrayList<HelixPaletteItem>(64)

        if (q.isNotEmpty()) {
            DesktopSearch.evaluate(q)?.let { value ->
                out += HelixPaletteItem(
                    id = "calc:$q",
                    title = value,
                    subtitle = commandLabels.calculator,
                    kind = HelixPaletteKind.Calculator,
                    score = 120,
                    calcValue = value,
                )
            }
        }

        fun addCommand(
            id: String,
            title: String,
            keywords: String,
            enabled: Boolean,
            baseScore: Int = 70,
        ) {
            if (!enabled) return
            val score = if (q.isEmpty()) {
                baseScore
            } else {
                rank(q, title, keywords).takeIf { it > 0 } ?: return
            }
            out += HelixPaletteItem(
                id = "cmd:$id",
                title = title,
                subtitle = commandLabels.command,
                kind = HelixPaletteKind.Command,
                score = score + if (q.isEmpty()) 0 else 5,
                commandId = id,
            )
        }

        addCommand(
            CMD_FILES,
            commandLabels.openFiles,
            "files file manager folder",
            enabled = true,
            baseScore = 85,
        )
        addCommand(
            CMD_SETTINGS,
            commandLabels.openSettings,
            "settings preferences config helix",
            enabled = true,
            baseScore = 84,
        )
        addCommand(
            CMD_NETWORK,
            commandLabels.openNetwork,
            "network wifi vpn tor dns firewall",
            enabled = true,
            baseScore = 83,
        )
        addCommand(
            CMD_KILL,
            commandLabels.killFocused,
            "kill close window labwc quit destroy",
            enabled = hasFocusedWindow,
            baseScore = 60,
        )
        addCommand(
            CMD_PASTE_VAULT,
            commandLabels.pasteVaultClip,
            "paste clipboard vault clip secure opsec",
            enabled = hasVaultClip,
            baseScore = 75,
        )

        for (win in windows) {
            val score = if (q.isEmpty()) {
                90
            } else {
                rank(q, win.title, "window ${commandLabels.window}").takeIf { it > 0 } ?: continue
            }
            out += HelixPaletteItem(
                id = "win:${win.id}",
                title = win.title,
                subtitle = commandLabels.window,
                kind = HelixPaletteKind.Window,
                score = score + 10,
                windowId = win.id,
            )
        }

        for (app in telos) {
            val score = if (q.isEmpty()) {
                50
            } else {
                rank(q, app.label, app.domain).takeIf { it > 0 } ?: continue
            }
            out += HelixPaletteItem(
                id = "telos:${app.domain}",
                title = app.label,
                subtitle = app.domain,
                kind = HelixPaletteKind.TelosApp,
                score = score,
                telosDomain = app.domain,
            )
        }

        if (q.isNotEmpty()) {
            for (entry in apps) {
                val score = rank(q, entry.name, entry.comment + " " + entry.exec)
                if (score <= 0) continue
                out += HelixPaletteItem(
                    id = "desk:${entry.file.absolutePath}",
                    title = entry.name,
                    subtitle = entry.comment.ifBlank { entry.exec },
                    kind = HelixPaletteKind.Desktop,
                    score = score,
                    desktopPath = entry.file.absolutePath,
                )
            }
            if (q.length >= 2) {
                val seen = HashSet<String>()
                for (root in filesRoot) {
                    walkFiles(root, maxDepth = 2, maxHits = 40) { file ->
                        if (!seen.add(file.absolutePath)) return@walkFiles
                        val score = rank(q, file.name)
                        if (score > 0) {
                            out += HelixPaletteItem(
                                id = "file:${file.absolutePath}",
                                title = file.name,
                                subtitle = file.parent ?: "",
                                kind = HelixPaletteKind.File,
                                score = score,
                                filePath = file.absolutePath,
                            )
                        }
                    }
                }
            }
        }

        return out
            .sortedWith(compareByDescending<HelixPaletteItem> { it.score }.thenBy { it.title.lowercase() })
            .distinctBy { it.id }
            .take(limit)
    }

    data class CommandLabels(
        val openFiles: String = "Open Files",
        val openSettings: String = "Open Settings",
        val openNetwork: String = "Open Network",
        val killFocused: String = "Close focused window",
        val pasteVaultClip: String = "Paste last vault clipboard",
        val window: String = "Window",
        val calculator: String = "Calculator",
        val command: String = "Command",
    )

    private fun walkFiles(root: File, maxDepth: Int, maxHits: Int, visit: (File) -> Unit) {
        val queue = ArrayDeque<Pair<File, Int>>()
        queue.add(root to 0)
        var n = 0
        while (queue.isNotEmpty() && n < maxHits) {
            val (dir, depth) = queue.removeFirst()
            val children = dir.listFiles() ?: continue
            for (child in children) {
                if (n >= maxHits) break
                if (child.isDirectory) {
                    if (depth < maxDepth && !child.name.startsWith(".")) {
                        queue.addLast(child to depth + 1)
                    }
                } else {
                    visit(child)
                    n++
                }
            }
        }
    }

    /** Resolves XDG roots only when they stay under [sessionHome] (test helper). */
    fun filterRootsUnderHome(roots: List<File>, sessionHome: File): List<File> {
        val home = runCatching { sessionHome.canonicalFile }.getOrElse { sessionHome }
        return roots.filter { file ->
            val c = runCatching { file.canonicalFile }.getOrElse { file }
            c == home || c.absolutePath.startsWith(home.absolutePath + File.separator)
        }
    }

    fun defaultXdgRoots(): List<File> = XdgUserDirsManager.resolve().let { dirs ->
        listOf(dirs.desktop, dirs.download, dirs.documents, dirs.music, dirs.pictures, dirs.videos, dirs.home)
            .distinctBy { it.absolutePath }
            .filter { it.isDirectory }
    }
}
