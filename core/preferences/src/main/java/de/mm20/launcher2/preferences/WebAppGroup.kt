package de.mm20.launcher2.preferences

import kotlinx.serialization.Serializable

@Serializable
data class WebAppGroup(
    val id: String,
    val name: String,
    val notificationsEnabled: Boolean = true,
    /** WebAppShortcut keys belonging to this group. */
    val appKeys: List<String> = emptyList(),
)
