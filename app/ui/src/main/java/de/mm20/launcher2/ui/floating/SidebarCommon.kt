package de.mm20.launcher2.ui.floating

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.net.Uri
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import de.mm20.launcher2.applications.AppRepository
import de.mm20.launcher2.contextprofiles.ContextProfileManager
import de.mm20.launcher2.freeze.FreezeManager
import de.mm20.launcher2.icons.IconService
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.preferences.ContextProfileIcon
import de.mm20.launcher2.preferences.FloatingLauncherFolder
import de.mm20.launcher2.preferences.FloatingLauncherZone
import de.mm20.launcher2.preferences.FloatingLauncherZoneConfig
import de.mm20.launcher2.preferences.ui.FloatingLauncherSettings
import de.mm20.launcher2.preferences.ui.ShutterSettings
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.searchable.SavableSearchableRepository
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.ShapedLauncherIcon
import de.mm20.launcher2.ui.component.dragndrop.DraggableItem
import de.mm20.launcher2.ui.component.dragndrop.LazyVerticalDragAndDropGrid
import de.mm20.launcher2.ui.component.dragndrop.rememberLazyDragAndDropGridState
import de.mm20.launcher2.ui.launcher.shutters.ShutterGate
import de.mm20.launcher2.ui.overlays.OverlayHost
import de.mm20.launcher2.ui.settings.SettingsActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.app.ActivityOptions
import android.graphics.Rect
import android.provider.Settings
import android.view.ContextThemeWrapper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import de.mm20.launcher2.globalactions.GlobalActionsService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import android.content.ContentUris
import android.os.Bundle
import android.provider.MediaStore
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.painter.Painter
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt
import org.koin.android.ext.android.inject
import androidx.compose.foundation.layout.aspectRatio
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import de.mm20.launcher2.widgets.Widget
import de.mm20.launcher2.widgets.WidgetRepository

/** How the sidebar looks and behaves, from the settings. Provided once by the panel. */
internal data class SidebarStyle(
    val iconSize: Dp = 48.dp,
    val showLabels: Boolean = true,
    val panelAlpha: Float = 0.85f,
    val floatingWindows: Boolean = true,
    val columns: Int = 2,
    val fileDock: Boolean = true,
) {
    /** Width and height of one cell: the icon plus room for the name */
    val cell: Dp get() = iconSize + if (showLabels) 36.dp else 24.dp
}

internal val LocalSidebarStyle = compositionLocalOf { SidebarStyle() }

@Composable
internal fun sidebarCell(): Dp = LocalSidebarStyle.current.cell

/** The tools that can be put into the sidebar, like the tools of the OxygenOS Smart Sidebar */
internal enum class SidebarTool(
    val id: String,
    val label: Int,
    val icon: Int,
    val color: Color,
) {
    Screenshot("screenshot", R.string.floating_launcher_tool_screenshot, R.drawable.ic_sidebar_screenshot, Color(0xFF1A6DFF)),
    PartialScreenshot("partial_screenshot", R.string.floating_launcher_tool_partial_screenshot, R.drawable.ic_sidebar_partial_screenshot, Color(0xFF1A6DFF)),
    ScrollingScreenshot("scrolling_screenshot", R.string.floating_launcher_tool_scrolling_screenshot, R.drawable.ic_sidebar_scrolling_screenshot, Color(0xFF1A6DFF)),
    ScreenRecorder("screen_recorder", R.string.floating_launcher_tool_screen_recorder, R.drawable.ic_glyph_screen_recorder, Color(0xFFE64A19)),
    VoiceRecorder("voice_recorder", R.string.floating_launcher_tool_voice_recorder, R.drawable.ic_glyph_voice_recorder, Color(0xFF00838F)),
    RecentFiles("recent_files", R.string.floating_launcher_recent_files, R.drawable.schedule_24px, Color(0xFF3D8BFF)),
    Flashlight("flashlight", R.string.floating_launcher_tool_flashlight, R.drawable.bolt_24px, Color(0xFFF59E0B)),
    QuickSettings("quick_settings", R.string.floating_launcher_tool_quick_settings, R.drawable.tune_24px, Color(0xFF7C4DFF)),
    Notifications("notifications", R.string.floating_launcher_tool_notifications, R.drawable.notifications_24px, Color(0xFF00897B)),
    LockScreen("lock_screen", R.string.floating_launcher_tool_lock_screen, R.drawable.lock_24px, Color(0xFF546E7A)),
    PowerMenu("power_menu", R.string.floating_launcher_tool_power_menu, R.drawable.power_settings_new_24px, Color(0xFFD32F2F));

    val key: String get() = KEY_PREFIX + id

    companion object {
        const val KEY_PREFIX = "tool:"
        fun fromKey(key: String): SidebarTool? =
            if (key.startsWith(KEY_PREFIX)) entries.firstOrNull { it.id == key.removePrefix(KEY_PREFIX) } else null
    }
}

/** Widgets of the sidebar are stored with the other widgets, under this parent */
internal val SIDEBAR_WIDGET_PARENT: UUID = UUID.nameUUIDFromBytes("telos-smart-sidebar".toByteArray())
internal const val WIDGET_KEY_PREFIX = "widget:"

/** One thing in the sidebar */
internal sealed interface SidebarEntry {
    val key: String

    data class App(val item: SavableSearchable) : SidebarEntry {
        override val key get() = item.key
    }

    data class Tool(val tool: SidebarTool) : SidebarEntry {
        override val key get() = tool.key
    }

    data class WidgetEntry(val widget: Widget) : SidebarEntry {
        override val key get() = WIDGET_KEY_PREFIX + widget.id
    }
}

/** Turns the stored keys into what can be shown. Things that no longer exist (an uninstalled app) are left out. */
@Composable
internal fun rememberSidebarEntries(
    keys: List<String>,
    searchableRepository: SavableSearchableRepository,
    widgetRepository: WidgetRepository,
): List<SidebarEntry> {
    val appKeys = remember(keys) {
        keys.filter { !it.startsWith(SidebarTool.KEY_PREFIX) && !it.startsWith(WIDGET_KEY_PREFIX) }
    }
    val apps by remember(appKeys) { searchableRepository.getByKeys(appKeys) }.collectAsState(emptyList())
    val widgets by remember { widgetRepository.get(SIDEBAR_WIDGET_PARENT) }.collectAsState(emptyList())
    return remember(keys, apps, widgets) {
        keys.mapNotNull { key ->
            SidebarTool.fromKey(key)?.let { return@mapNotNull SidebarEntry.Tool(it) }
            if (key.startsWith(WIDGET_KEY_PREFIX)) {
                val id = key.removePrefix(WIDGET_KEY_PREFIX)
                widgets.firstOrNull { it.id.toString() == id }?.let { SidebarEntry.WidgetEntry(it) }
            } else {
                apps.firstOrNull { it.key == key }?.let { SidebarEntry.App(it) }
            }
        }
    }
}

internal var floatingWindowCount = 0

/**
 * Opens [item]. With "floating windows" on and Android's freeform mode active (Settings > Desktop
 * mode > Floating windows, or the developer option), the app opens as a window on top of the app in
 * front; without freeform mode Android ignores the window size and the app opens normally.
 */
internal fun launchInSidebar(context: Context, item: SavableSearchable, floatingWindow: Boolean) {
    val options = if (floatingWindow && isFreeformActive(context)) {
        ActivityOptions.makeBasic().apply { setLaunchBounds(nextFloatingWindowBounds(context)) }.toBundle()
    } else {
        null
    }
    item.launch(context, options)
}

internal fun isFreeformActive(context: Context): Boolean = try {
    Settings.Global.getInt(context.contentResolver, "enable_freeform_support", 0) == 1
} catch (_: Exception) {
    false
}

internal fun nextFloatingWindowBounds(context: Context): Rect {
    val metrics = context.resources.displayMetrics
    val width = (metrics.widthPixels * 0.78f).toInt()
    val height = (metrics.heightPixels * 0.6f).toInt()
    val step = (24 * metrics.density).toInt() * (floatingWindowCount % 5)
    floatingWindowCount++
    val left = (metrics.widthPixels - width) / 2 + step
    val top = (metrics.heightPixels - height) / 3 + step
    return Rect(left, top, left + width, top + height)
}

/** A tool as a rounded square with a colored background, like the tools in the Smart Sidebar */
@Composable
internal fun SidebarToolIcon(tool: SidebarTool, size: Dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(tool.color),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(tool.icon), contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.55f))
    }
}

@Composable
internal fun SidebarEditIcon(size: Dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.edit_24px), contentDescription = null, modifier = Modifier.size(size * 0.5f))
    }
}

/** The "All" tile: four small tool icons */
@Composable
internal fun SidebarAllToolsIcon(size: Dp) {
    val mini = size * 0.38f
    val tools = listOf(SidebarTool.Screenshot, SidebarTool.ScreenRecorder, SidebarTool.PartialScreenshot, SidebarTool.VoiceRecorder)
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(size * 0.06f)) {
            for (row in tools.chunked(2)) {
                Row(horizontalArrangement = Arrangement.spacedBy(size * 0.06f)) {
                    for (tool in row) SidebarCircleIcon(tool.icon, tool.color, mini)
                }
            }
        }
    }
}

/** The list opened by the "All" tile: every tool */
@Composable
internal fun SidebarToolsOverlay(onTool: (SidebarTool) -> Unit, onDismiss: () -> Unit) {
    SidebarSheet(title = stringResource(R.string.floating_launcher_tools_all), onDismiss = onDismiss) {
        val style = LocalSidebarStyle.current
        for (row in SidebarTool.entries.chunked(3)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                for (tool in row) {
                    SidebarTile(stringResource(tool.label), { onTool(tool) }) { SidebarToolIcon(tool, style.iconSize) }
                }
                repeat(3 - row.size) { Spacer(Modifier.width(sidebarCell())) }
            }
        }
    }
}

/**
 * ClipData items dragged from this launcher's own app icons (GridItem.kt) carry this as their
 * clip label so drops onto the floating launcher can tell "one of our own app icons" apart from
 * arbitrary external content meant for the File Dock.
 */
internal const val APP_DRAG_CLIP_LABEL = "kvaesitso_app_icon"

/**
 * Something dragged onto the floating launcher from another app and held temporarily - OxygenOS
 * calls this the File Dock. Cleared whenever the service restarts (i.e. genuinely temporary, not
 * persisted to disk/settings), matching what "temporary storage" means there.
 */
internal sealed interface FileDockItem {
    val id: String

    data class TextItem(override val id: String, val text: String) : FileDockItem

    /** [uri] always points at our own FileProvider-backed cache copy, never the original
     * dragged-from-app URI - the permission grant on that one is tied to the drag gesture and
     * isn't guaranteed to still be valid by the time the user taps this later. */
    data class MediaItem(
        override val id: String,
        val uri: Uri,
        val mimeType: String?,
        val label: String,
    ) : FileDockItem
}

/**
 * Copies [sourceUri]'s content into this app's cache dir and returns a FileProvider URI pointing
 * at that copy. Needed because the URI permission grant that comes with a drag-and-drop only
 * covers the drag itself - reading it later (when the user taps a File Dock item) can't rely on
 * that grant still being valid, so this makes an independent copy we own outright while the grant
 * is still fresh.
 */
internal suspend fun copyToDockCache(context: Context, sourceUri: Uri, mimeType: String?): Uri? =
    withContext(Dispatchers.IO) {
        try {
            val dockDir = File(context.cacheDir, "floating_launcher_dock").apply { mkdirs() }
            val extension = mimeType?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
            val file = File(dockDir, buildString {
                append(UUID.randomUUID().toString())
                if (extension != null) append(".").append(extension)
            })
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext null
            FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        } catch (e: Exception) {
            null
        }
    }


/**
 * A single cell in the panel's grid. Icons-only outside edit mode, matching the default OxygenOS
 * Smart Sidebar look; in edit mode it also shows its label (so a bare icon isn't ambiguous while
 * rearranging) and a small remove badge, and stops launching on tap since a tap there means
 * "grab to drag" instead (handled by the drag grid this is placed inside).
 */
@Composable
internal fun FavoriteIcon(
    item: SavableSearchable,
    iconService: IconService,
    appRepository: AppRepository,
    shutterSettings: ShutterSettings,
    editMode: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    val icon by remember(item.key) { iconService.getIcon(item, 48) }.collectAsState(null)
    // Reflects live package-suspended state, so it updates immediately on freeze/unfreeze -
    // same source as the frozen-icon tint elsewhere (search results, favorites).
    val isFrozen by remember(item.key) {
        if (item is Application) {
            appRepository.findOne(item.componentName.packageName, item.user)
                .map { it?.isSuspended == true }
        } else {
            emptyFlow()
        }
    }.collectAsState(false)

    val shuttersEnabled by shutterSettings.enabled.collectAsState(false)
    val shutterKey by remember(item.key) {
        if (item is Application) shutterSettings.appFor(item.componentName.packageName)
        else flowOf(null)
    }.collectAsState(null)
    var showShutter by remember(item.key) { mutableStateOf(false) }
    val hapticFeedback = LocalHapticFeedback.current

    val style = LocalSidebarStyle.current
    Box(modifier = Modifier.size(sidebarCell())) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onClick),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            ShapedLauncherIcon(
                size = if (editMode) style.iconSize * 0.75f else style.iconSize,
                icon = { icon },
                grayscale = isFrozen,
            )
            if (editMode || style.showLabels) {
                Text(
                    text = item.labelOverride ?: item.label,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp),
                )
            }
        }
        if (editMode) {
            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.errorContainer),
            ) {
                Icon(
                    painterResource(R.drawable.close_20px),
                    contentDescription = stringResource(R.string.floating_launcher_remove_app),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }

    if (showShutter && item is Application) {
        ShutterGate(
            packageName = item.componentName.packageName,
            shutterKey = shutterKey,
            onDismiss = { showShutter = false },
        )
    }
}


/**
 * The File Dock's contents: text and media dragged onto the floating launcher from other apps,
 * held temporarily. Tapping a text item copies it to the clipboard; tapping a media item opens
 * a share sheet for it (there's no general way to "paste" into whatever app happens to be in the
 * foreground from here, unlike a real in-app clipboard).
 */
@Composable
internal fun FileDockOverlay(
    items: List<FileDockItem>,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.3f))
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onDismiss() })
            },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 320.dp)
                .heightIn(max = sidebarCell() * 6)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
            shadowElevation = 8.dp,
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.floating_launcher_file_dock),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            painterResource(R.drawable.close_24px),
                            contentDescription = stringResource(R.string.close),
                        )
                    }
                }
                if (items.isEmpty()) {
                    Text(
                        text = stringResource(R.string.floating_launcher_file_dock_empty),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                } else {
                    LazyColumn {
                        items(items, key = { it.id }) { dockItem ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        when (dockItem) {
                                            is FileDockItem.TextItem -> {
                                                coroutineScope.launch {
                                                    clipboard.setClipEntry(
                                                        ClipEntry(ClipData.newPlainText(null, dockItem.text))
                                                    )
                                                }
                                                Toast.makeText(
                                                    context,
                                                    context.getString(R.string.floating_launcher_file_dock_copied),
                                                    Toast.LENGTH_SHORT,
                                                ).show()
                                            }

                                            is FileDockItem.MediaItem -> {
                                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                    type = dockItem.mimeType ?: "*/*"
                                                    putExtra(Intent.EXTRA_STREAM, dockItem.uri)
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                                context.startActivity(
                                                    Intent.createChooser(shareIntent, dockItem.label).apply {
                                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                Icon(
                                    painterResource(
                                        when (dockItem) {
                                            is FileDockItem.TextItem -> R.drawable.description_24px
                                            is FileDockItem.MediaItem -> R.drawable.attach_file_24px
                                        }
                                    ),
                                    contentDescription = null,
                                )
                                Text(
                                    text = when (dockItem) {
                                        is FileDockItem.TextItem -> dockItem.text
                                        is FileDockItem.MediaItem -> dockItem.label
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(
                                    onClick = { onRemove(dockItem.id) },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        painterResource(R.drawable.close_20px),
                                        contentDescription = stringResource(R.string.floating_launcher_remove_app),
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


/** A full-width rounded button of the card, like "File Dock" and "Recent files" in the Smart Sidebar */
@Composable
internal fun SidebarWideButton(icon: Int, label: Int, highlighted: Boolean = false, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                painterResource(icon),
                contentDescription = null,
                tint = if (highlighted) MaterialTheme.colorScheme.primary else Color(0xFF3D8BFF),
                modifier = Modifier.size(28.dp),
            )
            Text(
                text = stringResource(label),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}


/** An icon with a label under it, the size of one app cell */
@Composable
internal fun SidebarTile(label: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .width(sidebarCell())
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        icon()
        if (LocalSidebarStyle.current.showLabels) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}


@Composable
internal fun SidebarCircleIcon(icon: Int, color: Color, size: Dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(size * 0.55f),
        )
    }
}


/** A centered sheet over the scrim, used by the tools list and the recent files list */
@Composable
internal fun SidebarSheet(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.3f))
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onDismiss() })
            },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 320.dp)
                .heightIn(max = 520.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
            shadowElevation = 8.dp,
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) {
                        Icon(
                            painterResource(R.drawable.close_24px),
                            contentDescription = stringResource(R.string.close),
                        )
                    }
                }
                content()
            }
        }
    }
}


private data class RecentFile(val uri: Uri, val name: String, val mimeType: String, val modified: Long)

/**
 * The newest files on the phone, like "Recent files" in the Smart Sidebar. Reads the system's
 * media index, so it needs the access to files that Telos Files asks for.
 */
private fun queryRecentFiles(context: Context): List<RecentFile> {
    val collection = MediaStore.Files.getContentUri("external")
    val projection = arrayOf(
        MediaStore.Files.FileColumns._ID,
        MediaStore.Files.FileColumns.DISPLAY_NAME,
        MediaStore.Files.FileColumns.MIME_TYPE,
        MediaStore.Files.FileColumns.DATE_MODIFIED,
    )
    val args = Bundle().apply {
        putString(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION, "${MediaStore.Files.FileColumns.MIME_TYPE} IS NOT NULL")
        putStringArray(android.content.ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(MediaStore.Files.FileColumns.DATE_MODIFIED))
        putInt(android.content.ContentResolver.QUERY_ARG_SORT_DIRECTION, android.content.ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
        putInt(android.content.ContentResolver.QUERY_ARG_LIMIT, 30)
    }
    val result = mutableListOf<RecentFile>()
    context.contentResolver.query(collection, projection, args, null)?.use { cursor ->
        while (cursor.moveToNext()) {
            val id = cursor.getLong(0)
            val name = cursor.getString(1) ?: continue
            val mime = cursor.getString(2) ?: continue
            result += RecentFile(ContentUris.withAppendedId(collection, id), name, mime, cursor.getLong(3) * 1000)
        }
    }
    return result
}

@Composable
internal fun RecentFilesOverlay(onOpened: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val files by produceState<List<RecentFile>?>(null) {
        value = withContext(Dispatchers.IO) { runCatching { queryRecentFiles(context) }.getOrDefault(emptyList()) }
    }
    SidebarSheet(title = stringResource(R.string.floating_launcher_recent_files), onDismiss = onDismiss) {
        val list = files
        when {
            list == null -> Unit
            list.isEmpty() -> Text(
                text = stringResource(R.string.floating_launcher_recent_files_empty),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            else -> LazyColumn {
                items(list, key = { it.uri.toString() }) { file ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(file.uri, file.mimeType)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                try {
                                    context.startActivity(intent)
                                    onOpened()
                                } catch (_: Exception) {
                                    Toast.makeText(context, R.string.floating_launcher_recent_files_no_app, Toast.LENGTH_SHORT).show()
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(file.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT)
                                .format(java.util.Date(file.modified)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
