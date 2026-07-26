package de.mm20.launcher2.search

interface Folder : SavableSearchable {
    /**
     * Keys of searchables inside this folder.
     */
    val itemKeys: List<String>

    /**
     * If true, tapping the folder launches the first item, and swiping up opens the folder.
     */
    val isCover: Boolean

    override val preferDetailsOverLaunch: Boolean
        get() = true
}
