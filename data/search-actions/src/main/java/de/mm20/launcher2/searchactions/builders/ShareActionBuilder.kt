package de.mm20.launcher2.searchactions.builders
import de.mm20.launcher2.i18n.R as I18nR

import android.content.Context
import de.mm20.launcher2.searchactions.TextClassificationResult
import de.mm20.launcher2.searchactions.actions.SearchAction
import de.mm20.launcher2.searchactions.actions.SearchActionIcon
import de.mm20.launcher2.searchactions.actions.ShareAction

class ShareActionBuilder(
    override val label: String,
) : SearchActionBuilder {

    constructor(context: Context) : this(context.getString(I18nR.string.search_action_share))

    override val key: String
        get() = "share"

    override val icon: SearchActionIcon = SearchActionIcon.Share

    override fun build(context: Context, classifiedQuery: TextClassificationResult): SearchAction? {
        return ShareAction(
            context.getString(I18nR.string.search_action_share),
            classifiedQuery.text
        )
    }
}