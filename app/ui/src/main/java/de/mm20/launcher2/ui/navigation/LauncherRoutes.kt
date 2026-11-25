package de.mm20.launcher2.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Type-Safe Navigation3 Routes Registry for Main Application and Virtual Apps in Telos Launcher.
 */

@Serializable
data object FilesRoute : NavKey

@Serializable
data object StoreRoute : NavKey

@Serializable
data object MediaRoute : NavKey

@Serializable
data object PhoneRoute : NavKey

@Serializable
data object MessagesRoute : NavKey

@Serializable
data object RadioRoute : NavKey

@Serializable
data object TvRoute : NavKey

@Serializable
data object NotesRoute : NavKey

@Serializable
data object CalendarRoute : NavKey

@Serializable
data object CalculatorRoute : NavKey

@Serializable
data object ClockRoute : NavKey

@Serializable
data object CameraRoute : NavKey

@Serializable
data object PhotosRoute : NavKey

@Serializable
data object VideoRoute : NavKey

@Serializable
data object ViewerRoute : NavKey

@Serializable
data object StremioRoute : NavKey
