package de.mm20.launcher2.ui.settings.appearance

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.PerAppFontScaleSetting
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import kotlinx.serialization.Serializable

@Serializable
data object PerAppFontsSettingsRoute : NavKey

@Composable
fun PerAppFontsSettingsScreen() {
    PreferenceScreen(
        title = { Text("Per-App Font Scales / Γραμματοσειρές ανά Εφαρμογή") }
    ) {
        item {
            PreferenceCategory(title = "Telos Core Apps") {
                PerAppFontScaleSetting(appKey = "system", modifier = Modifier.padding(8.dp))
                PerAppFontScaleSetting(appKey = "media", modifier = Modifier.padding(8.dp))
                PerAppFontScaleSetting(appKey = "store", modifier = Modifier.padding(8.dp))
                PerAppFontScaleSetting(appKey = "files", modifier = Modifier.padding(8.dp))
                PerAppFontScaleSetting(appKey = "network", modifier = Modifier.padding(8.dp))
                PerAppFontScaleSetting(appKey = "clock", modifier = Modifier.padding(8.dp))
                PerAppFontScaleSetting(appKey = "calculator", modifier = Modifier.padding(8.dp))
                PerAppFontScaleSetting(appKey = "notes", modifier = Modifier.padding(8.dp))
                PerAppFontScaleSetting(appKey = "camera", modifier = Modifier.padding(8.dp))
            }
        }
    }
}
