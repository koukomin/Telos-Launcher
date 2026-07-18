package de.mm20.launcher2.ui.settings.appearance.presets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.locals.LocalBackStack
import de.mm20.launcher2.ui.settings.colorscheme.ColorSchemePreview
import kotlinx.serialization.Serializable

@Serializable
data object PresetsSettingsRoute : NavKey

@Composable
fun PresetsSettingsScreen() {
    val viewModel: PresetsSettingsScreenVM = viewModel()
    val backStack = LocalBackStack.current
    val presets by viewModel.presets.collectAsStateWithLifecycle()

    PreferenceScreen(
        title = stringResource(R.string.preference_screen_presets),
    ) {
        item {
            PreferenceCategory {
                for (bundle in presets) {
                    Preference(
                        icon = R.drawable.palette_24px,
                        title = bundle.name,
                        summary = bundle.author?.takeIf { it.isNotBlank() },
                        controls = {
                            val colors = bundle.colors
                            if (colors != null) {
                                ColorSchemePreview(colors)
                            }
                        },
                        onClick = {
                            viewModel.install(bundle)
                            backStack.removeLastOrNull()
                        }
                    )
                }
            }
        }
    }
}
