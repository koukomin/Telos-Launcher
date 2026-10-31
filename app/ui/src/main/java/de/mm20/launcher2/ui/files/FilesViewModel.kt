package de.mm20.launcher2.ui.files
import de.mm20.launcher2.i18n.R as I18nR

import de.mm20.launcher2.comms.search.TelosSearch
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.withContext
import de.mm20.launcher2.helper.ArchiveFormats
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.files.remote.ConnectionStore
import de.mm20.launcher2.ui.files.remote.RemoteConnection
import de.mm20.launcher2.ui.files.remote.RemotePath
import de.mm20.launcher2.ui.files.remote.RemoteRegistry
import de.mm20.launcher2.ui.files.remote.RemoteSpace
import java.io.File

/** A storage volume: the phone's own storage, an SD card, a USB drive. */
data class StorageVolume(val name: String, val path: String, val total: Long, val free: Long, val removable: Boolean)

data class ClipboardState(val paths: List<String>, val cut: Boolean, val rootMode: Boolean, val shizukuMode: Boolean = false)

class FilesClipboard { var value by mutableStateOf<ClipboardState?>(null) }

data class TaskState(val title: String, val progress: Float?, val cancel: CancelFlag)

/** An archive that needs a password; [retry] runs again once the password was accepted. [browsing] is true when the user is looking inside it. */
data class ArchivePrompt(val archive: String, val browsing: Boolean, val retry: () -> Unit)

class FilesViewModel(application: Application) : AndroidViewModel(application) {
    private val context: Context get() = getApplication()
    private fun s(id: Int, vararg args: Any): String = context.getString(id, *args)

    /** The message for a RAR that cannot be read (encrypted, or no RAR reader in this build), or null for any other error */
    private fun rarMessage(t: Throwable): String? = when (t) {
        is ArchiveRarEncryptedException -> s(I18nR.string.au23_rar_encrypted)
        is ArchiveRarUnavailableException -> s(I18nR.string.au23_rar_unavailable)
        else -> null
    }
    private val prefs = context.getSharedPreferences("telos_files", Context.MODE_PRIVATE)
    private val local = LocalFs()
    private val suRoot = RootFs()
    private val shizukuRoot = RootFs(shizuku = true)
    private val root: RootFs get() = if (shizukuMode) shizukuRoot else suRoot

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
    /** 0 = list, 1 = grid with medium previews, 2 = grid with large previews; kept for all folders */
    var viewMode by mutableStateOf(prefs.getInt("viewMode", if (prefs.getBoolean("grid", false)) 1 else 0).coerceIn(0, 2))
        private set
    val grid: Boolean get() = viewMode != 0
    var showHidden by mutableStateOf(prefs.getBoolean("hidden", false))
        private set

    var selection by mutableStateOf<Set<String>>(emptySet())
        private set
    internal var onFilesChanged: () -> Unit = {}
    internal var onLocationChanged: () -> Unit = {}
    val transferQueue = FilesTransferQueue.get(application)
    var sharedClipboard = FilesClipboard()
    var clipboard: ClipboardState?
        get() = sharedClipboard.value
        private set(value) { sharedClipboard.value = value }
    var task by mutableStateOf<TaskState?>(null)
        private set

    var query by mutableStateOf("")
        private set
    var searchResults by mutableStateOf<List<FsEntry>?>(null)
        private set
    private var searchJob: Job? = null
    var searchFilter by mutableStateOf(de.mm20.launcher2.helper.FileSearchFilter())
        private set
    var searchKind by mutableStateOf<FileKind?>(null)
        private set
    var recursiveSearch by mutableStateOf(true)
    /** Search every storage volume (internal, SD, USB) instead of the current folder */
    var searchEverywhere by mutableStateOf(false)
        private set
    var searchLimited by mutableStateOf(false)
        private set
    private var searchRevision = 0L

    /** The superuser is used for file operations and for folders the app cannot read */
    var rootMode by mutableStateOf(false)
        private set
    var rootMounted by mutableStateOf<Boolean?>(null)
        private set

    /** Set while an archive waits for its password: the screen shows the password dialog. */
    var archivePrompt by mutableStateOf<ArchivePrompt?>(null)
        private set
    // archives whose password dialog was cancelled: they are not asked again while the user stays inside
    private val promptDeclined = mutableSetOf<String>()

    var bookmarks by mutableStateOf(prefs.getStringSet("bookmarks", emptySet())!!.sorted())
        private set
    var volumes by mutableStateOf<List<StorageVolume>>(emptyList())
        private set
    var remoteSpaces by mutableStateOf<Map<String, RemoteSpace>>(emptyMap())
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

    fun reloadConnections() {
        connections = ConnectionStore(context).all().sortedBy { it.name.lowercase() }
        refreshRemoteSpaces()
    }

    fun connectionName(p: String): String? =
        if (RemotePath.isRemote(p)) connections.firstOrNull { it.id == RemotePath.idOf(p) }?.name else null

    init {
        refreshVolumes()
        reloadConnections()
    }

    override fun onCleared() {
        task?.cancel?.cancelled = true
        searchJob?.cancel()
        super.onCleared()
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
            add(s(I18nR.string.au_files_internal_storage), Environment.getExternalStorageDirectory(), false)
            // other volumes show up as app folders: .../Android/data/<package>/files
            context.getExternalFilesDirs(null).drop(1).filterNotNull().forEach { d ->
                val volume = File(d.path.substringBefore("/Android"))
                if (volume.exists()) add(s(I18nR.string.au_files_sd_usb, volume.name), volume, true)
            }
            withContext(Dispatchers.Main) { volumes = found }
        }
        refreshRemoteSpaces()
    }

    fun refreshRemoteSpaces() {
        val ids = connections.map { it.id }
        viewModelScope.launch(Dispatchers.IO) {
            val map = HashMap<String, RemoteSpace>()
            ids.forEach { id ->
                runCatching {
                    RemoteRegistry.client(context, id).space()?.let { map[id] = it }
                }
            }
            withContext(Dispatchers.Main) { remoteSpaces = map }
        }
    }

    // ---- browsing ----

    /** Forgets the password of an archive the user is leaving */
    internal var archiveInUseElsewhere: (String) -> Boolean = { false }

    private fun leaveArchive(oldPath: String?, newPath: String?) {
        if (oldPath == null || !ArchivePath.isArchive(oldPath)) return
        val archive = ArchivePath.archiveOf(oldPath)
        if (newPath != null && ArchivePath.isArchive(newPath) && ArchivePath.archiveOf(newPath) == archive) return
        if (archiveInUseElsewhere(archive)) return
        ArchiveSessions.clear(archive)
        promptDeclined.remove(archive)
        if (archivePrompt?.archive == archive && archivePrompt?.browsing == true) archivePrompt = null
    }

    fun open(newPath: String?) {
        leaveArchive(path, newPath)
        searchJob?.cancel()
        selection = emptySet()
        searchResults = null
        query = ""
        path = newPath
        onLocationChanged()
        restoreFolderOptions(newPath)
        error = null
        if (newPath == null) { entries = emptyList(); return }
        load(newPath)
    }

    fun reloadPreferences() {
        bookmarks = prefs.getStringSet("bookmarks", emptySet()).orEmpty().sorted()
        restoreFolderOptions(path)
        reload()
    }

    fun reload() { path?.let { folder ->
        if (!RemotePath.isRemote(folder) && !ArchivePath.isArchive(folder) && !de.mm20.launcher2.ui.files.vault.VaultPath.isVault(folder))
            runCatching { de.mm20.launcher2.helper.FileSearchIndex.invalidate(folder) }
        load(folder)
    } }

    private fun load(dir: String) {
        loading = true
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { fsFor(dir).list(dir) } }
            if (path != dir) return@launch
            loading = false
            result.onSuccess {
                entries = it; error = null
                if (searchResults != null || query.isNotBlank()) search(query)
                if (ArchivePath.isArchive(dir)) askIfEncrypted(ArchivePath.archiveOf(dir))
            }.onFailure {
                entries = emptyList()
                if (it is ArchiveCryptoException && ArchivePath.isArchive(dir)) {
                    // a 7z with encrypted file names cannot even be listed without its password
                    error = s(I18nR.string.au21_arch_password_needed)
                    archivePrompt = ArchivePrompt(ArchivePath.archiveOf(dir), true) { load(dir) }
                } else {
                    error = rarMessage(it) ?: it.message?.takeIf { m -> m != "Cannot open this folder" } ?: s(I18nR.string.au_files_cannot_open_folder)
                }
            }
        }
    }

    /** Asks for the password when the archive that was just opened has encrypted files and none is known yet */
    private fun askIfEncrypted(archive: String) {
        if (ArchiveSessions.has(archive) || archive in promptDeclined || archivePrompt != null) return
        viewModelScope.launch {
            val encrypted = withContext(Dispatchers.IO) { runCatching { ArchiveFs.of(archive).hasEncrypted }.getOrDefault(false) }
            val current = path
            if (encrypted && archivePrompt == null && !ArchiveSessions.has(archive) && current != null && ArchivePath.isArchive(current) && ArchivePath.archiveOf(current) == archive) {
                archivePrompt = ArchivePrompt(archive, true) { }
            }
        }
    }

    /** Tries [password] on the archive of the open prompt. On success it is kept in memory for this archive only and the waiting action continues. */
    fun unlockArchive(password: CharArray, onError: (String) -> Unit, onDone: () -> Unit) {
        val prompt = archivePrompt ?: return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { ArchiveFs.checkPassword(File(prompt.archive), password) } }
            result.onSuccess {
                ArchiveSessions.set(prompt.archive, password)
                archivePrompt = null
                onDone()
                prompt.retry()
            }.onFailure {
                onError(when (it) {
                    is ArchiveWrongPasswordException -> s(I18nR.string.au21_arch_wrong_password)
                    is ArchivePasswordRequiredException -> s(I18nR.string.au21_arch_password_needed)
                    else -> it.message ?: s(I18nR.string.au21_arch_failed)
                })
            }
        }
    }

    fun cancelArchivePrompt() {
        val prompt = archivePrompt ?: return
        if (prompt.browsing) promptDeclined += prompt.archive
        archivePrompt = null
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

    private fun folderKey(folder: String?) = folder?.let { "folder:" + java.security.MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") { b -> "%02x".format(b) } }

    private fun restoreFolderOptions(folder: String?) {
        val key = folderKey(folder)
        val stored = key?.let { prefs.getString(it, null) }
        if (stored == null) {
            sort = loadSort()
            viewMode = prefs.getInt("viewMode", if (prefs.getBoolean("grid", false)) 1 else 0).coerceIn(0, 2)
            showHidden = prefs.getBoolean("hidden", false)
            return
        }
        runCatching {
            val json = org.json.JSONObject(stored)
            sort = SortSpec(SortKey.valueOf(json.getString("sort")), json.getBoolean("ascending"), json.getBoolean("foldersFirst"))
            viewMode = json.getInt("view").coerceIn(0, 2)
            showHidden = json.getBoolean("hidden")
        }
    }

    private fun saveFolderOptions() {
        val key = folderKey(path) ?: return
        prefs.edit().putString(key, org.json.JSONObject().put("sort", sort.key.name).put("ascending", sort.ascending)
            .put("foldersFirst", sort.foldersFirst).put("view", viewMode).put("hidden", showHidden).toString()).apply()
    }

    fun updateSort(spec: SortSpec) {
        sort = spec
        if (path == null) prefs.edit().putString("sortKey", spec.key.name).putBoolean("sortAsc", spec.ascending).putBoolean("foldersFirst", spec.foldersFirst).apply()
        saveFolderOptions()
    }

    fun toggleGrid() = updateViewMode(if (viewMode == 0) 1 else 0)
    fun updateViewMode(mode: Int) {
        viewMode = mode.coerceIn(0, 2)
        if (path == null) prefs.edit().putInt("viewMode", viewMode).putBoolean("grid", viewMode != 0).apply()
        saveFolderOptions()
    }
    fun toggleHidden() {
        showHidden = !showHidden; if (path == null) prefs.edit().putBoolean("hidden", showHidden).apply(); saveFolderOptions()
        if (searchResults != null || query.isNotBlank()) search(query)
    }

    // ---- selection ----

    fun toggleSelected(entry: FsEntry) {
        selection = if (entry.path in selection) selection - entry.path else selection + entry.path
    }

    fun selectAll() { selection = visible.map { it.path }.toSet() }
    fun clearSelection() { selection = emptySet() }
    fun selectedEntries(): List<FsEntry> = (searchResults ?: entries).filter { it.path in selection }

    // ---- search ----

    fun search(text: String) {
        query = text; searchJob?.cancel()
        val revision = ++searchRevision
        val dir = path; searchLimited = false
        val everywhere = searchEverywhere && !rootMode && volumes.isNotEmpty() &&
            (dir == null || !(RemotePath.isRemote(dir) || ArchivePath.isArchive(dir) || de.mm20.launcher2.ui.files.vault.VaultPath.isVault(dir)))
        if ((dir == null && !everywhere) || text.isBlank() && searchFilter == de.mm20.launcher2.helper.FileSearchFilter() && searchKind == null) { searchResults = null; return }
        val filter = searchFilter; val kind = searchKind
        val matcher = TelosSearch.compile(text)
        fun matches(entry: FsEntry, hidden: Boolean = entry.hidden): Boolean =
            (text.isBlank() || matcher.matches(entry.name)) && (kind == null || entry.kind == kind) &&
                (showHidden || !hidden) && filter.matches(de.mm20.launcher2.helper.IndexedFile(entry.path, entry.name, entry.isDir, entry.size, entry.modified, hidden, entry.isLink))
        if (dir != null && !everywhere && ((RemotePath.isRemote(dir) && !recursiveSearch) || ArchivePath.isArchive(dir) || de.mm20.launcher2.ui.files.vault.VaultPath.isVault(dir))) {
            searchResults = entries.filter { matches(it) }; return
        }
        searchJob = viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val job = coroutineContext
                    if (everywhere) {
                        var limited = false
                        // every volume is scanned and gets a fair share of the 300 results
                        val perVolume = volumes.map { volume ->
                            val snapshot = de.mm20.launcher2.helper.FileSearchIndex.read(File(volume.path)) { job.ensureActive() }
                            if (snapshot.limited || snapshot.unreadable > 0) limited = true
                            snapshot.files.asSequence().map { file ->
                                job.ensureActive()
                                FsEntry(file.path, file.name, file.directory, file.size, file.modified, isLink = file.symbolicLink) to file.hidden
                            }.filter { (entry, hidden) -> matches(entry, hidden) }.take(301).map { it.first }.toList()
                        }
                        val files = ArrayList<FsEntry>(300)
                        var round = 0
                        while (files.size < 300 && perVolume.any { round < it.size }) {
                            perVolume.forEach { list -> if (round < list.size && files.size < 300) files += list[round] }
                            round++
                        }
                        files to (limited || perVolume.sumOf { it.size } > 300)
                    } else if (RemotePath.isRemote(dir!!)) {
                        val scan = FilesRemoteSearch.scan(fsFor(dir), dir, showHidden, { matches(it) })
                        scan.entries to scan.partial
                    } else if (rootMode) {
                        val files = RootShell.run("find ${RootShell.q(dir)} -mindepth 1 2>/dev/null | head -20000", shizuku = shizukuMode).out.lines()
                            .filter { it.isNotBlank() && (text.isBlank() || matcher.matches(nameOf(it))) }.take(300)
                            .map { FsEntry(it, nameOf(it), File(it).isDirectory, -1, 0) }.filter { matches(it) }
                        files to (files.size >= 300)
                    } else if (!recursiveSearch) {
                        val files = entries.filter { matches(it) }; files.take(300) to (files.size > 300)
                    } else {
                        val snapshot = de.mm20.launcher2.helper.FileSearchIndex.read(File(dir)) { job.ensureActive() }
                        val files = snapshot.files.asSequence().map { file ->
                            job.ensureActive()
                            FsEntry(file.path, file.name, file.directory, file.size, file.modified, isLink = file.symbolicLink) to file.hidden
                        }.filter { (entry, hidden) -> matches(entry, hidden) }.take(301).map { it.first }.toList()
                        files.take(300) to (snapshot.limited || snapshot.unreadable > 0 || files.size > 300)
                    }
                }
                if (searchRevision == revision) { searchResults = result.first; searchLimited = result.second }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                if (searchRevision == revision) { searchResults = emptyList(); searchLimited = false; message = s(I18nR.string.au_files_cannot_open_folder) }
            }
        }
    }
    fun updateSearchEverywhere(enabled: Boolean) { searchEverywhere = enabled }

    fun setSearchOptions(filter: de.mm20.launcher2.helper.FileSearchFilter, kind: FileKind?, recursive: Boolean) {
        searchFilter = filter; searchKind = kind; recursiveSearch = recursive; search(query)
    }
    fun clearSearch() { query = ""; searchResults = null; searchJob?.cancel(); searchRevision++; searchLimited = false }

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
        shizukuRequest?.cancel()
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { RootShell.available(force = true) }
            rootMode = ok; shizukuMode = false
            if (!ok) message = s(I18nR.string.au_files_root_denied)
            onResult(ok)
            path?.let { load(it) }
        }
    }

    fun disableRoot() { shizukuRequest?.cancel(); rootMode = false; shizukuMode = false; rootMounted = null; path?.let { load(it) } }

    /** Root mode that runs through Shizuku instead of the superuser */
    var shizukuMode by mutableStateOf(false)
        private set

    private var shizukuRequest: Job? = null

    fun enableShizuku() {
        if (shizukuRequest?.isActive == true) return
        shizukuRequest = viewModelScope.launch {
            val permitted = ShizukuShell.requestAccess()
            val ok = permitted && withContext(Dispatchers.IO) { RootShell.available(force = true, shizuku = true) }
            rootMode = ok; shizukuMode = ok
            if (!ok) message = s(I18nR.string.telos_files_shizuku_denied)
            path?.let { load(it) }
        }
    }

    fun remount(mountPoint: String, writable: Boolean) {
        if (!rootMode || shizukuMode) return
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { root.remount(mountPoint, writable) }
            rootMounted = if (ok) writable else rootMounted
            message = if (ok) s(if (writable) I18nR.string.au_files_remount_rw else I18nR.string.au_files_remount_ro, mountPoint) else s(I18nR.string.au_files_remount_failed, mountPoint)
        }
    }

    fun chmod(entry: FsEntry, mode: String) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { root.chmod(entry.path, mode) }
            message = if (ok) s(I18nR.string.au_files_chmod_ok) else s(I18nR.string.au_files_chmod_failed)
            reload()
            onFilesChanged()
        }
    }

    // ---- operations ----

    private fun runTask(title: String, total: Long?, estimate: ((CancelFlag) -> Long?)? = null, block: (CancelFlag, (Long) -> Unit) -> String) {
        if (task != null) { message = s(I18nR.string.telos_files_task_busy); return }
        val cancel = CancelFlag()
        task = TaskState(title, if (total != null) 0f else null, cancel)
        viewModelScope.launch {
            val done = java.util.concurrent.atomic.AtomicLong()
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val expected = total ?: estimate?.invoke(cancel)
                    block(cancel) { bytes ->
                        val sum = done.addAndGet(bytes)
                        if (expected != null && expected > 0) task = task?.copy(progress = (sum.toFloat() / expected).coerceIn(0f, 1f))
                    }
                }
            }
            task = null
            message = result.getOrElse {
                when {
                    cancel.cancelled -> s(I18nR.string.au_files_cancelled)
                    it is ArchiveWrongPasswordException -> s(I18nR.string.au21_arch_wrong_password)
                    it is ArchivePasswordRequiredException -> s(I18nR.string.au21_arch_password_needed)
                    rarMessage(it) != null -> rarMessage(it)!!
                    else -> it.message ?: s(I18nR.string.au_files_failed)
                }
            }
            selection = emptySet()
            reload()
            onFilesChanged()
        }
    }

    fun newFolder(name: String) {
        val dir = path ?: return
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { fs.mkdir(dir, fs.freeName(dir, name)) }
            message = if (ok) null else s(I18nR.string.au_files_mkdir_failed)
            reload()
            onFilesChanged()
        }
    }

    fun newFile(name: String) {
        val dir = path ?: return
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { fs.createFile(dir, fs.freeName(dir, name)) }
            message = if (ok) null else s(I18nR.string.au_files_create_failed)
            reload()
            onFilesChanged()
        }
    }

    fun batchRename(items: List<FsEntry>, names: List<String>) {
        if (items.size != names.size || items.isEmpty()) return
        val operationFs = fs
        val destinations = items.zip(names).map { (entry, name) -> joinPath(parentOf(entry.path) ?: "/", name) }
        if (destinations.map { it.lowercase() }.toSet().size != destinations.size || names.any { !isPlainName(it) } ||
            items.any { parent -> parent.isDir && items.any { child -> child.path.startsWith(parent.path.trimEnd('/') + "/") } }) {
            message = s(I18nR.string.telos_files_rename_collision); return
        }
        runTask(s(I18nR.string.telos_files_batch_rename), null) { cancel, _ ->
            val sourcePaths = items.map { it.path }.toSet()
            if (destinations.any { it !in sourcePaths && operationFs.exists(it) }) return@runTask s(I18nR.string.telos_files_rename_collision)
            // Two phases allow swaps without overwriting an existing file.
            val current = items.map { it.path }.toMutableList()
            try {
                items.indices.forEach { i ->
                    if (cancel.cancelled) throw java.io.IOException("Cancelled")
                    val temp = ".telos-rename-${java.util.UUID.randomUUID()}"
                    if (!operationFs.rename(current[i], temp)) throw java.io.IOException("Rename failed")
                    current[i] = joinPath(parentOf(items[i].path) ?: "/", temp)
                }
                items.indices.forEach { i ->
                    if (cancel.cancelled || !operationFs.rename(current[i], names[i])) throw java.io.IOException("Rename failed")
                    current[i] = destinations[i]
                }
                s(I18nR.string.telos_files_renamed, items.size)
            } catch (e: Exception) {
                // Best-effort rollback, using fresh names first so swaps cannot collide.
                val rollback = current.map { old ->
                    val name = ".telos-rollback-${java.util.UUID.randomUUID()}"
                    if (runCatching { operationFs.rename(old, name) }.getOrDefault(false)) joinPath(parentOf(old) ?: "/", name) else old
                }
                val restored = rollback.indices.map { i -> runCatching { operationFs.rename(rollback[i], items[i].name) }.getOrDefault(false) }.all { it }
                s(if (restored) I18nR.string.telos_files_rename_rolled_back else I18nR.string.telos_files_rename_partial)
            }
        }
    }

    fun rename(entry: FsEntry, newName: String) {
        val dir = parentOf(entry.path) ?: return
        viewModelScope.launch {
            // the storage ignores upper/lower case when it looks for a name: "a.jpg" -> "A.jpg" is not a clash with itself
            val onlyCase = newName != entry.name && newName.equals(entry.name, ignoreCase = true)
            val ok = withContext(Dispatchers.IO) {
                newName.isNotBlank() && isPlainName(newName) && (onlyCase || !fs.exists(joinPath(dir, newName))) && fs.rename(entry.path, newName)
            }
            message = if (ok) null else s(I18nR.string.au_files_rename_failed)
            selection = emptySet()
            reload()
            onFilesChanged()
        }
    }

    private val trash: de.mm20.launcher2.comms.files.TelosTrash by lazy {
        org.koin.core.context.GlobalContext.get().get<de.mm20.launcher2.comms.files.TelosTrash>()
    }
    fun canTrash(items: List<FsEntry>): Boolean = !rootMode && items.isNotEmpty() && items.all { trash.supports(it.path) }
    fun moveToTrash(items: List<FsEntry>) {
        if (task != null) { message = s(I18nR.string.telos_files_task_busy); return }
        val cancel = CancelFlag()
        task = TaskState(s(I18nR.string.telos_trash_move), 0f, cancel)
        viewModelScope.launch {
            var moved = 0
            try {
                for (item in items) {
                    if (cancel.cancelled) throw java.io.IOException("Cancelled")
                    trash.move(item.path)
                    moved++
                    task = task?.copy(progress = moved.toFloat() / items.size)
                }
                message = s(I18nR.string.telos_trash_moved, moved)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
              catch (_: Exception) { message = s(I18nR.string.telos_trash_move_failed) }
            finally { task = null; selection = emptySet(); reload(); onFilesChanged() }
        }
    }

    fun delete(items: List<FsEntry>) {
        runTask(s(I18nR.string.au_files_deleting), null) { cancel, _ ->
            var failed = 0
            for (item in items) {
                if (cancel.cancelled) throw java.io.IOException("Cancelled")
                if (!fs.delete(item.path)) failed++
            }
            if (failed == 0) s(I18nR.string.au_files_deleted) else s(I18nR.string.au_files_delete_failed, failed)
        }
    }

    fun copyToClipboard(cut: Boolean) {
        clipboard = ClipboardState(selectedEntries().map { it.path }, cut, rootMode, shizukuMode)
        selection = emptySet()
        message = if (cut) s(I18nR.string.au_files_cut_hint) else s(I18nR.string.au_files_copied_hint)
    }

    fun clearClipboard() { clipboard = null }

    fun paste() {
        val clip = clipboard?.takeIf { it.paths.isNotEmpty() } ?: return
        paste(clip)
    }
    internal fun transferDragged(paths: List<String>, sourceRoot: Boolean, move: Boolean, destination: String? = path, sourceShizuku: Boolean = false) {
        if (task != null || paths.isEmpty() || path == null || ArchivePath.isArchive(path!!) || de.mm20.launcher2.ui.files.vault.VaultPath.isVault(path!!)) return
        paste(ClipboardState(paths, move, sourceRoot, sourceShizuku), clearCopiedClipboard = false, destination = destination)
    }
    private fun paste(clip: ClipboardState, clearCopiedClipboard: Boolean = true, destination: String? = path) {
        val dir = destination ?: return
        val dstFs = fsFor(dir)
        val first = clip.paths.first()
        val srcFs = if (RemotePath.isRemote(first) || ArchivePath.isArchive(first) || de.mm20.launcher2.ui.files.vault.VaultPath.isVault(first)) fsFor(first) else if (clip.rootMode) (if (clip.shizukuMode) shizukuRoot else suRoot) else local
        val bothLocal = dstFs is LocalFs && srcFs is LocalFs
        if (bothLocal) {
            runCatching { transferQueue.enqueue(clip.paths, dir, clip.cut) }
                .onSuccess {
                    selection = emptySet()
                    if (clip.cut && clearCopiedClipboard) clipboard = null
                    message = s(I18nR.string.telos_files_transfer_added)
                }
                .onFailure { message = s(I18nR.string.telos_files_transfer_queue_full) }
            return
        }
        val infoByPath = (searchResults ?: entries).associateBy { it.path }
        runTask(if (clip.cut) s(I18nR.string.au_files_moving) else s(I18nR.string.au_files_copying), null) { cancel, progress ->
            var failed = 0
            for (src in clip.paths) {
                if (cancel.cancelled) throw java.io.IOException("Cancelled")
                if (dir == src || dir.startsWith(src.trimEnd('/') + "/")) { failed++; continue } // not into itself
                if (bothLocal && File(dir).canonicalFile.toPath().startsWith(File(src).canonicalFile.toPath())) { failed++; continue }
                if (clip.cut && parentOf(src)?.trimEnd('/') == dir.trimEnd('/')) continue // moved into its own folder: nothing to do
                val name = dstFs.freeName(dir, nameOf(src))
                val ok = when {
                    // the same machine can copy or move by itself
                    srcFs === dstFs || (srcFs.isRoot && dstFs.isRoot) || (srcFs.isRemote && dstFs.isRemote && RemotePath.idOf(src) == RemotePath.idOf(dir)) -> {
                        val native = if (clip.cut) dstFs.move(src, dir, name) else dstFs.copy(src, dir, name)
                        native || runCatching { across(srcFs, src, infoByPath[src]?.isDir, dstFs, joinPath(dir, name), cancel, progress, clip.cut) }.getOrDefault(false)
                    }
                    else -> runCatching { across(srcFs, src, infoByPath[src]?.isDir, dstFs, joinPath(dir, name), cancel, progress, clip.cut) }.getOrDefault(false)
                }
                if (!ok) failed++
            }
            if (clip.cut && clearCopiedClipboard && failed == 0) clipboard = null
            if (failed == 0) s(I18nR.string.au_files_done) else if (clip.cut) s(I18nR.string.au_files_move_failed, failed) else s(I18nR.string.au_files_copy_failed, failed)
        }
    }

    /** Copies through the phone, and removes the original for a move. */
    private fun across(src: Fs, srcPath: String, isDir: Boolean?, dst: Fs, dstPath: String, cancel: CancelFlag, progress: (Long) -> Unit, move: Boolean): Boolean {
        val dir = isDir ?: runCatching { src.list(srcPath); true }.getOrDefault(false)
        FsOps.copyAcross(src, srcPath, dir, dst, dstPath, cancel, progress)
        // the copy is there; a move that could not remove the original is reported, not shown as done
        // Remote backends cannot prove a whole live directory is still the copied snapshot.
        // Preserve originals instead of recursively deleting files added during the copy.
        return !move // Unverified remote originals are retained; native server moves are attempted above.
    }

    /** Unlocks a Cryptomator vault (the key derivation is slow on purpose, so it runs off the main thread) and opens it. */
    fun unlockVault(vault: String, password: String, onError: (String) -> Unit, onDone: () -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { de.mm20.launcher2.ui.files.vault.VaultSessions.unlock(vault, password) } }
            result.onSuccess { onDone(); open(de.mm20.launcher2.ui.files.vault.VaultPath.build(vault, "/")) }.onFailure {
                onError(if (it is de.mm20.launcher2.ui.files.vault.WrongPasswordException) s(I18nR.string.au_files_wrong_password) else it.message ?: s(I18nR.string.au_files_vault_failed))
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
        // the name comes from a server: only its last part is used, so it cannot point out of the folder
        val target = File(File(context.cacheDir, "remote_open").apply { mkdirs() }, entry.name.substringAfterLast('/').takeIf { isPlainName(it) } ?: "file")
        val cancel = CancelFlag()
        task = TaskState(s(I18nR.string.au_files_downloading), if (entry.size > 0) 0f else null, cancel)
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
            result.onSuccess { then(target) }.onFailure {
                target.delete()
                if (it is ArchiveCryptoException && ArchivePath.isArchive(entry.path)) {
                    // an encrypted file in an archive: ask for the password, then fetch it again
                    archivePrompt = ArchivePrompt(ArchivePath.archiveOf(entry.path), false) { download(entry, then) }
                } else {
                    message = if (cancel.cancelled) s(I18nR.string.au_files_cancelled) else rarMessage(it) ?: it.message ?: s(I18nR.string.au_files_download_failed)
                }
            }
        }
    }

    /** What of [entries] can be shared: files only, and never anything from a vault (those stay behind the unlock) */
    fun shareable(entries: List<FsEntry>): List<FsEntry> =
        entries.filter { !it.isDir && !de.mm20.launcher2.ui.files.vault.VaultPath.isVault(it.path) }

    /** Bytes that have to be fetched from a network or cloud storage (or an archive) before [entries] can be shared */
    fun shareDownloadBytes(entries: List<FsEntry>): Long =
        shareable(entries).filter { RemotePath.isRemote(it.path) || ArchivePath.isArchive(it.path) }.sumOf { it.size.coerceAtLeast(0L) }

    /**
     * Shares [entries]: local files straight away, files on network/cloud storages (and in archives) are
     * downloaded into the cache first (with the progress bar), then handed out through the FileProvider.
     */
    fun shareEntries(entries: List<FsEntry>) {
        val files = shareable(entries)
        if (files.isEmpty()) {
            message = s(I18nR.string.au9_share_nothing)
            return
        }
        val local = files.filter { !RemotePath.isRemote(it.path) && !ArchivePath.isArchive(it.path) }
        val remote = files.filter { RemotePath.isRemote(it.path) || ArchivePath.isArchive(it.path) }
        if (remote.isEmpty()) {
            val root = rootMode; val adb = shizukuMode
            viewModelScope.launch { FileActions.share(context, local, root, adb) }
            return
        }
        val folder = File(File(context.cacheDir, "share_dl"), System.nanoTime().toString())
        fun step(i: Int, done: List<FsEntry>) {
            if (i >= remote.size) {
                val root = rootMode; val adb = shizukuMode
                viewModelScope.launch { FileActions.share(context, local + done, root, adb) }
                return
            }
            download(remote[i]) { f ->
                // every file gets its own folder, so two files with one name cannot overwrite each other
                val target = File(File(folder, i.toString()).apply { mkdirs() }, f.name)
                val moved = if (f.renameTo(target)) target else f
                step(i + 1, done + FsEntry(moved.path, moved.name, false, moved.length(), moved.lastModified()))
            }
        }
        step(0, emptyList())
    }

    /**
     * Packs [items] into a new archive next to them. [password] (zip only) encrypts the files with AES-256;
     * it is used for this one task and not kept.
     */
    fun compress(items: List<FsEntry>, zipName: String, format: CompressFormat = CompressFormat.Zip, password: CharArray? = null, splitBytes: Long? = null) {
        val dir = path ?: return
        val files = items.map { File(it.path) }
        val secret = password?.takeIf { format.supportsPassword && it.isNotEmpty() }
        runTask(s(I18nR.string.au_files_compressing), null, { cancel -> files.fold(0L) { total, file -> Math.addExact(total, FsOps.sizeOf(file, cancel)) } }) { cancel, progress ->
            val name = ArchiveFormats.freeName(ArchiveFormats.withExtension(zipName, format.extension)) { File(dir, it).exists() }
            val target = File(dir, name)
            try {
                if (format == CompressFormat.Zip && splitBytes != null) {
                    // The split writer owns all its staging files and collision cleanup.
                    de.mm20.launcher2.helper.SplitZipArchive.create(files, target, splitBytes, secret,
                        { if (cancel.cancelled) throw java.io.IOException("Cancelled") }, progress)
                } else {
                    try {
                        when {
                            format == CompressFormat.Zip && secret != null -> ArchiveWriter.zipEncrypted(files, target, secret, cancel, progress)
                            format == CompressFormat.Zip -> FsOps.zip(files, target, cancel, progress)
                            format == CompressFormat.SevenZ -> ArchiveWriter.sevenZ(files, target, cancel, progress)
                            else -> ArchiveWriter.tarGz(files, target, cancel, progress)
                        }
                    } catch (e: Exception) { target.delete(); throw e }
                }
                s(I18nR.string.au_files_created, target.name)
            } finally { secret?.fill('\u0000') }
        }
    }

    /** Unpacks an archive into a folder next to it. Asks for the password first when the archive is encrypted. */
    fun extract(entry: FsEntry) {
        val dir = path ?: return
        val archiveFs = ArchiveFs(File(entry.path))
        viewModelScope.launch {
            val needsPassword = !ArchiveSessions.has(entry.path) && withContext(Dispatchers.IO) {
                try { archiveFs.hasEncrypted } catch (e: ArchiveCryptoException) { true } catch (e: Exception) { false }
            }
            if (needsPassword) {
                archivePrompt = ArchivePrompt(entry.path, false) { extract(entry) }
                return@launch
            }
            runTask(s(I18nR.string.au_files_extracting), null) { cancel, progress ->
                val target = File(dir, local.freeName(dir, ArchivePath.baseName(entry.name)))
                try {
                    archiveFs.extractAll(target, cancel, progress)
                } catch (e: Exception) {
                    target.deleteRecursively() // a half unpacked folder helps nobody
                    throw e
                } finally {
                    // unpacking from the file list does not keep the password; inside an open archive it stays until the user leaves
                    val current = path
                    if (current == null || !ArchivePath.isArchive(current) || ArchivePath.archiveOf(current) != entry.path) ArchiveSessions.clear(entry.path)
                }
                s(I18nR.string.au_files_extracted, target.name)
            }
        }
    }

    suspend fun sizeOf(entry: FsEntry): Long = withContext(Dispatchers.IO) { runCatching { fs.totalSize(entry.path) }.getOrDefault(-1L) }
    suspend fun checksum(entry: FsEntry, algorithm: String): String = withContext(Dispatchers.IO) { runCatching { FsOps.hash(File(entry.path), algorithm) }.getOrDefault("") }
    suspend fun childCount(entry: FsEntry): Int = withContext(Dispatchers.IO) { runCatching { fs.list(entry.path).size }.getOrDefault(-1) }
}
