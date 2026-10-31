package de.mm20.launcher2.searchactions.builders
import de.mm20.launcher2.i18n.R as I18nR

import android.content.Context
import de.mm20.launcher2.searchactions.TextClassificationResult
import de.mm20.launcher2.searchactions.actions.CreateContactAction
import de.mm20.launcher2.searchactions.actions.SearchAction
import de.mm20.launcher2.searchactions.actions.SearchActionIcon

class CreateContactActionBuilder(
    override val label: String
) : SearchActionBuilder {

    constructor(context: Context) : this(context.getString(I18nR.string.search_action_contact))

    override val key: String
        get() = "contact"

    override val icon: SearchActionIcon = SearchActionIcon.Contact

    override fun build(context: Context, classifiedQuery: TextClassificationResult): SearchAction? {
        if (classifiedQuery.phoneNumber != null || classifiedQuery.email != null) {
            return CreateContactAction(
                context.getString(I18nR.string.search_action_contact),
                phone = classifiedQuery.phoneNumber,
                email = classifiedQuery.email,
            )
        }
        return null
    }

}