package dev.izumi.appopsnext.presentation.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.izumi.appopsnext.R

enum class MainDestination(
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int,
    @DrawableRes val selectedIconRes: Int,
) {
    APPS(
        labelRes = R.string.appopsnext_navigation_apps,
        iconRes = R.drawable.appopsnext_ic_ph_squares_four,
        selectedIconRes = R.drawable.appopsnext_ic_ph_squares_four_fill,
    ),
    TEMPLATES(
        labelRes = R.string.appopsnext_navigation_templates,
        iconRes = R.drawable.appopsnext_ic_ph_file_text,
        selectedIconRes = R.drawable.appopsnext_ic_ph_file_text_fill,
    ),
    HISTORY(
        labelRes = R.string.appopsnext_navigation_history,
        iconRes = R.drawable.appopsnext_ic_ph_clock_counter_clockwise,
        selectedIconRes = R.drawable.appopsnext_ic_ph_clock_counter_clockwise_fill,
    ),
    SETTINGS(
        labelRes = R.string.appopsnext_navigation_settings,
        iconRes = R.drawable.appopsnext_ic_ph_gear,
        selectedIconRes = R.drawable.appopsnext_ic_ph_gear_fill,
    ),
}

@Composable
fun AppNavigationBar(
    selectedDestination: MainDestination,
    onDestinationSelected: (MainDestination) -> Unit,
) {
    androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        androidx.compose.foundation.layout.Row(
            androidx.compose.ui.Modifier.fillMaxWidth().navigationBarsPadding().selectableGroup().padding(8.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.Top,
        ) {
            MainDestination.entries.forEach { destination ->
                val selected = destination == selectedDestination
                val foreground = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                androidx.compose.foundation.layout.Column(
                    androidx.compose.ui.Modifier.weight(1f).clip(MaterialTheme.shapes.medium)
                        .selectable(selected, role = androidx.compose.ui.semantics.Role.Tab) { onDestinationSelected(destination) }
                        .heightIn(min = 48.dp).padding(vertical = 8.dp),
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
                ) {
                    androidx.compose.foundation.layout.Box(
                        androidx.compose.ui.Modifier.background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.large)
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Icon(painterResource(if (selected) destination.selectedIconRes else destination.iconRes), null, tint = foreground)
                    }
                    Text(stringResource(destination.labelRes), color = foreground, style = MaterialTheme.typography.labelSmall,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
    }
}
