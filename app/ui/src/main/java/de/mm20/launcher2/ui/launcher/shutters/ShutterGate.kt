package de.mm20.launcher2.ui.launcher.shutters

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.preferences.ui.ShutterSettings
import de.mm20.launcher2.searchable.SavableSearchableRepository
import de.mm20.launcher2.ui.common.SearchablePicker
import de.mm20.launcher2.ui.component.DismissableBottomSheet
import kotlinx.coroutines.flow.firstOrNull
import org.koin.compose.koinInject

/**
 * Swiping up on an app icon either launches the app/shortcut/etc already assigned as that app's
 * "shutter", or - on first use - shows a picker to assign one, then launches it immediately.
 */
@Composable
fun ShutterGate(
    packageName: String,
    shutterKey: String?,
    onDismiss: () -> Unit,
) {
    val shutterSettings = koinInject<ShutterSettings>()
    val searchableRepository = koinInject<SavableSearchableRepository>()
    val context = LocalContext.current

    if (shutterKey != null) {
        LaunchedEffect(shutterKey) {
            val target = searchableRepository.getByKeys(listOf(shutterKey)).firstOrNull()?.firstOrNull()
            if (target != null) {
                target.launch(context, null)
            } else {
                // Stale reference (e.g. the assigned shortcut/app was uninstalled) - clear it so
                // the next swipe-up shows the picker instead of silently doing nothing.
                shutterSettings.setApp(packageName, null)
            }
            onDismiss()
        }
    } else {
        DismissableBottomSheet(
            expanded = true,
            onDismissRequest = onDismiss,
        ) {
            SearchablePicker(
                modifier = Modifier.padding(bottom = 16.dp),
                value = null,
                onValueChanged = { picked ->
                    if (picked != null) {
                        shutterSettings.setApp(packageName, picked.key)
                        picked.launch(context, null)
                    }
                    onDismiss()
                },
            )
        }
    }
}
