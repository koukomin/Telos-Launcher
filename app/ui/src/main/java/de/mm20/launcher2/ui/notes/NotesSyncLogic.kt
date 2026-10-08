package de.mm20.launcher2.ui.notes

/** The decisions of the sync that can lose notes, kept free of Android classes so that they can be tested. */
object NotesSyncLogic {

    /** [toTrash] are the ids of notes that were deleted in the folder, [skipped] how many were not trashed for safety. */
    data class FolderDecision(val toTrash: List<String>, val skipped: Int)

    /**
     * Which notes were deleted in the folder. A note counts as deleted only if the listing worked and, when it is empty,
     * the folder itself is still reachable. If more than half of the synced notes (and at least two) would go to the
     * trash in one run, nothing is trashed: that looks like a lost folder, not like a clean up.
     */
    fun folderDeletions(
        notes: List<Note>, listedDocIds: Set<String>, createdDocIds: Set<String>,
        listingOk: Boolean, rootReachable: Boolean,
    ): FolderDecision {
        val synced = notes.filter { it.remoteId != null && !it.trashed }
        val gone = synced.filter { it.remoteId !in listedDocIds && it.remoteId !in createdDocIds }
        if (gone.isEmpty()) return FolderDecision(emptyList(), 0)
        if (!listingOk) return FolderDecision(emptyList(), gone.size)
        if (listedDocIds.isEmpty() && !rootReachable) return FolderDecision(emptyList(), gone.size)
        if (gone.size >= 2 && gone.size * 2 > synced.size) return FolderDecision(emptyList(), gone.size)
        return FolderDecision(gone.map { it.id }, 0)
    }

    private fun key(n: Note) = n.title.trim() + "\u0000" + n.body.replace("\r\n", "\n").trim()

    /** Drops the imported notes that [existing] (or an earlier imported note) already has: same title and same text. */
    fun dedupe(incoming: List<Note>, existing: List<Note>): Pair<List<Note>, Int> {
        val seen = existing.map(::key).toMutableSet()
        val fresh = incoming.filter { seen.add(key(it)) }
        return fresh to (incoming.size - fresh.size)
    }
}
