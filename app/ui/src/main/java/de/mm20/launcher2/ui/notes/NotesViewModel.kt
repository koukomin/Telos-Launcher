package de.mm20.launcher2.ui.notes

import de.mm20.launcher2.search.GreekFold
import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

enum class NotesFilter { Notes, Archive, Trash }

class NotesViewModel(app: Application) : AndroidViewModel(app), KoinComponent {
    val store: NotesStore by inject()
    val sync = NotesSync(app, store)

    val query = MutableStateFlow("")
    val filter = MutableStateFlow(NotesFilter.Notes)
    val label = MutableStateFlow<String?>(null)
    val syncing = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)

    init { viewModelScope.launch(Dispatchers.IO) { store.load() } }

    val labels: StateFlow<List<String>> = store.notes.combine(filter) { n, _ -> n.flatMap { it.labels }.distinct().sorted() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val visible: StateFlow<List<Note>> = combine(store.notes, query, filter, label) { all, q, f, l ->
        all.filter {
            when (f) {
                NotesFilter.Notes -> !it.archived && !it.trashed
                NotesFilter.Archive -> it.archived && !it.trashed
                NotesFilter.Trash -> it.trashed
            }
        }.filter { l == null || l in it.labels }
            .filter { q.isBlank() || de.mm20.launcher2.comms.search.TelosSearch.matches(q, it.title, it.body, *it.labels.toTypedArray()) }
            .sortedWith(compareByDescending<Note> { it.pinned }.thenByDescending { it.modifiedAt })
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun save(n: Note) = viewModelScope.launch(Dispatchers.IO) { if (n.isEmpty) store.deleteForever(n.id) else store.saveEdited(n) }
    fun trash(n: Note) = viewModelScope.launch(Dispatchers.IO) { store.saveEdited(n.copy(trashed = true, pinned = false)) }
    fun restore(n: Note) = viewModelScope.launch(Dispatchers.IO) { store.saveEdited(n.copy(trashed = false)) }
    fun delete(n: Note) = viewModelScope.launch(Dispatchers.IO) { store.deleteForever(n.id) }
    fun emptyTrash() = viewModelScope.launch(Dispatchers.IO) { store.emptyTrash() }

    fun import(uris: List<Uri>) = viewModelScope.launch {
        val read = withContext(Dispatchers.IO) { NotesImport.read(getApplication(), uris) }
        val (fresh, skipped) = withContext(Dispatchers.IO) {
            NotesSyncLogic.dedupe(read, store.notes.value).also { (f, _) -> f.forEach { store.save(it, touch = false) } }
        }
        message.value = "import:${fresh.size}:$skipped"
    }

    fun syncNow() = viewModelScope.launch {
        if (syncing.value) return@launch
        syncing.value = true
        val r = try { sync.syncAll() } finally { syncing.value = false }
        message.value = if (r.error != null) "error:${r.error}" else "sync:${r.uploaded}:${r.downloaded}:${r.skippedDeletes}"
    }
}
