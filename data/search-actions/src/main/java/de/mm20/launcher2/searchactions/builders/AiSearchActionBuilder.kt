package de.mm20.launcher2.searchactions.builders
import de.mm20.launcher2.i18n.R as I18nR

import android.content.Context
import de.mm20.launcher2.searchactions.TextClassificationResult
import de.mm20.launcher2.searchactions.actions.AiSearchAction
import de.mm20.launcher2.searchactions.actions.SearchAction
import de.mm20.launcher2.searchactions.actions.SearchActionIcon

class AiSearchActionBuilder(
    override val label: String
) : SearchActionBuilder {

    constructor(context: Context) : this(context.getString(I18nR.string.search_action_websearch))

    override val key: String
        get() = "ai_search"

    override val icon: SearchActionIcon = SearchActionIcon.WebSearch

    override fun build(context: Context, classifiedQuery: TextClassificationResult): SearchAction? {
        val query = classifiedQuery.text.trim()
        if (query.startsWith("ai ", ignoreCase = true) || query.startsWith("? ")) {
            val q = query.substring(2).trim()
            if (q.isNotBlank()) {
                return AiSearchAction(
                    label = "$label: $q",
                    query = q,
                )
            }
        }
        return null
    }
}
