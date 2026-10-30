package com.telos.desktop.search

import com.telos.desktop.apps.DesktopEntry
import com.telos.desktop.apps.LinuxAppScanner
import com.telos.desktop.apps.TelosVirtualApp
import com.telos.desktop.apps.TelosVirtualApps
import com.telos.desktop.xdg.XdgUserDirsManager
import java.io.File

enum class SearchKind { TelosApp, Desktop, File }

data class SearchHit(
    val id: String,
    val title: String,
    val subtitle: String,
    val kind: SearchKind,
    val score: Int,
    val telos: TelosVirtualApp? = null,
    val desktop: DesktopEntry? = null,
    val file: File? = null,
)

object DesktopSearch {
    fun rank(query: String, title: String, extra: String = ""): Int {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return 0
        val t = title.lowercase()
        val e = extra.lowercase()
        return when {
            t == q -> 100
            t.startsWith(q) -> 80
            t.contains(q) -> 50
            e.contains(q) -> 30
            else -> 0
        }
    }

    fun search(
        query: String,
        apps: List<DesktopEntry> = LinuxAppScanner.scan(),
        filesRoot: List<File> = defaultFileRoots(),
        telos: List<TelosVirtualApp> = TelosVirtualApps.all,
    ): List<SearchHit> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val out = ArrayList<SearchHit>(64)
        for (app in telos) {
            val score = rank(q, app.label, app.domain)
            if (score > 0) {
                out += SearchHit("telos:${app.domain}", app.label, app.domain, SearchKind.TelosApp, score, telos = app)
            }
        }
        for (entry in apps) {
            val score = rank(q, entry.name, entry.comment + " " + entry.exec)
            if (score > 0) {
                out += SearchHit("desk:${entry.file.absolutePath}", entry.name, entry.comment, SearchKind.Desktop, score, desktop = entry)
            }
        }
        if (q.length >= 2) {
            val seen = HashSet<String>()
            for (root in filesRoot) {
                walkFiles(root, maxDepth = 2, maxHits = 80) { file ->
                    if (!seen.add(file.absolutePath)) return@walkFiles
                    val score = rank(q, file.name)
                    if (score > 0) {
                        out += SearchHit("file:${file.absolutePath}", file.name, file.parent ?: "", SearchKind.File, score, file = file)
                    }
                }
            }
        }
        return out.sortedWith(compareByDescending<SearchHit> { it.score }.thenBy { it.title.lowercase() }).take(80)
    }

    fun defaultFileRoots(): List<File> {
        val dirs = XdgUserDirsManager.resolve()
        return listOf(dirs.desktop, dirs.download, dirs.documents, dirs.home).filter { it.isDirectory }
    }

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
                    if (depth < maxDepth && !child.name.startsWith(".")) queue.addLast(child to depth + 1)
                } else {
                    visit(child)
                    n++
                }
            }
        }
    }
}
