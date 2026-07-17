package de.mm20.launcher2.ui.desktopmode

import android.app.ActivityOptions
import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.view.Display
import android.view.WindowManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.desktopmode.DesktopModeManager
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.preferences.ui.DesktopModeSettings
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.ShapedLauncherIcon
import de.mm20.launcher2.ui.ktx.toPixels
import kotlinx.coroutines.flow.Flow
import org.koin.compose.koinInject

@Composable
internal fun DesktopStartMenu(onDismiss: () -> Unit) {
    val viewModel: DesktopStartMenuVM = viewModel()
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val desktopModeManager = koinInject<DesktopModeManager>()
    val freeformActive by desktopModeManager.freeformActiveInSystem.collectAsStateWithLifecycle()
    val desktopModeSettings = koinInject<DesktopModeSettings>()
    val iconSize by desktopModeSettings.gridIconSize.collectAsStateWithLifecycle(48)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onDismiss() })
            },
        contentAlignment = Alignment.BottomStart,
    ) {
        Surface(
            modifier = Modifier
                .padding(start = 8.dp, bottom = DesktopTaskbarHeight + 8.dp)
                .width(320.dp)
                .fillMaxHeight(0.7f)
                .clip(RoundedCornerShape(12.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
            tonalElevation = 6.dp,
            shadowElevation = 6.dp,
        ) {
            if (apps.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(24.dp)) {
                    Text(
                        text = stringResource(R.string.desktop_mode_start_menu_empty),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(apps, key = { it.key }) { app ->
                        StartMenuAppRow(
                            app = app,
                            iconSize = iconSize.dp,
                            getIcon = { size -> viewModel.getIcon(app, size) },
                            onClick = {
                                launchOnDisplay(context, app, freeformActive)
                                onDismiss()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StartMenuAppRow(
    app: Application,
    iconSize: Dp,
    getIcon: (Int) -> Flow<LauncherIcon?>,
    onClick: () -> Unit,
) {
    val iconSizePx = iconSize.toPixels().toInt()
    val icon by remember(app.key, iconSizePx) { getIcon(iconSizePx) }.collectAsStateWithLifecycle(null)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ShapedLauncherIcon(size = iconSize, icon = { icon })
        Text(
            text = app.labelOverride ?: app.label,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private var freeformLaunchCount = 0

/**
 * Launches [app] onto the same display this composable is currently shown on. When
 * [freeformActive] is true (the OS-level `enable_freeform_support` setting is on), also hints a
 * cascading window size/position via [ActivityOptions.setLaunchBounds] - this is a plain public
 * API, no hidden APIs involved. If freeform isn't active, setLaunchBounds is never called and the
 * OS falls back to launching the app fullscreen on the external display, which is the intended
 * degrade path when freeform isn't available.
 */
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
        display.displayId
    } else {
        @Suppress("DEPRECATION")
        (getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.defaultDisplay?.displayId
            ?: Display.DEFAULT_DISPLAY
    }
}
