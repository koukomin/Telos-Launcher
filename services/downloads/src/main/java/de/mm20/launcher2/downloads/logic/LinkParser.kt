package de.mm20.launcher2.downloads.logic

/** Finds download links in pasted or shared text. */
object LinkParser {
    private val urlRegex = Regex("""https?://[^\s<>"'`]+""", RegexOption.IGNORE_CASE)

    /** All http(s) links in [text], trailing punctuation removed, in order and without duplicates */
    fun extractHttp(text: String): List<String> =
        urlRegex.findAll(text).map { it.value.trimEnd('.', ',', ';', ')', ']', '}', '!', '?') }
            .filter { it.length > "http://x".length }
            .distinct().toList()

    fun isHttpUrl(text: String): Boolean {
        val t = text.trim()
        return !t.contains(Regex("\\s")) && urlRegex.matches(t)
    }
}
