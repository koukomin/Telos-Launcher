package de.mm20.launcher2.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.ui.layout.UiSpacing

/** Three-destination bars with natural label height; large fonts never compete with a fixed 80dp bar. */
@Composable
fun AdaptiveNavigationBar(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(modifier = modifier, color = containerColor) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().selectableGroup().padding(UiSpacing.Small),
            horizontalArrangement = Arrangement.spacedBy(UiSpacing.Small),
            verticalAlignment = Alignment.Top,
            content = content,
        )
    }
}

@Composable
fun RowScope.AdaptiveNavigationItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    label: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.weight(1f).clip(MaterialTheme.shapes.medium)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .heightIn(min = UiSpacing.TouchTarget).padding(vertical = UiSpacing.Small),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        CompositionLocalProvider(LocalContentColor provides if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant) {
            Box(
                Modifier.background(if (selected) colors.secondaryContainer else Color.Transparent, MaterialTheme.shapes.large)
                    .padding(horizontal = UiSpacing.Content, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) { icon() }
        }
        CompositionLocalProvider(LocalContentColor provides if (selected) colors.onSurface else colors.onSurfaceVariant) {
            ProvideTextStyle(MaterialTheme.typography.labelMedium.copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center)) { label() }
        }
    }
}
