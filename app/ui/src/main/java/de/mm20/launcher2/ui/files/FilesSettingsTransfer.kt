package de.mm20.launcher2.ui.files

import android.content.SharedPreferences
import de.mm20.launcher2.ui.files.remote.RemotePath
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.ByteArrayOutputStream

/** Portable appearance and navigation settings only. Connection secrets are never included. */
internal object FilesSettingsTransfer {
    private val folderKey = Regex("folder:[0-9a-f]{64}")
    const val MaxTabs = 16
    private const val MaxChars = 524288
    data class Workspace(val paths: List<String?>, val left: Int, val right: Int, val dual: Boolean, val activeRight: Boolean)
    data class Settings(val view: Int, val hidden: Boolean, val sort: String, val ascending: Boolean,
                        val foldersFirst: Boolean, val bookmarks: Set<String>, val folders: Map<String, String>, val workspace: Workspace, val toolbar: FilesToolbarConfig)
    fun read(input: InputStream): String {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (true) {
            val count = input.read(buffer, 0, minOf(buffer.size, MaxChars - out.size() + 1))
            if (count < 0) return Charsets.UTF_8.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(out.toByteArray())).toString()
            require(out.size() + count <= MaxChars) { "Settings file too large" }
            out.write(buffer, 0, count)
        }
    }
    private fun validPath(path: String) = path.length in 1..8192 && '\u0000' !in path &&
        (path.startsWith("/") || RemotePath.isRemote(path) || ArchivePath.isArchive(path) || de.mm20.launcher2.ui.files.vault.VaultPath.isVault(path))
    private fun integer(json: JSONObject, key: String): Int {
        val value = json.get(key)
        require(value is Int || value is Long)
        val number = (value as Number).toLong()
        require(number in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong())
        return number.toInt()
    }
    private fun boolean(json: JSONObject, key: String): Boolean = (json.get(key) as? Boolean) ?: error("Invalid boolean")
    fun parse(text: String): Settings {
        require(text.length <= MaxChars && text.toByteArray(Charsets.UTF_8).size <= MaxChars)
        val json = JSONObject(text)
        require(integer(json, "version") == 1)
        val view = integer(json, "viewMode").also { require(it in 0..2) }
        val sort = json.getString("sortKey").also { SortKey.valueOf(it) }
        val bookmarksJson = json.getJSONArray("bookmarks")
        require(bookmarksJson.length() <= 1000)
        val bookmarks = (0 until bookmarksJson.length()).map { bookmarksJson.getString(it).also { path -> require(validPath(path)) } }.toSet()
        val foldersJson = json.getJSONObject("folders")
        require(foldersJson.length() <= 1000)
        val folders = foldersJson.keys().asSequence().associateWith { key ->
            require(key.matches(folderKey))
            val folder = foldersJson.getJSONObject(key)
            SortKey.valueOf(folder.getString("sort"))
            require(integer(folder, "view") in 0..2)
            boolean(folder, "ascending"); boolean(folder, "foldersFirst"); boolean(folder, "hidden")
            JSONObject().put("sort", folder.getString("sort")).put("view", integer(folder, "view"))
                .put("ascending", boolean(folder, "ascending")).put("foldersFirst", boolean(folder, "foldersFirst"))
                .put("hidden", boolean(folder, "hidden")).toString()
        }
        val ws = json.getJSONObject("workspace")
        val pathsJson = ws.getJSONArray("paths")
        require(pathsJson.length() in 1..MaxTabs)
        val paths = (0 until pathsJson.length()).map { if (pathsJson.isNull(it)) null else pathsJson.getString(it).also { path -> require(validPath(path)) } }
        val left = integer(ws, "left"); val right = integer(ws, "right")
        require(left in paths.indices && right in paths.indices)
        val dual = boolean(ws, "dual")
        require(!dual || left != right)
        return Settings(view, boolean(json, "hidden"), sort, boolean(json, "sortAsc"), boolean(json, "foldersFirst"), bookmarks, folders,
            Workspace(paths, left, right, dual, dual && boolean(ws, "activeRight")),
            FilesToolbarConfig.decode(json.optString("toolbarOrder", null), json.optString("toolbarVisible", null)))
    }
    fun export(prefs: SharedPreferences, workspace: Workspace): String {
        val folders = JSONObject()
        prefs.all.filterKeys { it.matches(folderKey) }.forEach { (key, value) ->
            if (value is String) runCatching { folders.put(key, JSONObject(value)) }
        }
        val text = JSONObject().put("version", 1).put("viewMode", prefs.getInt("viewMode", 0)).put("hidden", prefs.getBoolean("hidden", false))
            .put("sortKey", prefs.getString("sortKey", "Name")).put("sortAsc", prefs.getBoolean("sortAsc", true))
            .put("foldersFirst", prefs.getBoolean("foldersFirst", true)).put("bookmarks", JSONArray(prefs.getStringSet("bookmarks", emptySet())!!.sorted()))
            .put("folders", folders).put("workspace", workspaceJson(workspace))
            .put("toolbarOrder", prefs.getString("toolbarOrder", null)).put("toolbarVisible", prefs.getString("toolbarVisible", null)).toString(2)
        parse(text) // The same bounds and schema apply to exported settings.
        return text
    }
    fun workspaceJson(workspace: Workspace) = JSONObject().put("paths", JSONArray(workspace.paths.map { it ?: JSONObject.NULL }))
        .put("left", workspace.left).put("right", workspace.right).put("dual", workspace.dual).put("activeRight", workspace.activeRight)
    fun apply(prefs: SharedPreferences, settings: Settings) {
        val edit = prefs.edit()
        prefs.all.keys.filter { it.matches(folderKey) }.forEach { edit.remove(it) }
        edit.putInt("viewMode", settings.view).putBoolean("grid", settings.view != 0).putBoolean("hidden", settings.hidden)
            .putString("sortKey", settings.sort).putBoolean("sortAsc", settings.ascending).putBoolean("foldersFirst", settings.foldersFirst)
            .putStringSet("bookmarks", settings.bookmarks).putString("workspace", workspaceJson(settings.workspace).toString())
            .putString("toolbarOrder", settings.toolbar.order.joinToString(",") { it.name })
            .putString("toolbarVisible", settings.toolbar.actions.joinToString(",") { it.name })
        settings.folders.forEach { (key, value) -> edit.putString(key, value) }
        check(edit.commit()) { "Could not save settings" }
    }
}
