package de.mm20.launcher2.comms.ermis

/** Link messages by RFC message identifiers, never merely by a matching subject. */
object ErmisConversations {
    private val messageId = Regex("<[^<>\\s]{1,998}>")
    fun identifiers(header: String): List<String> = messageId.findAll(header.take(32_000)).map { it.value }.take(100).toList()

    fun group(messages: List<ErmisMessage>): List<List<ErmisMessage>> {
        val parents = IntArray(messages.size) { it }
        fun root(index: Int): Int {
            var current = index
            while (parents[current] != current) { parents[current] = parents[parents[current]]; current = parents[current] }
            return current
        }
        val owners = mutableMapOf<Pair<String, String>, Int>()
        messages.forEachIndexed { index, mail ->
            val ids = (identifiers(mail.messageId) + identifiers(mail.inReplyTo) + mail.references.flatMap(::identifiers)).distinct()
            ids.forEach { id ->
                val previous = owners.putIfAbsent(mail.accountId to id, index)
                if (previous != null) parents[root(index)] = root(previous)
            }
        }
        return messages.indices.groupBy(::root).values.map { indices -> indices.map(messages::get).sortedByDescending { it.date } }
            .sortedByDescending { it.first().date }
    }
}
