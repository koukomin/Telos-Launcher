package de.mm20.launcher2.comms.ermis

/** Reject excessive content rather than silently truncating or evicting unfinished mail. */
internal object ErmisDraftPolicy {
    private val identifier = Regex("[A-Za-z0-9_-]{1,80}")
    private const val MAX_DRAFTS = 25
    private const val MAX_TOTAL_BYTES = 4_000_000
    fun validate(draft: ErmisSavedDraft) {
        require(identifier.matches(draft.id) && identifier.matches(draft.accountId))
        require(draft.body.length <= 200_000 && draft.body.toByteArray(Charsets.UTF_8).size <= 200_000)
        require(listOf(draft.to, draft.cc, draft.bcc, draft.subject).all {
            it.length <= 16_000 && !it.contains('\r') && !it.contains('\n')
        })
        require(draft.attachmentUris.size + (if (draft.forwardSource != null) 1 else 0) <= 20 && draft.attachmentUris.distinct().size == draft.attachmentUris.size)
        require(draft.attachmentUris.all { it.length <= 4096 && it.startsWith("content://") })
        require(draft.references.size <= 100 && draft.references.all { validHeader(it) })
        require(draft.inReplyTo == null || validHeader(draft.inReplyTo))
        draft.forwardSource?.let(ErmisForwardPolicy::validate)
    }
    private fun validHeader(text: String) = text.length <= 998 && !text.contains('\r') && !text.contains('\n')
    private fun bytes(draft: ErmisSavedDraft): Int = listOf(draft.id, draft.accountId, draft.to, draft.cc,
        draft.bcc, draft.subject, draft.body, draft.inReplyTo.orEmpty()).sumOf { it.toByteArray(Charsets.UTF_8).size } +
        draft.attachmentUris.sumOf { it.toByteArray(Charsets.UTF_8).size } +
        draft.references.sumOf { it.toByteArray(Charsets.UTF_8).size } +
        (draft.forwardSource?.let { it.accountId.toByteArray(Charsets.UTF_8).size + it.folder.toByteArray(Charsets.UTF_8).size + 32 } ?: 0)
    fun upsert(existing: List<ErmisSavedDraft>, draft: ErmisSavedDraft): List<ErmisSavedDraft> {
        validate(draft)
        val next = (existing.filterNot { it.id == draft.id } + draft).sortedByDescending { it.updatedAt }
        require(next.size <= MAX_DRAFTS)
        require(next.sumOf(::bytes) <= MAX_TOTAL_BYTES)
        return next
    }
}
