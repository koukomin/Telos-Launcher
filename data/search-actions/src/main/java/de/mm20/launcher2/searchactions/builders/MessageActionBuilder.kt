package de.mm20.launcher2.searchactions.builders
import de.mm20.launcher2.i18n.R as I18nR

import android.content.Context
import de.mm20.launcher2.searchactions.TextClassificationResult
import de.mm20.launcher2.searchactions.actions.MessageAction
import de.mm20.launcher2.searchactions.actions.SearchAction
import de.mm20.launcher2.searchactions.actions.SearchActionIcon

class MessageActionBuilder(
    override val label: String
): SearchActionBuilder {

    constructor(context: Context) : this(context.getString(I18nR.string.search_action_message))

    override val key: String
        get() = "message"

    override val icon: SearchActionIcon = SearchActionIcon.Message

    override fun build(context: Context, classifiedQuery: TextClassificationResult): SearchAction? {
        if (classifiedQuery.phoneNumber != null) {
            return MessageAction(
                context.getString(I18nR.string.search_action_message), classifiedQuery.phoneNumber
            )
        }
        return null
    }
}