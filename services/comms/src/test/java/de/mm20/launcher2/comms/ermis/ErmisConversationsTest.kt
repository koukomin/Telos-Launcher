package de.mm20.launcher2.comms.ermis

import org.junit.Assert.*
import org.junit.Test

class ErmisConversationsTest {
    private fun message(uid: Long, id: String, account: String = "one", reply: String = "", references: List<String> = emptyList()) =
        ErmisMessage(account, uid, id, "sender@example.com", "Same subject", date = uid, inReplyTo = reply, references = references)

    @Test fun groupsRepliesAndSharedMissingAncestor() {
        val groups = ErmisConversations.group(listOf(message(1, "<a@example.com>", reply = "<missing@example.com>"), message(2, "<b@example.com>", references = listOf("<missing@example.com>"))))
        assertEquals(1, groups.size)
        assertEquals(listOf(2L, 1L), groups.single().map { it.uid })
    }
    @Test fun matchingSubjectsAndDifferentAccountsRemainSeparate() {
        assertEquals(3, ErmisConversations.group(listOf(message(1, "<a@ex>"), message(2, "<b@ex>"), message(3, "<a@ex>", account = "two"))).size)
    }
    @Test fun mergesTransitiveReferences() {
        assertEquals(1, ErmisConversations.group(listOf(message(1, "<a@ex>"), message(2, "<b@ex>"), message(3, "<c@ex>", references = listOf("<a@ex>", "<b@ex>")))).size)
    }
}
