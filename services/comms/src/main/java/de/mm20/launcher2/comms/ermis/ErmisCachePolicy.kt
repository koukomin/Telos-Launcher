package de.mm20.launcher2.comms.ermis

/** Bodies can be fetched again. Bound resident text across folders, not only each individual email. */
internal object ErmisCachePolicy {
    const val MAX_BODY_CHARS = 2_000_000
    fun bound(messages: List<ErmisMessage>, selected: ErmisMessage? = null): List<ErmisMessage> {
        var remaining = MAX_BODY_CHARS - (selected?.body?.length ?: 0).coerceAtMost(1_000_000)
        return messages.take(1000).map { message ->
            val active = selected != null && message.accountId == selected.accountId && message.folder == selected.folder && message.uid == selected.uid
            if (active) message.copy(body = message.body.take(1_000_000), html = message.html.take(200_000))
            else {
                val body = message.body.take(minOf(32_000, remaining.coerceAtLeast(0)))
                remaining -= body.length
                message.copy(body = body, html = "")
            }
        }
    }
}
