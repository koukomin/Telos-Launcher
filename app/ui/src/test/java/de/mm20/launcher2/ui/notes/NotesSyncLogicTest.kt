package de.mm20.launcher2.ui.notes

import org.junit.Assert.assertEquals
import org.junit.Test

class NotesSyncLogicTest {
    private fun notes(n: Int) = (1..n).map { Note(id = "n$it", remoteId = "d$it") }

    @Test
    fun emptyListingOfUnreachableFolderTrashesNothing() {
        val d = NotesSyncLogic.folderDeletions(notes(4), emptySet(), emptySet(), true, false)
        assertEquals(emptyList<String>(), d.toTrash); assertEquals(4, d.skipped)
    }

    @Test
    fun failedListingTrashesNothing() {
        assertEquals(0, NotesSyncLogic.folderDeletions(notes(4), setOf("d1"), emptySet(), false, true).toTrash.size)
    }

    @Test
    fun oneDeletedNoteIsTrashed() {
        val d = NotesSyncLogic.folderDeletions(notes(4), setOf("d1", "d2", "d3"), emptySet(), true, true)
        assertEquals(listOf("n4"), d.toTrash); assertEquals(0, d.skipped)
    }

    @Test
    fun moreThanHalfIsSkipped() {
        val d = NotesSyncLogic.folderDeletions(notes(4), setOf("d1"), emptySet(), true, true)
        assertEquals(0, d.toTrash.size); assertEquals(3, d.skipped)
        assertEquals(2, NotesSyncLogic.folderDeletions(notes(4), setOf("d1", "d2"), emptySet(), true, true).toTrash.size)
    }

    @Test
    fun createdAndTrashedNotesAreNotDeletions() {
        val list = notes(3) + Note(id = "t", remoteId = "dt", trashed = true)
        assertEquals(0, NotesSyncLogic.folderDeletions(list, setOf("d1", "d2"), setOf("d3"), true, true).toTrash.size)
    }

    @Test
    fun emptyListingOfReachableSingleNote() {
        assertEquals(listOf("n1"), NotesSyncLogic.folderDeletions(notes(1), emptySet(), emptySet(), true, true).toTrash)
    }

    @Test
    fun dedupe() {
        val existing = listOf(Note(title = "A", body = "x\n"))
        val (fresh, skipped) = NotesSyncLogic.dedupe(listOf(Note(title = "A", body = "x"), Note(title = "B", body = "y"), Note(title = "B", body = "y")), existing)
        assertEquals(1, fresh.size); assertEquals("B", fresh[0].title); assertEquals(2, skipped)
    }
}
