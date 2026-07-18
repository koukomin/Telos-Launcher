package de.mm20.launcher2.themes

import de.mm20.launcher2.preferences.IconShape
import de.mm20.launcher2.preferences.SearchBarStyle
import kotlinx.serialization.Serializable

/**
 * The layout half of a [ThemeBundle] - grid density, icon shape/size, dock, search bar placement
 * and style, and animation character. Every field is optional so a bundle can tweak just a few of
 * these without having to restate the rest; installing one only touches the fields it sets.
 */
@Serializable
data class LayoutPreset(
    val gridColumnCount: Int? = null,
    val gridIconSize: Int? = null,
    val gridShowLabels: Boolean? = null,
    val iconShape: IconShape? = null,
    val dock: Boolean? = null,
    val dockRows: Int? = null,
    val bottomSearchBar: Boolean? = null,
    val searchBarStyle: SearchBarStyle? = null,
    val fixedSearchBar: Boolean? = null,
    val reduceAnimations: Boolean? = null,
    val animationSpeed: Float? = null,
)
