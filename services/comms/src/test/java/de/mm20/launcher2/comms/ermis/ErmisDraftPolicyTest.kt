package de.mm20.launcher2.comms.ermis

import org.junit.Assert.*
import org.junit.Test

class ErmisDraftPolicyTest {
    private val draft = ErmisSavedDraft("draft-1", "account-1", "unfinished@", "", "Body", updatedAt = 1L)
    @Test fun incompleteAddressIsPreservedAndUpdatesKeepASingleDraft() {
        val first = ErmisDraftPolicy.upsert(emptyList(), draft)
        val updated = ErmisDraftPolicy.upsert(first, draft.copy(body = "Updated", updatedAt = 2L))
        assertEquals(1, updated.size)
        assertEquals("unfinished@", updated.single().to)
        assertEquals("Updated", updated.single().body)
    }
    @Test fun refusesToEvictDraftsWhenCountLimitIsReached() {
        val full = (1..25).map { draft.copy(id = "draft-$it") }
        assertThrows(IllegalArgumentException::class.java) { ErmisDraftPolicy.upsert(full, draft.copy(id = "draft-26")) }
        assertEquals(25, ErmisDraftPolicy.upsert(full, draft.copy(body = "Still editable")).size)
    }
    @Test fun unicodeBodyBudgetCountsBytesAndNeverTruncates() {
        assertThrows(IllegalArgumentException::class.java) { ErmisDraftPolicy.validate(draft.copy(body = "α".repeat(100_001))) }
        ErmisDraftPolicy.validate(draft.copy(body = "α".repeat(100_000)))
        assertThrows(IllegalArgumentException::class.java) {
            ErmisDraftPolicy.upsert((1..20).map { draft.copy(id = "draft-$it", body = "a".repeat(200_000)) }, draft.copy(id = "more"))
        }
    }
    @Test fun rejectsUnpersistableAttachmentFormsAndHeaderInjection() {
        assertThrows(IllegalArgumentException::class.java) { ErmisDraftPolicy.validate(draft.copy(attachmentUris = listOf("file:///sdcard/private"))) }
        assertThrows(IllegalArgumentException::class.java) { ErmisDraftPolicy.validate(draft.copy(subject = "ok\r\nBcc: other")) }
        val uri = "content://documents/document/1"
        ErmisDraftPolicy.validate(draft.copy(attachmentUris = listOf(uri)))
        assertThrows(IllegalArgumentException::class.java) { ErmisDraftPolicy.validate(draft.copy(attachmentUris = listOf(uri, uri))) }
    }
}
