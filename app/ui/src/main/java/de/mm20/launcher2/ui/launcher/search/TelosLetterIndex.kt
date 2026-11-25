package de.mm20.launcher2.ui.launcher.search

/**
 * Fast 0ms Binary Search App Drawer Letter Index Engine.
 * Quickly locates item indices in sorted app lists for fast-scroll sidebars.
 */
object TelosLetterIndex {

    /**
     * Binary searches for the first item in [items] whose label starts with [targetLetter].
     * Returns the 0-based index or 0 if not found.
     */
    fun <T> findFirstIndexForLetter(
        items: List<T>,
        targetLetter: Char,
        labelSelector: (T) -> String
    ): Int {
        if (items.isEmpty()) return 0
        val targetChar = targetLetter.lowercaseChar()

        var low = 0
        var high = items.size - 1
        var result = -1

        while (low <= high) {
            val mid = (low + high) ushr 1
            val label = labelSelector(items[mid]).trim()
            val firstChar = if (label.isNotEmpty()) label[0].lowercaseChar() else ' '

            if (firstChar >= targetChar) {
                if (firstChar == targetChar) {
                    result = mid
                }
                high = mid - 1
            } else {
                low = mid + 1
            }
        }

        return if (result != -1) result else 0
    }
}
