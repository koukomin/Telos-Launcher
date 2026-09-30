package de.mm20.launcher2.ui.desktopmode

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.ShapedLauncherIcon
import de.mm20.launcher2.applications.AppRepository
import de.mm20.launcher2.icons.IconService
import de.mm20.launcher2.ui.ktx.toPixels
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import org.koin.compose.koinInject

val DesktopTaskbarHeight = 56.dp

@Composable
internal fun DesktopTaskbar(
    modifier: Modifier = Modifier,
    startMenuOpen: Boolean,
    onToggleStartMenu: () -> Unit,
) {
    val context = LocalContext.current
    val appRepository = koinInject<AppRepository>()
    val iconService = koinInject<IconService>()

    // === TELOS_PENDING_REVIEW_START: desktop_taskbar_running_apps ===
    val tasksTracker = remember(context) { DesktopRunningTasksTracker(context) }
    val runningTasks by tasksTracker.runningTasks.collectAsStateWithLifecycle()
    // === TELOS_PENDING_REVIEW_END: desktop_taskbar_running_apps ===

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(DesktopTaskbarHeight)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val startBackground by animateColorAsState(
            if (startMenuOpen) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainer
        )
        Row(
            modifier = Modifier
                .height(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(startBackground)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onToggleStartMenu,
                )
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                painterResource(R.drawable.apps_24px),
                contentDescription = stringResource(R.string.desktop_mode_start_menu),
                modifier = Modifier.size(22.dp),
                tint = if (startMenuOpen) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onSurface,
            )
        }

        // === TELOS_PENDING_REVIEW_START: desktop_taskbar_running_apps ===
        LazyRow(
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(runningTasks, key = { it.taskId }) { task ->
                val appFlow = remember(task.packageName) {
                    appRepository.findMany().map { apps -> apps.firstOrNull { it.componentName.packageName == task.packageName } }
                }
                val app by appFlow.collectAsStateWithLifecycle(null)
                
                val iconSizePx = 32.dp.toPixels().toInt()
                val iconFlow = remember(app) {
                    app?.let { iconService.getIcon(it, iconSizePx) }
                }
                val icon by (iconFlow ?: emptyFlow()).collectAsStateWithLifecycle(null)

                Row(
                    modifier = Modifier
                        .height(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { tasksTracker.bringTaskToFront(task.taskId) }
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (icon != null) {
                        ShapedLauncherIcon(
                            icon = { icon },
                            size = 32.dp,
                            badge = { null }
                        )
                    } else {
                        // Fallback icon
                        Icon(painterResource(R.drawable.android_24px), contentDescription = null, modifier = Modifier.size(32.dp))
                    }
                    
                    // Running Indicator (Dot)
                    Box(
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .size(6.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    )
                }
            }
        }
        // === TELOS_PENDING_REVIEW_END: desktop_taskbar_running_apps ===

        Row(
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DesktopSystemTray()
            DesktopClock()
        }
    }
}
