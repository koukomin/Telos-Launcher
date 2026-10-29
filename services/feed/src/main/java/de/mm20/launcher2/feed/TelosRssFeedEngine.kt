package de.mm20.launcher2.feed

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

/**
 * Offline-first RSS & Atom feed parser for the Telos Launcher feed page.
 * Parses RSS XML feeds off the main thread into structured news articles.
 */
object TelosRssFeedEngine {

    private const val TAG = "TelosRssFeedEngine"

    data class RssItem(
        val title: String,
        val link: String,
        val description: String,
        val pubDate: String? = null
    )

    suspend fun parseRssFeed(xmlText: String): List<RssItem> = withContext(Dispatchers.Default) {
        if (xmlText.isBlank()) return@withContext emptyList()

        val items = mutableListOf<RssItem>()
        runCatching {
            val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xmlText))

            var eventType = parser.eventType
            var currentTitle: String? = null
            var currentLink: String? = null
            var currentDescription: String? = null
            var currentPubDate: String? = null
            var insideItem = false

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val name = parser.name
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (name.equals("item", ignoreCase = true) || name.equals("entry", ignoreCase = true)) {
                            insideItem = true
                        } else if (insideItem) {
                            when (name.lowercase()) {
                                "title" -> currentTitle = parser.nextText()
                                "link" -> {
                                    val href = parser.getAttributeValue(null, "href")
                                    currentLink = href ?: parser.nextText()
                                }
                                "description", "summary" -> currentDescription = parser.nextText()
                                "pubdate", "published" -> currentPubDate = parser.nextText()
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (name.equals("item", ignoreCase = true) || name.equals("entry", ignoreCase = true)) {
                            if (!currentTitle.isNullOrBlank() && !currentLink.isNullOrBlank()) {
                                items.add(
                                    RssItem(
                                        title = currentTitle.trim(),
                                        link = currentLink.trim(),
                                        description = currentDescription?.trim() ?: "",
                                        pubDate = currentPubDate?.trim()
                                    )
                                )
                            }
                            currentTitle = null
                            currentLink = null
                            currentDescription = null
                            currentPubDate = null
                            insideItem = false
                        }
                    }
                }
                eventType = parser.next()
            }
            Log.i(TAG, "Successfully parsed ${items.size} RSS news feed items")
        }.getOrElse { e ->
            Log.e(TAG, "Failed to parse RSS feed XML", e)
        }
        items
    }
}
