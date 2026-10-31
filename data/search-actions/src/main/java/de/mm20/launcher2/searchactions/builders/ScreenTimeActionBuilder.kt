package de.mm20.launcher2.searchactions.builders
import de.mm20.launcher2.i18n.R as I18nR

import android.content.Context
import de.mm20.launcher2.searchactions.TextClassificationResult
import de.mm20.launcher2.searchactions.actions.SearchAction
import de.mm20.launcher2.searchactions.actions.SearchActionIcon
import de.mm20.launcher2.searchactions.actions.ScreenTimeAction

class ScreenTimeActionBuilder(
    override val label: String
) : SearchActionBuilder {

    constructor(context: Context) : this(context.getString(I18nR.string.search_action_screentime))

    override val key: String
        get() = "screentime"

    override val icon: SearchActionIcon = SearchActionIcon.StatsSearch

    override fun build(context: Context, classifiedQuery: TextClassificationResult): SearchAction? {
        val query = classifiedQuery.text.lowercase().trim()
        if (query.contains("screen time") || query.contains("screentime") || query.contains("usage")) {
            val text = ScreenTimeAction.getTodayScreenTime(context)
            return ScreenTimeAction(
                label = text,
                screenTimeText = text,
            )
        }
        return null
    }
}
