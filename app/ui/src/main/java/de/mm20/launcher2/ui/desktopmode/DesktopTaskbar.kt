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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.launch
import de.mm20.launcher2.desktopmode.DesktopWindowManager
import de.mm20.launcher2.desktopmode.SnapPosition
import de.mm20.launcher2.desktopmode.WindowSnapCalculator

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable

val DesktopTaskbarHeight = 56.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DesktopTaskbar(
    modifier: Modifier = Modifier,
    startMenuOpen: Boolean,
    onToggleStartMenu: () -> Unit,
) {
    val context = LocalContext.current
    val appRepository = koinInject<AppRepository>()
    val iconService = koinInject<IconService>()

    // === TELOS_PENDING_REVIEW_START: desktop_window_snapping ===
    val windowManager = koinInject<DesktopWindowManager>()
    val scope = rememberCoroutineScope()
    // === TELOS_PENDING_REVIEW_END: desktop_window_snapping ===

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

        // === TELOS_PENDING_REVIEW_START: desktop_window_snapping ===
    val windowManager = koinInject<DesktopWindowManager>()
    val scope = rememberCoroutineScope()
    // === TELOS_PENDING_REVIEW_END: desktop_window_snapping ===

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

                // === TELOS_PENDING_REVIEW_START: desktop_window_snapping ===
                var showTaskMenu by remember { mutableStateOf(false) }
                // === TELOS_PENDING_REVIEW_END: desktop_window_snapping ===

                Row(
                    modifier = Modifier
                        .height(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        // === TELOS_PENDING_REVIEW_START: desktop_window_snapping ===
                        .combinedClickable(
                            onClick = { tasksTracker.bringTaskToFront(task.taskId) },
                            onLongClick = { showTaskMenu = true }
                        )
                        // === TELOS_PENDING_REVIEW_END: desktop_window_snapping ===
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

                    // === TELOS_PENDING_REVIEW_START: desktop_window_snapping ===
                    val taskbarHeightPx = DesktopTaskbarHeight.toPixels().toInt()
                    DropdownMenu(
                        expanded = showTaskMenu,
                        onDismissRequest = { showTaskMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.telos_snap_left)) },
                            onClick = {
                                showTaskMenu = false
                                scope.launch {
                                    val metrics = context.resources.displayMetrics
                                    val bounds = WindowSnapCalculator.calculateBounds(metrics, SnapPosition.LEFT_HALF, taskbarHeightPx)
                                    windowManager.snapTask(task.taskId, bounds)
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.telos_snap_right)) },
                            onClick = {
                                showTaskMenu = false
                                scope.launch {
                                    val metrics = context.resources.displayMetrics
                                    val bounds = WindowSnapCalculator.calculateBounds(metrics, SnapPosition.RIGHT_HALF, taskbarHeightPx)
                                    windowManager.snapTask(task.taskId, bounds)
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.telos_maximize)) },
                            onClick = {
                                showTaskMenu = false
                                scope.launch {
                                    val metrics = context.resources.displayMetrics
                                    val bounds = WindowSnapCalculator.calculateBounds(metrics, SnapPosition.MAXIMIZED, taskbarHeightPx)
                                    windowManager.snapTask(task.taskId, bounds)
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.telos_close_window)) },
                            onClick = {
                                showTaskMenu = false
                                scope.launch {
                                    windowManager.closeTask(task.taskId)
                                }
                            },
                            leadingIcon = { Icon(painterResource(R.drawable.close_24px), null) }
                        )
                    }
                    // === TELOS_PENDING_REVIEW_END: desktop_window_snapping ===
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
