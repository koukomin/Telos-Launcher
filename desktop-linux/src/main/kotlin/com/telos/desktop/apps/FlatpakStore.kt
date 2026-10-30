package com.telos.desktop.apps

import com.telos.desktop.linux.Sys

data class FlatpakRef(
    val id: String,
    val name: String,
    val origin: String,
    val installed: Boolean,
)

object FlatpakStore {
    fun available(): Boolean = Sys.which("flatpak") != null

    fun listInstalled(): List<FlatpakRef> {
        if (!available()) return emptyList()
        val r = Sys.run("flatpak", "list", "--app", "--columns=application,name,origin", timeoutSec = 15)
        if (!r.ok) return emptyList()
        return parseRows(r.stdout, installed = true)
    }

    fun search(query: String): List<FlatpakRef> {
        if (!available() || query.isBlank()) return emptyList()
        val r = Sys.run("flatpak", "search", query, "--columns=application,name", timeoutSec = 20)
        if (!r.ok) return emptyList()
        return parseRows(r.stdout, installed = false)
    }

    fun install(id: String): Boolean {
        if (!available() || id.isBlank()) return false
        return Sys.runBg("flatpak", "install", "-y", "--noninteractive", "flathub", id)
    }

    fun uninstall(id: String): Boolean {
        if (!available() || id.isBlank()) return false
        return Sys.runBg("flatpak", "uninstall", "-y", "--noninteractive", id)
    }

    fun updateAll(): Boolean {
        if (!available()) return false
        return Sys.runBg("flatpak", "update", "-y", "--noninteractive")
    }

    fun remotes(): List<String> {
        if (!available()) return emptyList()
        val r = Sys.run("flatpak", "remotes", "--columns=name", timeoutSec = 10)
        if (!r.ok) return emptyList()
        return r.stdout.lineSequence().map { it.trim() }.filter { it.isNotEmpty() && it != "Name" }.toList()
    }

    internal fun parseRows(stdout: String, installed: Boolean): List<FlatpakRef> {
        val out = ArrayList<FlatpakRef>()
        for (raw in stdout.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("Application") || line.startsWith("---")) continue
            val cols = line.split('\t', '|').map { it.trim() }.filter { it.isNotEmpty() }
            if (cols.isEmpty()) continue
            val id = cols[0]
            if (!id.contains('.')) continue
            val name = cols.getOrNull(1)?.ifBlank { id } ?: id
            val origin = cols.getOrNull(2) ?: ""
            out += FlatpakRef(id, name, origin, installed)
        }
        return out.distinctBy { it.id }
    }
}
