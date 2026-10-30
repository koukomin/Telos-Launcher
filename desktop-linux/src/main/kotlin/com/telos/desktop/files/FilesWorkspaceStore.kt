package com.telos.desktop.files

import java.io.File
import java.util.Properties
import java.util.UUID

data class FilesTabState(
    val id: String,
    val path: String,
)

data class FilesWorkspaceState(
    val tabs: List<FilesTabState>,
    val left: String,
    val right: String,
    val dual: Boolean,
    val activeRight: Boolean = false,
)

object FilesWorkspaceStore {
    const val MaxTabs = 16

    fun load(file: File, defaultPath: String): FilesWorkspaceState {
        if (!file.isFile) return single(defaultPath)
        val p = Properties()
        file.inputStream().use { p.load(it) }
        val paths = (p.getProperty("paths") ?: defaultPath).split('\u0001').filter { it.isNotBlank() }.take(MaxTabs)
        if (paths.isEmpty()) return single(defaultPath)
        val tabs = paths.map { FilesTabState(UUID.randomUUID().toString(), it) }
        val leftIdx = p.getProperty("left", "0").toIntOrNull()?.coerceIn(0, tabs.lastIndex) ?: 0
        val rightIdx = p.getProperty("right", "0").toIntOrNull()?.coerceIn(0, tabs.lastIndex) ?: 0
        return FilesWorkspaceState(
            tabs = tabs,
            left = tabs[leftIdx].id,
            right = tabs[rightIdx].id,
            dual = p.getProperty("dual", "false").toBoolean(),
            activeRight = p.getProperty("activeRight", "false").toBoolean(),
        )
    }

    fun save(file: File, state: FilesWorkspaceState) {
        file.parentFile?.mkdirs()
        val p = Properties()
        p["paths"] = state.tabs.joinToString("\u0001") { it.path }
        p["left"] = state.tabs.indexOfFirst { it.id == state.left }.coerceAtLeast(0).toString()
        p["right"] = state.tabs.indexOfFirst { it.id == state.right }.coerceAtLeast(0).toString()
        p["dual"] = state.dual.toString()
        p["activeRight"] = state.activeRight.toString()
        file.outputStream().use { p.store(it, "Telos Files workspace") }
    }

    fun single(path: String) = FilesWorkspaceState(
        tabs = listOf(FilesTabState("home", path)),
        left = "home",
        right = "home",
        dual = false,
    )
}

object CloudPlacesStore {
    private val allowed = listOf("file:", "smb:", "sftp:", "ssh:", "ftp:", "dav:", "davs:", "nfs:")

    fun allowedUri(uri: String): Boolean {
        val u = uri.trim().lowercase()
        return allowed.any { u.startsWith(it) }
    }

    fun load(file: File): List<NetworkPlace> {
        if (!file.isFile) return emptyList()
        val p = Properties()
        file.inputStream().use { p.load(it) }
        val n = p.getProperty("count", "0").toIntOrNull() ?: 0
        return (0 until n).mapNotNull { i ->
            val uri = p.getProperty("uri.$i") ?: return@mapNotNull null
            if (!allowedUri(uri)) return@mapNotNull null
            val label = p.getProperty("label.$i") ?: uri
            NetworkPlace(label, uri, null)
        }
    }

    fun save(file: File, places: List<NetworkPlace>) {
        file.parentFile?.mkdirs()
        val p = Properties()
        val ok = places.filter { allowedUri(it.uri) }.take(32)
        p["count"] = ok.size.toString()
        ok.forEachIndexed { i, place ->
            p["uri.$i"] = place.uri
            p["label.$i"] = place.label
        }
        file.outputStream().use { p.store(it, "Telos Files cloud places") }
    }
}
