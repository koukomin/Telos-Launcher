package de.mm20.launcher2.ui.navigation

import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.serialization.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Type-Safe Navigation3 Routes Registry for Settings and Customization Screens in Telos Launcher.
 */

@Serializable
data object AboutSettingsRoute : NavKey

@Serializable
data object AdvancedSettingsRoute : NavKey

@Serializable
data object AgentsSettingsRoute : NavKey

@Serializable
data object AppearanceSettingsRoute : NavKey

@Serializable
data object ExportThemeSettingsRoute : NavKey

@Serializable
data object PerAppFontsSettingsRoute : NavKey

@Serializable
data object PresetsSettingsRoute : NavKey

@Serializable
data object AppLockAppsRoute : NavKey

@Serializable
data object AppLockSettingsRoute : NavKey

@Serializable
data object AppLockWebAppsRoute : NavKey

@Serializable
data object IntruderPhotosRoute : NavKey

@Serializable
data object AppSearchSettingsRoute : NavKey

@Serializable
data object ArtOptimizerRoute : NavKey

@Serializable
data object BackupSettingsRoute : NavKey

@Serializable
data object BreezyWeatherSettingsRoute : NavKey

@Serializable
data object BuildInfoSettingsRoute : NavKey

@Serializable
data class CalendarProviderSettingsRoute(val providerId: String) : NavKey

@Serializable
data object CalendarSearchSettingsRoute : NavKey

@Serializable
data class ColorSchemeSettingsRoute(
    @Serializable(with = UUIDSerializer::class) val id: UUID
) : NavKey

@Serializable
data object ColorSchemesSettingsRoute : NavKey

@Serializable
data object CommsSettingsRoute : NavKey
