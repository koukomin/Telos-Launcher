package de.mm20.launcher2.comms.ermis

import org.junit.Assert.*
import org.junit.Test

class ErmisMailboxPolicyTest {
    private val inbox = ErmisMessage("one", 42, "<same@example.org>", "sender@example.org", "Subject", date = 1)
    @Test fun uidIdentityAndValidityAreIsolatedByAccountAndFolder() {
        assertEquals("one", ErmisMailboxPolicy.validityKey("one", "inbox"))
        assertEquals("one|Archive", ErmisMailboxPolicy.validityKey("one", "Archive"))
        assertNotEquals(ErmisMailboxPolicy.validityKey("one", "Archive"), ErmisMailboxPolicy.validityKey("two", "Archive"))
        assertFalse(ErmisMailboxPolicy.matches(inbox, "one", "Archive", 42))
        assertFalse(ErmisMailboxPolicy.matches(inbox, "two", "INBOX", 42))
        assertTrue(ErmisMailboxPolicy.matches(inbox, "one", "inbox", 42))
        assertFalse(ErmisMailboxPolicy.matches(inbox, "one", "INBOX", 43))
    }
    @Test fun namedFoldersRemainCaseSensitiveWhileInboxUsesLegacyCanonicalName() {
        assertNotEquals(ErmisMailboxPolicy.validityKey("one", "Personal"), ErmisMailboxPolicy.validityKey("one", "personal"))
        assertThrows(IllegalArgumentException::class.java) { ErmisMailboxPolicy.validityKey("one|other", "Inbox") }
        assertThrows(IllegalArgumentException::class.java) { ErmisMailboxPolicy.folder("INBOX\r\nUID EXPUNGE 1:*") }
    }
    @Test fun customFoldersRejectInboxControlsAndWildcardNames() {
        assertEquals("Πελάτες 2026", ErmisMailboxPolicy.customFolder("  Πελάτες 2026  "))
        for (name in listOf("INBOX", " inbox ", "", "  ", "Personal*", "Personal%", "A\tB", "A\nB", "A\u0000B"))
            assertThrows(IllegalArgumentException::class.java) { ErmisMailboxPolicy.customFolder(name) }
    }
    @Test fun specialUseAttributesRecognizeLocalizedRolesBeforeNames() {
        assertEquals(ErmisFolderRole.TRASH, ErmisMailboxPolicy.role("[Gmail]/Κάδος", "Κάδος", listOf("\\Trash")))
        assertEquals(ErmisFolderRole.SPAM, ErmisMailboxPolicy.role("Unwanted", "Unwanted", listOf("\\jUnK")))
        assertEquals(ErmisFolderRole.SENT, ErmisMailboxPolicy.role("Απεσταλμένα", "Archive", listOf("\\Sent")))
        assertEquals(ErmisFolderRole.ALL, ErmisMailboxPolicy.role("[Gmail]/Όλα", "Όλα", listOf("\\All")))
        assertEquals(ErmisFolderRole.INBOX, ErmisMailboxPolicy.role("INBOX", "Inbox", emptyList()))
    }
    @Test fun roleFallbackNeverGuessesFromSubstringOrTreatsAllMailAsArchive() {
        assertEquals(ErmisFolderRole.SENT, ErmisMailboxPolicy.role("Sent Items", "Sent Items", emptyList()))
        assertEquals(ErmisFolderRole.ALL, ErmisMailboxPolicy.role("All Mail", "All Mail", emptyList()))
        assertEquals(ErmisFolderRole.ARCHIVE, ErmisMailboxPolicy.role("Archive", "Archive", emptyList()))
        for (name in listOf("Client Archives 2026", "Trash invoices", "Αρχείο", "Sentimental"))
            assertEquals(ErmisFolderRole.OTHER, ErmisMailboxPolicy.role(name, name, emptyList()))
    }
    @Test fun sameMessageIdAndUidInDifferentFoldersNeverMergeConversations() {
        val archive = inbox.copy(folder = "Archive")
        val otherAccount = inbox.copy(accountId = "two")
        assertEquals(3, ErmisConversations.group(listOf(inbox, archive, otherAccount)).size)
    }
    @Test fun nativeMoveNeverInvokesCopyOrDeletionFallback() {
        val calls = mutableListOf<String>()
        ErmisMovePolicy.execute(true, true, { calls += "move" }, { calls += "copy" }, { calls += "delete" }, { calls += "uid-expunge" })
        assertEquals(listOf("move"), calls)
    }
    @Test fun fallbackCopiesBeforeDeletingAndOnlyUidExpungesSelectedMessage() {
        val calls = mutableListOf<String>()
        ErmisMovePolicy.execute(false, true, { calls += "move" }, { calls += "copy" }, { calls += "delete" }, { calls += "uid-expunge" })
        assertEquals(listOf("copy", "delete", "uid-expunge"), calls)
        calls.clear()
        ErmisMovePolicy.execute(false, false, { calls += "move" }, { calls += "copy" }, { calls += "delete" }, { calls += "uid-expunge" })
        assertEquals(listOf("copy", "delete"), calls)
    }
    @Test fun failedOrUncertainCopyNeverMarksSourceDeleted() {
        val calls = mutableListOf<String>()
        assertThrows(IllegalStateException::class.java) {
            ErmisMovePolicy.execute(false, true, {}, { calls += "copy"; error("server response lost") }, { calls += "delete" }, { calls += "uid-expunge" })
        }
        assertEquals(listOf("copy"), calls)
    }
    @Test fun nativeMoveFailureDoesNotTryPotentiallyDuplicatingFallback() {
        val calls = mutableListOf<String>()
        assertThrows(IllegalStateException::class.java) {
            ErmisMovePolicy.execute(true, true, { calls += "move"; error("server response lost") }, { calls += "copy" }, { calls += "delete" }, { calls += "uid-expunge" })
        }
        assertEquals(listOf("move"), calls)
    }
}
