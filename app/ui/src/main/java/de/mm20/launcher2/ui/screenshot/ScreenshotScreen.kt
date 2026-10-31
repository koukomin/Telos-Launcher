package de.mm20.launcher2.ui.screenshot
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation3.runtime.NavKey
import coil.compose.AsyncImage
import de.mm20.launcher2.globalactions.GlobalActionsService
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.common.share.ShareActions
import de.mm20.launcher2.ui.component.SearchEmptyState
import de.mm20.launcher2.ui.component.TelosSearchBar
import de.mm20.launcher2.ui.component.preferences.ListPreference
import de.mm20.launcher2.ui.component.preferences.ListPreferenceItem
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import de.mm20.launcher2.ui.locals.LocalBackStack
import de.mm20.launcher2.ui.theme.transparency.transparency
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ScreenshotScreen() {
    val context = LocalContext.current
    val backStack = LocalBackStack.current
    val scope = rememberCoroutineScope()
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

    var shotQuery by rememberSaveable { mutableStateOf("") }
    var selectedShots by remember { mutableStateOf<Set<Uri>>(emptySet()) }
    val selecting = selectedShots.isNotEmpty()
    BackHandler(enabled = selecting) { selectedShots = emptySet() }
    val shownShots = remember(shots, shotQuery) {
        val df = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT)
        de.mm20.launcher2.comms.search.TelosSearch.filter(shots, shotQuery) { listOf(it.name, df.format(java.util.Date(it.modified))) }
    }

    fun capture(block: () -> Unit) {
        block()
        // step aside, so that the picture shows what was behind this screen
        (context as? Activity)?.moveTaskToBack(true)
    }

    fun shareSelected() {
        val uris = shots.asSequence().map { it.uri }.filter { it in selectedShots }.toList()
        scope.launch {
            val shared = withContext(Dispatchers.IO) {
                ShareActions.shareUris(context, uris, "image/*")
            }
            if (!shared) {
                Toast.makeText(context, I18nR.string.au20_viewer_share_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    de.mm20.launcher2.ui.component.VirtualAppWindow {
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(120.dp * androidx.compose.ui.platform.LocalDensity.current.fontScale),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(I18nR.string.screenshot_title),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f).padding(start = 8.dp, top = 16.dp, bottom = 8.dp),
                    )
                    IconButton(onClick = { backStack.add(ScreenshotSettingsRoute) }) {
                        Icon(painterResource(BaseR.drawable.settings_24px), contentDescription = stringResource(I18nR.string.voice_settings))
                    }
                }
            }
            if (!running) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.errorContainer).padding(16.dp),
                    ) {
                        Text(stringResource(I18nR.string.screenshot_accessibility_banner), color = MaterialTheme.colorScheme.onErrorContainer)
                        Button(
                            onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
                            modifier = Modifier.padding(top = 8.dp),
                        ) { Text(stringResource(I18nR.string.screenshot_open_accessibility)) }
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                    CaptureCard(BaseR.drawable.ic_sidebar_screenshot, I18nR.string.screenshot_full, I18nR.string.screenshot_full_summary) {
                        capture { ScreenshotController.captureFull(context) }
                    }
                    CaptureCard(BaseR.drawable.ic_sidebar_partial_screenshot, I18nR.string.screenshot_partial, I18nR.string.screenshot_partial_summary) {
                        capture { ScreenshotController.capturePartial(context) }
                    }
                    CaptureCard(BaseR.drawable.ic_sidebar_scrolling_screenshot, I18nR.string.screenshot_scrolling, I18nR.string.screenshot_scrolling_summary) {
                        capture { ScreenshotController.captureScrolling(context) }
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    stringResource(I18nR.string.screenshot_screenshots),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 4.dp),
                )
            }
            if (shots.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(stringResource(I18nR.string.screenshot_none), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
                }
            } else {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    TelosSearchBar(shotQuery, { shotQuery = it }, stringResource(I18nR.string.tsp_search_screenshots), Modifier.padding(horizontal = 0.dp))
                }
                if (shownShots.isEmpty() && shotQuery.isNotBlank()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { SearchEmptyState(shotQuery) }
                }
            }
            items(shownShots, key = { it.uri.toString() }) { shot ->
                val isSelected = shot.uri in selectedShots
                Box(
                    Modifier
                        .aspectRatio(0.62f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .combinedClickable(
                            onClick = {
                                selectedShots = if (selecting) {
                                    if (isSelected) selectedShots - shot.uri else selectedShots + shot.uri
                                } else {
                                    ScreenshotEditorActivity.open(context, shot.uri)
                                    selectedShots
                                }
                            },
                            onLongClick = {
                                selectedShots = if (isSelected) selectedShots - shot.uri else selectedShots + shot.uri
                            },
                        )
                        .semantics { selected = isSelected },
                ) {
                    AsyncImage(
                        model = shot.uri,
                        contentDescription = shot.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (isSelected) {
                        Box(
                            Modifier
                                .matchParentSize()
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                            contentAlignment = Alignment.TopEnd,
                        ) {
                            Text("✓", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(8.dp))
                        }
                    }
                }
            }
        }
        if (selecting) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(I18nR.string.au_viewer_items, selectedShots.size),
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                IconButton(onClick = ::shareSelected) {
                    Icon(painterResource(BaseR.drawable.share_24px), contentDescription = stringResource(I18nR.string.hc_share))
                }
                IconButton(onClick = { selectedShots = emptySet() }) {
                    Icon(painterResource(BaseR.drawable.close_24px), contentDescription = stringResource(I18nR.string.hc_cancel))
                }
            }
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
        Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(26.dp))
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

    PreferenceScreen(title = stringResource(I18nR.string.voice_settings)) {
        item {
            PreferenceCategory(title = stringResource(I18nR.string.screenshot_settings_capture)) {
                ListPreference(
                    title = stringResource(I18nR.string.screenshot_format),
                    items = listOf(ListPreferenceItem("PNG", ScreenshotFormat.Png), ListPreferenceItem("JPEG", ScreenshotFormat.Jpeg)),
                    value = format,
                    onValueChanged = { format = it; settings.format = it },
                )
                ListPreference(
                    title = stringResource(I18nR.string.screenshot_delay),
                    items = listOf(
                        ListPreferenceItem(stringResource(I18nR.string.screenshot_delay_none), 0),
                        ListPreferenceItem(stringResource(I18nR.string.au_capture_seconds, 3), 3),
                        ListPreferenceItem(stringResource(I18nR.string.au_capture_seconds, 5), 5),
                        ListPreferenceItem(stringResource(I18nR.string.au_capture_seconds, 10), 10),
                    ),
                    value = delay,
                    onValueChanged = { delay = it; settings.delay = it },
                )
                SwitchPreference(
                    title = stringResource(I18nR.string.screenshot_notify),
                    summary = stringResource(I18nR.string.screenshot_notify_summary),
                    value = notify,
                    onValueChanged = { notify = it; settings.notify = it },
                )
            }
        }
        item {
            PreferenceCategory(title = stringResource(I18nR.string.voice_settings_privacy)) {
                Text(stringResource(I18nR.string.screenshot_privacy), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
            }
        }
    }
}
