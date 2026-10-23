package de.mm20.launcher2.searchactions.actions

import android.content.Context
import android.content.Intent
import android.net.Uri

class AiSearchAction(
    override val label: String,
    val query: String,
) : SearchAction {

    override val icon: SearchActionIcon = SearchActionIcon.WebSearch
    override val iconColor: Int = 0
    override val customIcon: String? = null

    override fun start(context: Context) {
        val encoded = Uri.encode(query)
        val url = "https://www.google.com/search?q=$encoded"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }
}
