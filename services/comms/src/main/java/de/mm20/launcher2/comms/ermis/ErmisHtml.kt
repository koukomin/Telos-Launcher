package de.mm20.launcher2.comms.ermis

import org.jsoup.Jsoup
import org.jsoup.safety.Safelist

/** Only passive formatting survives. The UI renderer independently refuses every network request. */
internal object ErmisHtml {
    const val MAX_HTML = 200_000
    fun sanitize(source: String): String {
        if (source.length > MAX_HTML) return ""
        val safe = Safelist.relaxed()
            .removeTags("img")
            .addAttributes("table", "width", "cellpadding", "cellspacing", "border")
            .addAttributes("td", "colspan", "rowspan", "align")
            .addAttributes("th", "colspan", "rowspan", "align")
            .addAttributes(":all", "dir")
            .removeProtocols("a", "href", "ftp", "mailto")
        val clean = Jsoup.clean(source, "", safe, org.jsoup.nodes.Document.OutputSettings().prettyPrint(false))
        return clean.takeIf { it.length <= MAX_HTML }.orEmpty()
    }
}
