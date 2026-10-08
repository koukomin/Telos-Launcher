package de.mm20.launcher2.comms.media

/**
 * Hands a big list to another screen or process through an Intent: the Binder transaction limit
 * (about 1 MB for everything in flight) is exceeded by a few thousand addresses, which crashes
 * startActivity. Only a window around the chosen item is passed on.
 */
object PlaylistWindow {
    const val DEFAULT_MAX = 400

    /** The range of indices of a list of [size] items to hand over, containing [index]. */
    fun around(size: Int, index: Int, max: Int = DEFAULT_MAX): IntRange {
        if (size <= 0) return IntRange.EMPTY
        val i = index.coerceIn(0, size - 1)
        if (size <= max) return 0 until size
        val start = (i - max / 2).coerceIn(0, size - max)
        return start until start + max
    }
}

/** The part of [this] to pass on and the position of the chosen item in it. */
fun <T> List<T>.windowAround(index: Int, max: Int = PlaylistWindow.DEFAULT_MAX): Pair<List<T>, Int> {
    val range = PlaylistWindow.around(size, index, max)
    if (range.isEmpty()) return emptyList<T>() to 0
    return subList(range.first, range.last + 1) to (index.coerceIn(0, size - 1) - range.first)
}
