package de.mm20.launcher2.ui.files

import de.mm20.launcher2.search.GreekFold
import android.app.Application
import android.content.Context
import android.os.Environment
import android.os.StatFs
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import de.mm20.launcher2.ui.files.remote.ConnectionStore
import de.mm20.launcher2.ui.files.remote.RemoteConnection
import de.mm20.launcher2.ui.files.remote.RemotePath
import de.mm20.launcher2.ui.files.remote.RemoteRegistry
import java.io.File

/** A storage volume: the phone's own storage, an SD card, a USB drive. */
data class StorageVolume(val name: String, val path: String, val total: Long, val free: Long, val removable: Boolean)

data class ClipboardState(val paths: List<String>, val cut: Boolean, val rootMode: Boolean)

data class TaskState(val title: String, val progress: Float?, val cancel: CancelFlag)

class FilesViewModel(application: Application) : AndroidViewModel(application) {
    private val context: Context get() = getApplication()
    private val prefs = context.getSharedPreferences("telos_files", Context.MODE_PRIVATE)
    private val local = LocalFs()
    private val root = RootFs()

    /** Where the user is. null is the home page with the storages and the shortcuts. */
    var path by mutableStateOf<String?>(null)
        private set
    var entries by mutableStateOf<List<FsEntry>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var message by mutableStateOf<String?>(null)

    var sort by mutableStateOf(loadSort())
        private set
    var grid by mutableStateOf(prefs.getBoolean("grid", false))
        private set
    var showHidden by mutableStateOf(prefs.getBoolean("hidden", false))
        private set

    var selection by mutableStateOf<Set<String>>(emptySet())
        private set
    var clipboard by mutableStateOf<ClipboardState?>(null)
        private set
    var task by mutableStateOf<TaskState?>(null)
        private set

    var query by mutableStateOf("")
        private set
    var searchResults by mutableStateOf<List<FsEntry>?>(null)
        private set
    private var searchJob: Job? = null

    /** The superuser is used for file operations and for folders the app cannot read */
    var rootMode by mutableStateOf(false)
        private set
    var rootMounted by mutableStateOf<Boolean?>(null)
        private set

    var bookmarks by mutableStateOf(prefs.getStringSet("bookmarks", emptySet())!!.sorted())
        private set
    var volumes by mutableStateOf<List<StorageVolume>>(emptyList())
        private set

    private fun fsFor(p: String): Fs = when {
        RemotePath.isRemote(p) -> RemoteRegistry.fs(context, RemotePath.idOf(p))
        ArchivePath.isArchive(p) -> ArchiveFs.of(ArchivePath.archiveOf(p))
        de.mm20.launcher2.ui.files.vault.VaultPath.isVault(p) -> de.mm20.launcher2.ui.files.vault.CryptomatorFs.of(de.mm20.launcher2.ui.files.vault.VaultPath.vaultOf(p))
        rootMode -> root
        else -> local
    }

    private val fs: Fs get() = fsFor(path ?: "/")

    /** The saved network and cloud storages */
    var connections by mutableStateOf<List<RemoteConnection>>(emptyList())
        private set

    fun reloadConnections() { connections = ConnectionStore(context).all().sortedBy { it.name.lowercase() } }

    fun connectionName(p: String): String? =
        if (RemotePath.isRemote(p)) connections.firstOrNull { it.id == RemotePath.idOf(p) }?.name else null

    init {
        refreshVolumes()
        reloadConnections()
    }

    private fun loadSort(): SortSpec = SortSpec(
        key = runCatching { SortKey.valueOf(prefs.getString("sortKey", "Name")!!) }.getOrDefault(SortKey.Name),
        ascending = prefs.getBoolean("sortAsc", true),
        foldersFirst = prefs.getBoolean("foldersFirst", true),
    )

    fun refreshVolumes() {
        viewModelScope.launch(Dispatchers.IO) {
            val found = mutableListOf<StorageVolume>()
            fun add(name: String, dir: File, removable: Boolean) {
                runCatching {
                    val stat = StatFs(dir.path)
                    found += StorageVolume(name, dir.path, stat.totalBytes, stat.availableBytes, removable)
                }
            }
            add("Internal storage", Environment.getExternalStorageDirectory(), false)
            // other volumes show up as app folders: .../Android/data/<package>/files
            context.getExternalFilesDirs(null).drop(1).filterNotNull().forEach { d ->
                val volume = File(d.path.substringBefore("/Android"))
                if (volume.exists()) add(volume.name.let { "SD card / USB ($it)" }, volume, true)
            }
            withContext(Dispatchers.Main) { volumes = found }
        }
    }

    // ---- browsing ----

    fun open(newPath: String?) {
        selection = emptySet()
        searchResults = null
        query = ""
        path = newPath
        error = null
        if (newPath == null) { entries = emptyList(); return }
        load(newPath)
    }

    fun reload() { path?.let { load(it) } }

    private fun load(dir: String) {
        loading = true
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { fs.list(dir) } }
            if (path != dir) return@launch
            loading = false
            result.onSuccess { entries = it; error = null }
                .onFailure { entries = emptyList(); error = it.message ?: "Cannot open this folder" }
        }
    }

    /** One step up. Returns false when already at the top (the home page). */
    fun up(): Boolean {
        if (searchResults != null || query.isNotEmpty()) { clearSearch(); return true }
        if (selection.isNotEmpty()) { selection = emptySet(); return true }
        val current = path ?: return false
        val isVolumeRoot = !rootMode && volumes.any { it.path == current }
        val parent = when {
            RemotePath.isRemote(current) -> if (RemotePath.isRoot(current)) null else parentOf(current)
            ArchivePath.isArchive(current) -> if (ArchivePath.isRoot(current)) parentOf(ArchivePath.archiveOf(current)) else ArchivePath.build(ArchivePath.archiveOf(current), parentOf(ArchivePath.innerOf(current)) ?: "/")
            de.mm20.launcher2.ui.files.vault.VaultPath.isVault(current) -> if (de.mm20.launcher2.ui.files.vault.VaultPath.isRoot(current)) parentOf(de.mm20.launcher2.ui.files.vault.VaultPath.vaultOf(current)) else de.mm20.launcher2.ui.files.vault.VaultPath.build(de.mm20.launcher2.ui.files.vault.VaultPath.vaultOf(current), parentOf(de.mm20.launcher2.ui.files.vault.VaultPath.innerOf(current)) ?: "/")
            isVolumeRoot -> null
            else -> parentOf(current)
        }
        open(parent)
        return true
    }

    val visible: List<FsEntry>
        get() = (searchResults ?: entries).let { list -> if (showHidden) list else list.filterNot { it.hidden } }.sorted(sort)

    // ---- view options ----

    fun updateSort(spec: SortSpec) {
        sort = spec
        prefs.edit().putString("sortKey", spec.key.name).putBoolean("sortAsc", spec.ascending).putBoolean("foldersFirst", spec.foldersFirst).apply()
    }

    fun toggleGrid() { grid = !grid; prefs.edit().putBoolean("grid", grid).apply() }
    fun toggleHidden() { showHidden = !showHidden; prefs.edit().putBoolean("hidden", showHidden).apply() }

    // ---- selection ----

    fun toggleSelected(entry: FsEntry) {
        selection = if (entry.path in selection) selection - entry.path else selection + entry.path
    }

    fun selectAll() { selection = visible.map { it.path }.toSet() }
    fun clearSelection() { selection = emptySet() }
    fun selectedEntries(): List<FsEntry> = (searchResults ?: entries).filter { it.path in selection }

    // ---- search ----

    fun search(text: String) {
        query = text
        searchJob?.cancel()
        val dir = path
        if (text.isBlank() || dir == null) { searchResults = null; return }
        if (RemotePath.isRemote(dir)) {
            // a server cannot be searched quickly: filter what is shown
            searchResults = entries.filter { GreekFold.contains(it.name, text) }
            return
        }
        searchJob = viewModelScope.launch {
            val found = withContext(Dispatchers.IO) {
                if (rootMode) {
                    RootShell.run("find ${RootShell.q(dir)} -iname ${RootShell.q("*$text*")} 2>/dev/null | head -300").out.lines()
                        .filter { it.isNotBlank() }
                        .map { FsEntry(it, nameOf(it), File(it).isDirectory, -1, 0) }
                } else {
                    File(dir).walkTopDown().onEnter { true }.filter { GreekFold.contains(it.name, text) && it.path != dir }.take(300)
                        .map { FsEntry(it.path, it.name, it.isDirectory, if (it.isDirectory) -1 else it.length(), it.lastModified()) }.toList()
                }
            }
            if (query == text) searchResults = found
        }
    }

    fun clearSearch() { query = ""; searchResults = null; searchJob?.cancel() }

    // ---- bookmarks ----

    fun isBookmarked(p: String) = p in bookmarks
    fun toggleBookmark(p: String) {
        val next = if (p in bookmarks) bookmarks - p else bookmarks + p
        bookmarks = next.sorted()
        prefs.edit().putStringSet("bookmarks", next.toSet()).apply()
    }

    // ---- root ----

    /** Asks for the superuser. Returns through [onResult] on the main thread. */
    fun enableRoot(onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { RootShell.available(force = true) }
            rootMode = ok
            if (!ok) message = "Root access was not granted"
            onResult(ok)
            path?.let { load(it) }
        }
    }

    fun disableRoot() { rootMode = false; rootMounted = null; path?.let { load(it) } }

    fun remount(mountPoint: String, writable: Boolean) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { root.remount(mountPoint, writable) }
            rootMounted = if (ok) writable else rootMounted
            message = if (ok) "$mountPoint is now ${if (writable) "writable" else "read-only"}" else "Could not remount $mountPoint"
        }
    }

    fun chmod(entry: FsEntry, mode: String) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { root.chmod(entry.path, mode) }
            message = if (ok) "Permissions changed" else "Could not change the permissions"
            reload()
        }
    }

    // ---- operations ----

    private fun runTask(title: String, total: Long?, block: (CancelFlag, (Long) -> Unit) -> String) {
        val cancel = CancelFlag()
        task = TaskState(title, if (total != null) 0f else null, cancel)
        viewModelScope.launch {
            val done = java.util.concurrent.atomic.AtomicLong()
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    block(cancel) { bytes ->
                        val sum = done.addAndGet(bytes)
                        if (total != null && total > 0) task = task?.copy(progress = (sum.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            task = null
            message = result.getOrElse { if (cancel.cancelled) "Cancelled" else it.message ?: "Failed" }
            selection = emptySet()
            reload()
        }
    }

    fun newFolder(name: String) {
        val dir = path ?: return
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { fs.mkdir(dir, fs.freeName(dir, name)) }
            message = if (ok) null else "Could not create the folder"
            reload()
        }
    }

    fun newFile(name: String) {
        val dir = path ?: return
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { fs.createFile(dir, fs.freeName(dir, name)) }
            message = if (ok) null else "Could not create the file"
            reload()
        }
    }

    fun rename(entry: FsEntry, newName: String) {
        val dir = parentOf(entry.path) ?: return
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { newName.isNotBlank() && !fs.exists(joinPath(dir, newName)) && fs.rename(entry.path, newName) }
            message = if (ok) null else "Could not rename (does the name exist already?)"
            selection = emptySet()
            reload()
        }
    }

    fun delete(items: List<FsEntry>) {
        runTask("Deleting", null) { _, _ ->
            val failed = items.count { !fs.delete(it.path) }
            if (failed == 0) "Deleted" else "$failed could not be deleted"
        }
    }

    fun copyToClipboard(cut: Boolean) {
        clipboard = ClipboardState(selectedEntries().map { it.path }, cut, rootMode)
        selection = emptySet()
        message = if (cut) "Cut. Open the target folder and paste." else "Copied. Open the target folder and paste."
    }

    fun clearClipboard() { clipboard = null }

    fun paste() {
        val clip = clipboard ?: return
        val dir = path ?: return
        val dstFs = fsFor(dir)
        val srcFs = fsFor(clip.paths.first())
        val bothLocal = !dstFs.isRemote && !srcFs.isRemote && !(rootMode || clip.rootMode)
        val total = if (bothLocal) clip.paths.sumOf { FsOps.sizeOf(File(it)) } else null
        val infoByPath = (searchResults ?: entries).associateBy { it.path }
        runTask(if (clip.cut) "Moving" else "Copying", total) { cancel, progress ->
            var failed = 0
            for (src in clip.paths) {
                if (cancel.cancelled) break
                if (dir == src || dir.startsWith(src.trimEnd('/') + "/")) { failed++; continue } // not into itself
                val name = dstFs.freeName(dir, nameOf(src))
                val ok = when {
                    bothLocal -> if (clip.cut && File(src).renameTo(File(dir, name))) true else runCatching {
                        FsOps.copyTree(File(src), File(dir, name), cancel, progress)
                        if (clip.cut) File(src).deleteRecursively()
                        true
                    }.getOrDefault(false)
                    // the same machine can copy or move by itself
                    srcFs === dstFs || (srcFs.isRoot && dstFs.isRoot) || (srcFs.isRemote && dstFs.isRemote && RemotePath.idOf(src) == RemotePath.idOf(dir)) -> {
                        val native = if (clip.cut) dstFs.move(src, dir, name) else dstFs.copy(src, dir, name)
                        native || runCatching { across(srcFs, src, infoByPath[src]?.isDir, dstFs, joinPath(dir, name), cancel, progress, clip.cut) }.getOrDefault(false)
                    }
                    else -> runCatching { across(srcFs, src, infoByPath[src]?.isDir, dstFs, joinPath(dir, name), cancel, progress, clip.cut) }.getOrDefault(false)
                }
                if (!ok) failed++
            }
            if (clip.cut) clipboard = null
            if (failed == 0) "Done" else "$failed could not be ${if (clip.cut) "moved" else "copied"}"
        }
    }

    /** Copies through the phone, and removes the original for a move. */
    private fun across(src: Fs, srcPath: String, isDir: Boolean?, dst: Fs, dstPath: String, cancel: CancelFlag, progress: (Long) -> Unit, move: Boolean): Boolean {
        val dir = isDir ?: runCatching { src.list(srcPath); true }.getOrDefault(false)
        FsOps.copyAcross(src, srcPath, dir, dst, dstPath, cancel, progress)
        if (move) src.delete(srcPath)
        return true
    }

    /** Unlocks a Cryptomator vault (the key derivation is slow on purpose, so it runs off the main thread) and opens it. */
    fun unlockVault(vault: String, password: String, onError: (String) -> Unit, onDone: () -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { de.mm20.launcher2.ui.files.vault.VaultSessions.unlock(vault, password) } }
            result.onSuccess { onDone(); open(de.mm20.launcher2.ui.files.vault.VaultPath.build(vault, "/")) }.onFailure {
                onError(if (it is de.mm20.launcher2.ui.files.vault.WrongPasswordException) "Wrong password" else it.message ?: "Could not open the vault")
            }
        }
    }

    /** Forgets the key and removes the decrypted files that were opened from the vault. */
    fun lockVault(vault: String) {
        de.mm20.launcher2.ui.files.vault.VaultSessions.lock(vault)
        File(context.cacheDir, "remote_open").deleteRecursively()
        open(parentOf(vault))
    }

    /** Fetches a file from a remote storage into the cache, then calls [then] on the main thread with the local file. */
    fun download(entry: FsEntry, then: (File) -> Unit) {
        val target = File(File(context.cacheDir, "remote_open").apply { mkdirs() }, entry.name)
        val cancel = CancelFlag()
        task = TaskState("Downloading", if (entry.size > 0) 0f else null, cancel)
        viewModelScope.launch {
            val done = java.util.concurrent.atomic.AtomicLong()
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    fsFor(entry.path).openRead(entry.path).use { input ->
                        target.outputStream().use { output ->
                            val buffer = ByteArray(128 * 1024)
                            while (true) {
                                if (cancel.cancelled) throw java.io.IOException("Cancelled")
                                val n = input.read(buffer)
                                if (n < 0) break
                                output.write(buffer, 0, n)
                                val sum = done.addAndGet(n.toLong())
                                if (entry.size > 0) task = task?.copy(progress = (sum.toFloat() / entry.size).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
            }
            task = null
            result.onSuccess { then(target) }.onFailure { message = if (cancel.cancelled) "Cancelled" else it.message ?: "Download failed" }
        }
    }

    fun compress(items: List<FsEntry>, zipName: String) {
        val dir = path ?: return
        val files = items.map { File(it.path) }
        val total = files.sumOf { FsOps.sizeOf(it) }
        runTask("Compressing", total) { cancel, progress ->
            val target = File(dir, local.freeName(dir, if (zipName.endsWith(".zip")) zipName else "$zipName.zip"))
            try {
                FsOps.zip(files, target, cancel, progress)
            } catch (e: Exception) {
                target.delete(); throw e
            }
            "Created ${target.name}"
        }
    }

    /** Unpacks an archive into a folder next to it (zip, 7z, tar, ...). */
    fun extract(entry: FsEntry) {
        val dir = path ?: return
        val archiveFs = ArchiveFs(File(entry.path))
        runTask("Extracting", null) { cancel, progress ->
            val target = File(dir, local.freeName(dir, entry.name.substringBefore('.')))
            FsOps.copyAcross(archiveFs, ArchivePath.build(entry.path, "/"), true, local, target.path, cancel, progress)
            "Extracted to ${target.name}"
        }
    }

    suspend fun sizeOf(entry: FsEntry): Long = withContext(Dispatchers.IO) { fs.totalSize(entry.path) }
    suspend fun checksum(entry: FsEntry, algorithm: String): String = withContext(Dispatchers.IO) { runCatching { FsOps.hash(File(entry.path), algorithm) }.getOrDefault("") }
    suspend fun childCount(entry: FsEntry): Int = withContext(Dispatchers.IO) { runCatching { fs.list(entry.path).size }.getOrDefault(-1) }
}
