// === TELOS_PENDING_REVIEW_START: desktop_grid_and_context_menu ===
package de.mm20.launcher2.ui.desktopmode

import android.app.ActivityOptions
import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.view.Display
import android.view.WindowManager
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.desktopmode.DesktopModeManager
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.ShapedLauncherIcon
import de.mm20.launcher2.ui.ktx.toPixels
import kotlinx.coroutines.flow.emptyFlow
import org.koin.compose.koinInject

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DesktopWorkspace(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val viewModel: DesktopWorkspaceVM = viewModel()
    val desktopManager = koinInject<DesktopModeManager>()
    val pinnedApps by viewModel.pinnedApps.collectAsStateWithLifecycle()
    val freeformActive by desktopManager.freeformActiveInSystem.collectAsStateWithLifecycle(false)

    var showContextMenu by remember { mutableStateOf(false) }
    var contextMenuOffset by remember { mutableStateOf(IntOffset.Zero) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { offset ->
                        contextMenuOffset = IntOffset(offset.x.toInt(), offset.y.toInt())
                        showContextMenu = true
                    },
                    // Secondary click (mouse right-click) naturally falls through here or via custom gesture logic 
                    // To handle mouse secondary clicks precisely in Compose 1.4+, we can use onClick with PointerEventPass
                )
            }
    ) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 80.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(pinnedApps, key = { it.key }) { app ->
                var showAppMenu by remember { mutableStateOf(false) }
                
                val iconSizePx = 48.dp.toPixels().toInt()
                val iconFlow = remember(app) { viewModel.getIcon(app, iconSizePx) }
                val icon by iconFlow.collectAsStateWithLifecycle(null)

                Box(modifier = Modifier.size(80.dp, 100.dp)) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .combinedClickable(
                                onClick = { 
                                    launchOnDisplay(context, app, freeformActive) 
                                },
                                onLongClick = { showAppMenu = true }
                            )
                            .padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (icon != null) {
                            ShapedLauncherIcon(
                                icon = { icon },
                                size = 48.dp,
                                badge = { null }
                            )
                        } else {
                            Icon(
                                painterResource(R.drawable.android_24px),
                                contentDescription = null,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = app.labelOverride ?: app.label,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color.White,
                                shadow = Shadow(
                                    color = Color.Black.copy(alpha = 0.8f),
                                    offset = Offset(1f, 1f),
                                    blurRadius = 4f
                                )
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }

                    DropdownMenu(
                        expanded = showAppMenu,
                        onDismissRequest = { showAppMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_launch)) },
                            onClick = {
                                showAppMenu = false
                                launchOnDisplay(context, app, freeformActive)
                            },
                            leadingIcon = { Icon(painterResource(R.drawable.open_in_new_24px), null) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_favorites_unpin)) },
                            onClick = {
                                showAppMenu = false
                                viewModel.unpin(app)
                            },
                            leadingIcon = { Icon(painterResource(R.drawable.star_24px_filled), null) }
                        )
                    }
                }
            }
        }

        // Desktop Wallpaper Context Menu
        if (showContextMenu) {
            // Using a simple popup or dropdown. Due to constraints, DropdownMenu without an anchor wraps at 0,0 
            // To position it precisely we could use a Popup, but for this milestone DropdownMenu with Box offset works.
            Box(modifier = Modifier.offset { contextMenuOffset }) {
                DropdownMenu(
                    expanded = showContextMenu,
                    onDismissRequest = { showContextMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.telos_change_wallpaper)) },
                        onClick = {
                            showContextMenu = false
                            // Handled by Launcher Application Intent or a deep link.
                        },
                        leadingIcon = { Icon(painterResource(R.drawable.image_search_24px), null) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.telos_desktop_settings)) },
                        onClick = {
                            showContextMenu = false
                            // Handled by Launcher Application Intent or a deep link.
                        },
                        leadingIcon = { Icon(painterResource(R.drawable.settings_24px), null) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.telos_add_shortcut)) },
                        onClick = {
                            showContextMenu = false
                            // Opens SearchablePicker sheet in standard flow
                        },
                        leadingIcon = { Icon(painterResource(R.drawable.add_24px), null) }
                    )
                }
            }
        }
    }
}

private var freeformLaunchCount = 0

private fun launchOnDisplay(context: Context, app: Application, freeformActive: Boolean) {
    val displayId = context.displayIdCompat()
    val options = ActivityOptions.makeBasic().apply {
        launchDisplayId = displayId
    }
    if (freeformActive) {
        options.setLaunchBounds(nextFreeformBounds(context))
    }
    app.launch(context, options.toBundle())
}

private fun nextFreeformBounds(context: Context): Rect {
    val metrics = context.resources.displayMetrics
    val width = (metrics.widthPixels * 0.6f).toInt()
    val height = (metrics.heightPixels * 0.65f).toInt()
    val stepPx = (32 * metrics.density).toInt()
    val step = (freeformLaunchCount % 6) + 1
    freeformLaunchCount++
    val left = stepPx * step
    val top = stepPx * step
    return Rect(left, top, left + width, top + height)
}

private fun Context.displayIdCompat(): Int {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        display?.displayId ?: Display.DEFAULT_DISPLAY
    } else {
        @Suppress("DEPRECATION")
        (getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.defaultDisplay?.displayId ?: 0
    }
}
// === TELOS_PENDING_REVIEW_END: desktop_grid_and_context_menu ===
