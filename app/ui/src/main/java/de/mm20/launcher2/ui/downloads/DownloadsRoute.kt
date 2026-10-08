package de.mm20.launcher2.ui.downloads

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** [initialUrls]: links to put into the add sheet right away (a share or an Open with) */
@Serializable
data class DownloadsRoute(val initialUrls: List<String> = emptyList()) : NavKey

@Serializable
data object DownloadsSettingsRoute : NavKey
