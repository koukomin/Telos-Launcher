package de.mm20.launcher2.search

/**
 * Zero-Allocation Multi-Word Token Scanner for Telos Search Engine.
 * Iterates word boundaries in search query strings without instantiating intermediate String arrays or Lists.
 */
object TelosTokenScanner {

    /**
     * Scans word boundaries in [query] and invokes [tokenAction] with start and end indices.
     */
    inline fun scanWordTokens(query: CharSequence, tokenAction: (start: Int, end: Int) -> Unit) {
        val len = query.length
        var inWord = false
        var start = 0

        for (i in 0 until len) {
            val c = query[i]
            // Match the ASCII separators previously handled by Regex("\\s+").
            if (c == ' ' || c in '\t'..'\r') {
                if (inWord) {
                    tokenAction(start, i)
                    inWord = false
                }
            } else {
                if (!inWord) {
                    start = i
                    inWord = true
                }
            }
        }

        if (inWord) {
            tokenAction(start, len)
        }
    }
}
