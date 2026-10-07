package de.mm20.launcher2.ui.screenshot

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation3.runtime.NavKey
import coil.compose.AsyncImage
import de.mm20.launcher2.globalactions.GlobalActionsService
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.ListPreference
import de.mm20.launcher2.ui.component.preferences.ListPreferenceItem
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import de.mm20.launcher2.ui.locals.LocalBackStack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

@Serializable
data object ScreenshotRoute : NavKey

@Serializable
data object ScreenshotSettingsRoute : NavKey

/**
 * Telos Screenshot: the three ways to take a screenshot, and the screenshots that Telos made.
 * Tapping one of them takes the picture of what was on screen, so the app steps aside first.
 */
@Composable
fun ScreenshotScreen() {
    val context = LocalContext.current
    val backStack = LocalBackStack.current
    val actions: GlobalActionsService = koinInject()
    var running by remember { mutableStateOf(actions.isAccessibilityRunning()) }
    var reload by remember { mutableStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        running = actions.isAccessibilityRunning()
        reload++
    }
    val shots by produceState(emptyList<SavedScreenshot>(), reload) {
        value = withContext(Dispatchers.IO) { runCatching { ScreenshotStore.list(context) }.getOrDefault(emptyList()) }
    }

    fun capture(block: () -> Unit) {
        block()
        // step aside, so that the picture shows what was behind this screen
        (context as? Activity)?.moveTaskToBack(true)
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).systemBarsPadding()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.screenshot_title),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f).padding(start = 8.dp, top = 16.dp, bottom = 8.dp),
                    )
                    IconButton(onClick = { backStack.add(ScreenshotSettingsRoute) }) {
                        Icon(painterResource(R.drawable.settings_24px), contentDescription = stringResource(R.string.voice_settings))
                    }
                }
            }
            if (!running) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.errorContainer).padding(16.dp),
                    ) {
                        Text(stringResource(R.string.screenshot_accessibility_banner), color = MaterialTheme.colorScheme.onErrorContainer)
                        Button(
                            onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
                            modifier = Modifier.padding(top = 8.dp),
                        ) { Text(stringResource(R.string.screenshot_open_accessibility)) }
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                    CaptureCard(R.drawable.ic_sidebar_screenshot, R.string.screenshot_full, R.string.screenshot_full_summary) {
                        capture { ScreenshotController.captureFull(context) }
                    }
                    CaptureCard(R.drawable.ic_sidebar_partial_screenshot, R.string.screenshot_partial, R.string.screenshot_partial_summary) {
                        capture { ScreenshotController.capturePartial(context) }
                    }
                    CaptureCard(R.drawable.ic_sidebar_scrolling_screenshot, R.string.screenshot_scrolling, R.string.screenshot_scrolling_summary) {
                        capture { ScreenshotController.captureScrolling(context) }
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    stringResource(R.string.screenshot_screenshots),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 4.dp),
                )
            }
            if (shots.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(stringResource(R.string.screenshot_none), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
                }
            }
            items(shots, key = { it.uri.toString() }) { shot ->
                AsyncImage(
                    model = shot.uri,
                    contentDescription = shot.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .aspectRatio(0.62f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable { ScreenshotEditorActivity.open(context, shot.uri) },
                )
            }
        }
    }
}

@Composable
private fun CaptureCard(icon: Int, title: Int, summary: Int, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Color(0xFF1A6DFF)), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(26.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The settings of Telos Screenshot */
@Composable
fun ScreenshotSettingsScreen() {
    val context = LocalContext.current
    val settings = remember { ScreenshotSettings(context) }
    var format by remember { mutableStateOf(settings.format) }
    var delay by remember { mutableStateOf(settings.delay) }
    var notify by remember { mutableStateOf(settings.notify) }

    PreferenceScreen(title = stringResource(R.string.voice_settings)) {
        item {
            PreferenceCategory(title = stringResource(R.string.screenshot_settings_capture)) {
                ListPreference(
                    title = stringResource(R.string.screenshot_format),
                    items = listOf(ListPreferenceItem("PNG", ScreenshotFormat.Png), ListPreferenceItem("JPEG", ScreenshotFormat.Jpeg)),
                    value = format,
                    onValueChanged = { format = it; settings.format = it },
                )
                ListPreference(
                    title = stringResource(R.string.screenshot_delay),
                    items = listOf(
                        ListPreferenceItem(stringResource(R.string.screenshot_delay_none), 0),
                        ListPreferenceItem("3 s", 3), ListPreferenceItem("5 s", 5), ListPreferenceItem("10 s", 10),
                    ),
                    value = delay,
                    onValueChanged = { delay = it; settings.delay = it },
                )
                SwitchPreference(
                    title = stringResource(R.string.screenshot_notify),
                    summary = stringResource(R.string.screenshot_notify_summary),
                    value = notify,
                    onValueChanged = { notify = it; settings.notify = it },
                )
            }
        }
        item {
            PreferenceCategory(title = stringResource(R.string.voice_settings_privacy)) {
                Text(stringResource(R.string.screenshot_privacy), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
            }
        }
    }
}
