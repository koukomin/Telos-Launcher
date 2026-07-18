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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import org.koin.android.ext.android.inject

/**
 * Foreground service hosting the floating quick launcher: up to six small tabs, one per
 * [FloatingLauncherZone] (each screen edge split into thirds, independently toggleable), each
 * expanding into its own scrollable grid of apps. Opt-in, off by default - started/stopped by the
 * settings screen when the user toggles it.
 *
 * Unlike the original single-tab version, the overlay window is always full-screen
 * (MATCH_PARENT) with FLAG_NOT_TOUCH_MODAL: every zone's tab and whichever panel is currently
 * expanded are positioned within one Compose tree via alignment, not by moving/resizing the
 * WindowManager view itself. Touches on empty areas of that fullscreen window are expected to
 * fall through to whatever's underneath, since nothing there claims them (no pointerInput/
 * clickable modifier covers those regions) - this is the standard "chat heads" overlay pattern,
 * but it's the one part of this whole redesign that most needs verifying on a real device: if
 * Compose's hit-testing doesn't let untouched regions pass through as expected here, the app
 * behind the overlay could become entirely untouchable.
 *
 * Deliberately does not include a search field: accepting text input in an overlay window means
 * making the window focusable, which briefly steals input focus from whatever app is in the
 * foreground. That interaction is hard to get right without a physical device to verify it on,
 * so this only offers each zone's curated app list.
 *
 * Each zone's tab and panel also accept a drop from a global (cross-window) Android drag started
 * elsewhere (home screen, app drawer - see GridItem.kt), carrying the dragged app's searchable
 * key as plain text. Whether WindowManager actually routes those DragEvents to this window (given
 * it's FLAG_NOT_TOUCH_MODAL) is, like the touch-passthrough behavior above, unverified without a
 * real device - there's real precedent for it working (this is the same mechanism chat-heads-style
 * bubbles use to accept a shared link), but it hasn't been tested here.
 *
 * The same drop target doubles as the entry point for the File Dock: dragging arbitrary text,
 * images, or files from another app (not one of this launcher's own app icons) holds them
 * temporarily instead of adding an app. Media items are copied into this app's own cache dir
 * (exposed via FileProvider) immediately on drop, rather than keeping the original dragged-from-app
 * URI - a drag's URI permission grant is only guaranteed to last for the drag itself, and a File
 * Dock item is by definition opened well after that, so hanging onto the original URI risks a
 * SecurityException the first time the user actually taps it.
 */
class FloatingLauncherService : Service(), SavedStateRegistryOwner {

    private val floatingLauncherSettings: FloatingLauncherSettings by inject()
    private val iconService: IconService by inject()
    private val searchableRepository: SavableSearchableRepository by inject()
    private val freezeManager: FreezeManager by inject()
    private val appRepository: AppRepository by inject()
    private val contextProfileManager: ContextProfileManager by inject()
    private val shutterSettings: ShutterSettings by inject()

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        startForeground(NOTIFICATION_ID, buildNotification())

        try {
            addOverlay()
        } catch (e: Exception) {
            // Permission revoked, or no window token available - nothing sensible to do but stop.
            stopSelf()
            return
        }

        scope.launch {
            floatingLauncherSettings.enabled.collect { enabled ->
                if (!enabled) stopSelf()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        val view = composeView
        val wm = windowManager
        if (view != null && wm != null) {
            try {
                wm.removeView(view)
            } catch (_: Exception) {
            }
        }
        composeView = null
        windowManager = null
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        scope.cancel()
        super.onDestroy()
    }

    private fun addOverlay() {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val view = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setViewTreeLifecycleOwner(this@FloatingLauncherService)
            setViewTreeSavedStateRegistryOwner(this@FloatingLauncherService)
        }
        composeView = view

        view.setContent {
            FloatingLauncherContent(
                settings = floatingLauncherSettings,
                iconService = iconService,
                searchableRepository = searchableRepository,
                freezeManager = freezeManager,
                appRepository = appRepository,
                contextProfileManager = contextProfileManager,
                shutterSettings = shutterSettings,
                onPanelExpandedChanged = { expanded -> updateBlurBehind(expanded) },
            )
        }

        wm.addView(view, buildLayoutParams(blurBehind = false))
    }

    /**
     * Blurs whatever's behind the overlay window while a panel is open, matching the OxygenOS
     * Smart Sidebar's frosted-glass look. Only meaningful on API 31+, where WindowManager exposes
     * a per-window blur radius - below that, FLAG_BLUR_BEHIND alone is an old, mostly-unsupported
     * flag on modern devices, so this is a no-op there and the panel instead relies on its own
     * translucent background color for the effect.
     */
    private fun updateBlurBehind(enabled: Boolean) {
        if (!isAtLeastApiLevel(31)) return
        val view = composeView ?: return
        val wm = windowManager ?: return
        try {
            wm.updateViewLayout(view, buildLayoutParams(blurBehind = enabled))
        } catch (_: Exception) {
            // View not attached (e.g. service tearing down mid-update) - nothing to do.
        }
    }

    private fun buildLayoutParams(blurBehind: Boolean): WindowManager.LayoutParams {
        val type = if (isAtLeastApiLevel(26)) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        if (blurBehind && isAtLeastApiLevel(31)) {
            flags = flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
        }

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            flags,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (blurBehind && isAtLeastApiLevel(31)) {
                this.blurBehindRadius = BLUR_BEHIND_RADIUS_PX
            }
        }
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.floating_launcher_notification_channel),
                    NotificationManager.IMPORTANCE_MIN,
                )
            )
        }

        val openSettings = PendingIntent.getActivity(
            this,
            0,
            Intent(this, SettingsActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.widgets_24px)
            .setContentTitle(getString(R.string.floating_launcher_notification_title))
            .setContentText(getString(R.string.floating_launcher_notification_text))
            .setContentIntent(openSettings)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "floating_launcher"
        private const val NOTIFICATION_ID = 4820
        private const val BLUR_BEHIND_RADIUS_PX = 60
    }
}

@Composable
private fun FloatingLauncherContent(
    settings: FloatingLauncherSettings,
    iconService: IconService,
    searchableRepository: SavableSearchableRepository,
    freezeManager: FreezeManager,
    appRepository: AppRepository,
    contextProfileManager: ContextProfileManager,
    shutterSettings: ShutterSettings,
    onPanelExpandedChanged: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isSystemDark = (configuration.uiMode and
        Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    val colorScheme = when {
        isAtLeastApiLevel(31) && isSystemDark -> dynamicDarkColorScheme(context)
        isAtLeastApiLevel(31) -> dynamicLightColorScheme(context)
        isSystemDark -> darkColorScheme()
        else -> lightColorScheme()
    }

    val zones by settings.zones.collectAsState(emptyMap())
    val thickness by settings.thickness.collectAsState(24)
    val tabColor by settings.color.collectAsState(0xFF6750A4.toInt())
    val tabAlpha by settings.alpha.collectAsState(0.6f)
    val hideIndicator by settings.hideIndicator.collectAsState(false)
    val columns by settings.columns.collectAsState(2)
    val maxPerColumn by settings.maxPerColumn.collectAsState(10)
    val hapticFeedbackEnabled by settings.hapticFeedback.collectAsState(true)
    val autoHideGaming by settings.autoHideGaming.collectAsState(false)
    // icon is the only field a ContextProfile carries that signals "this one's for gaming" -
    // profiles are otherwise fully user-defined with no built-in category.
    val activeProfile by contextProfileManager.activeProfile.collectAsState(null)
    val hiddenForGaming = autoHideGaming && activeProfile?.icon == ContextProfileIcon.Gaming

    var expandedZone by remember { mutableStateOf<FloatingLauncherZone?>(null) }
    // Deliberately not persisted (no DataStore field) - matches what "temporary" means for
    // OxygenOS's File Dock, this shelf is cleared whenever the service restarts.
    var fileDockItems by remember { mutableStateOf<List<FileDockItem>>(emptyList()) }
    val fileDockScope = rememberCoroutineScope()

    LaunchedEffect(hiddenForGaming) {
        if (hiddenForGaming) expandedZone = null
    }

    LaunchedEffect(expandedZone) {
        onPanelExpandedChanged(expandedZone != null)
    }

    fun addAppToZone(zone: FloatingLauncherZone, key: String) {
        val current = zones[zone]?.apps ?: emptyList()
        if (key !in current) {
            settings.setZoneApps(zone, current + key)
        }
    }

    fun reorderZone(zone: FloatingLauncherZone, keys: List<String>) {
        settings.setZoneApps(zone, keys)
    }

    fun removeAppFromZone(zone: FloatingLauncherZone, key: String) {
        val current = zones[zone]?.apps ?: emptyList()
        settings.setZoneApps(zone, current - key)
    }

    fun createFolder(zone: FloatingLauncherZone, name: String, appKeys: List<String>) {
        settings.createFolder(zone, name, appKeys)
    }

    fun renameFolder(zone: FloatingLauncherZone, folderId: String, name: String) {
        settings.renameFolder(zone, folderId, name)
    }

    fun deleteFolder(zone: FloatingLauncherZone, folderId: String) {
        settings.deleteFolder(zone, folderId)
    }

    fun handleExternalDrop(clipData: ClipData) {
        fileDockScope.launch {
            for (i in 0 until clipData.itemCount) {
                val item = clipData.getItemAt(i)
                val uri = item.uri
                if (uri != null) {
                    val mimeType = clipData.description.getMimeType(0)
                    val cachedUri = copyToDockCache(context, uri, mimeType) ?: continue
                    fileDockItems = fileDockItems + FileDockItem.MediaItem(
                        id = UUID.randomUUID().toString(),
                        uri = cachedUri,
                        mimeType = mimeType,
                        label = uri.lastPathSegment ?: uri.toString(),
                    )
                } else {
                    val text = item.text?.toString()
                    if (!text.isNullOrBlank()) {
                        fileDockItems = fileDockItems + FileDockItem.TextItem(UUID.randomUUID().toString(), text)
                    }
                }
            }
        }
    }

    fun removeFileDockItem(id: String) {
        fileDockItems = fileDockItems.filterNot { it.id == id }
    }

    MaterialTheme(colorScheme = colorScheme) {
        OverlayHost {
            if (!hiddenForGaming) {
                Box(modifier = Modifier.fillMaxSize()) {
                    for (zone in FloatingLauncherZone.entries) {
                        val config = zones[zone] ?: continue
                        if (!config.enabled) continue
                        ZoneTab(
                            zone = zone,
                            thickness = thickness,
                            color = if (hideIndicator) Color.Transparent else Color(tabColor).copy(alpha = tabAlpha),
                            hapticFeedbackEnabled = hapticFeedbackEnabled,
                            onClick = { expandedZone = zone },
                            onAppDropped = { key -> addAppToZone(zone, key) },
                            onExternalContentDropped = { clipData -> handleExternalDrop(clipData) },
                            modifier = Modifier.align(
                                BiasAlignment(
                                    horizontalBias = if (zone.isLeftEdge) -1f else 1f,
                                    verticalBias = zone.verticalFraction * 2f - 1f,
                                )
                            ),
                        )
                    }
                    val zone = expandedZone
                    val zoneConfig = zone?.let { zones[it] }
                    if (zone != null && zoneConfig != null) {
                        ExpandedPanel(
                            zone = zone,
                            config = zoneConfig,
                            columns = columns,
                            maxPerColumn = maxPerColumn,
                            searchableRepository = searchableRepository,
                            iconService = iconService,
                            freezeManager = freezeManager,
                            appRepository = appRepository,
                            shutterSettings = shutterSettings,
                            onDismiss = { expandedZone = null },
                            onAppLaunched = { expandedZone = null },
                            onAppDropped = { key -> addAppToZone(zone, key) },
                            onReorder = { keys -> reorderZone(zone, keys) },
                            onRemoveApp = { key -> removeAppFromZone(zone, key) },
                            onCreateFolder = { name, keys -> createFolder(zone, name, keys) },
                            onRenameFolder = { folderId, name -> renameFolder(zone, folderId, name) },
                            onDeleteFolder = { folderId -> deleteFolder(zone, folderId) },
                            onExternalContentDropped = { clipData -> handleExternalDrop(clipData) },
                            fileDockItems = fileDockItems,
                            onRemoveFileDockItem = { id -> removeFileDockItem(id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoneTab(
    zone: FloatingLauncherZone,
    thickness: Int,
    color: Color,
    hapticFeedbackEnabled: Boolean,
    onClick: () -> Unit,
    onAppDropped: (String) -> Unit,
    onExternalContentDropped: (ClipData) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = if (zone.isLeftEdge) {
        RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
    } else {
        RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
    }
    var isDropTarget by remember { mutableStateOf(false) }
    val hapticFeedback = LocalHapticFeedback.current
    Box(
        modifier = modifier
            .size(width = thickness.dp, height = 72.dp)
            .clip(shape)
            .background(if (isDropTarget) color.copy(alpha = (color.alpha + 0.35f).coerceAtMost(1f)) else color)
            // Swipe/drag the tab toward the panel side to open it, matching the OxygenOS Smart
            // Sidebar's tab-drag interaction - a plain tap no longer opens it.
            .pointerInput(zone, hapticFeedbackEnabled) {
                val openThreshold = 32.dp.toPx()
                var totalDrag = 0f
                var triggered = false
                detectHorizontalDragGestures(
                    onDragStart = {
                        totalDrag = 0f
                        triggered = false
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        if (!triggered) {
                            totalDrag += dragAmount
                            val openingDrag = if (zone.isLeftEdge) totalDrag else -totalDrag
                            if (openingDrag > openThreshold) {
                                triggered = true
                                if (hapticFeedbackEnabled) {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                                onClick()
                            }
                        }
                        change.consume()
                    },
                )
            }
            .dragAndDropTarget(
                // Accepts anything: our own app icons (text/plain, disambiguated below by clip
                // label) as well as arbitrary external content for the File Dock (text, images,
                // files - see APP_DRAG_CLIP_LABEL).
                shouldStartDragAndDrop = { true },
                target = object : DragAndDropTarget {
                    override fun onEntered(event: DragAndDropEvent) {
                        isDropTarget = true
                    }

                    override fun onExited(event: DragAndDropEvent) {
                        isDropTarget = false
                    }

                    override fun onEnded(event: DragAndDropEvent) {
                        isDropTarget = false
                    }

                    override fun onDrop(event: DragAndDropEvent): Boolean {
                        isDropTarget = false
                        val clipData = event.toAndroidDragEvent().clipData
                            ?.takeIf { it.itemCount > 0 }
                            ?: return false
                        if (clipData.description.label == APP_DRAG_CLIP_LABEL) {
                            val key = clipData.getItemAt(0)?.text?.toString() ?: return false
                            onAppDropped(key)
                        } else {
                            onExternalContentDropped(clipData)
                        }
                        return true
                    }
                },
            ),
    )
}

/** Icon cell size in the icons-only grid - matches OxygenOS Smart Sidebar's compact square tiles. */
private val ICON_CELL_SIZE = 72.dp

/**
 * ClipData items dragged from this launcher's own app icons (GridItem.kt) carry this as their
 * clip label so drops onto the floating launcher can tell "one of our own app icons" apart from
 * arbitrary external content meant for the File Dock.
 */
private const val APP_DRAG_CLIP_LABEL = "kvaesitso_app_icon"

/**
 * Something dragged onto the floating launcher from another app and held temporarily - OxygenOS
 * calls this the File Dock. Cleared whenever the service restarts (i.e. genuinely temporary, not
 * persisted to disk/settings), matching what "temporary storage" means there.
 */
private sealed interface FileDockItem {
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
private suspend fun copyToDockCache(context: Context, sourceUri: Uri, mimeType: String?): Uri? =
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
 * One entry in a zone's grid: either a loose app, or a folder grouping several apps. Both share
 * one flat, reorderable position list (see [FloatingLauncherZoneConfig.apps]) - this is just the
 * resolved, renderable form of that list for a given composition.
 */
private sealed interface ZoneGridItem {
    val rawKey: String

    data class AppEntry(val app: SavableSearchable) : ZoneGridItem {
        override val rawKey get() = app.key
    }

    data class FolderEntry(val folder: FloatingLauncherFolder) : ZoneGridItem {
        override val rawKey get() = FloatingLauncherFolder.sentinelKey(folder.id)
    }
}

@Composable
private fun ExpandedPanel(
    zone: FloatingLauncherZone,
    config: FloatingLauncherZoneConfig,
    columns: Int,
    maxPerColumn: Int,
    searchableRepository: SavableSearchableRepository,
    iconService: IconService,
    freezeManager: FreezeManager,
    appRepository: AppRepository,
    shutterSettings: ShutterSettings,
    onDismiss: () -> Unit,
    onAppLaunched: () -> Unit,
    onAppDropped: (String) -> Unit,
    onReorder: (List<String>) -> Unit,
    onRemoveApp: (String) -> Unit,
    onCreateFolder: (name: String, appKeys: List<String>) -> Unit,
    onRenameFolder: (folderId: String, name: String) -> Unit,
    onDeleteFolder: (folderId: String) -> Unit,
    onExternalContentDropped: (ClipData) -> Unit,
    fileDockItems: List<FileDockItem>,
    onRemoveFileDockItem: (String) -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val apps by remember(config.apps) {
        searchableRepository.getByKeys(config.apps)
    }.collectAsState(emptyList())
    // getByKeys doesn't preserve order - restore the user's configured order, and resolve
    // folder sentinel entries against the zone's folder list.
    val orderedItems = remember(apps, config.apps, config.folders) {
        config.apps.mapNotNull { key ->
            val folderId = FloatingLauncherFolder.idFromSentinel(key)
            if (folderId != null) {
                config.folders.firstOrNull { it.id == folderId }?.let { ZoneGridItem.FolderEntry(it) }
            } else {
                apps.firstOrNull { it.key == key }?.let { ZoneGridItem.AppEntry(it) }
            }
        }
    }
    var isDropTarget by remember { mutableStateOf(false) }
    var editMode by remember(zone) { mutableStateOf(false) }
    var openFolder by remember(zone) { mutableStateOf<FloatingLauncherFolder?>(null) }
    var editingFolder by remember(zone) { mutableStateOf<FloatingLauncherFolder?>(null) }
    var showCreateFolder by remember(zone) { mutableStateOf(false) }
    var showAllApps by remember(zone) { mutableStateOf(false) }
    var showFileDock by remember(zone) { mutableStateOf(false) }
    val dragState = rememberLazyDragAndDropGridState(
        onItemMove = { from, to ->
            val current = orderedItems.toMutableList()
            val fromIndex = current.indexOfFirst { it.rawKey == from.key }
            val toIndex = current.indexOfFirst { it.rawKey == to.key }
            if (fromIndex != -1 && toIndex != -1) {
                val moved = current.removeAt(fromIndex)
                current.add(toIndex, moved)
                onReorder(current.map { it.rawKey })
            }
        },
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onDismiss() })
            },
        contentAlignment = if (zone.isLeftEdge) {
            Alignment.CenterStart
        } else {
            Alignment.CenterEnd
        },
    ) {
        // OxygenOS Smart Sidebar look: a semi-transparent rounded card. The window behind it is
        // additionally blurred on API 31+ (toggled by the service hosting this composable) -
        // below that, this translucent color is the whole effect.
        Surface(
            modifier = Modifier
                .widthIn(max = ICON_CELL_SIZE * columns + 32.dp)
                .heightIn(max = ICON_CELL_SIZE * maxPerColumn + 112.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .dragAndDropTarget(
                    shouldStartDragAndDrop = { true },
                    target = object : DragAndDropTarget {
                        override fun onEntered(event: DragAndDropEvent) {
                            isDropTarget = true
                        }

                        override fun onExited(event: DragAndDropEvent) {
                            isDropTarget = false
                        }

                        override fun onEnded(event: DragAndDropEvent) {
                            isDropTarget = false
                        }

                        override fun onDrop(event: DragAndDropEvent): Boolean {
                            isDropTarget = false
                            val clipData = event.toAndroidDragEvent().clipData
                                ?.takeIf { it.itemCount > 0 }
                                ?: return false
                            if (clipData.description.label == APP_DRAG_CLIP_LABEL) {
                                val key = clipData.getItemAt(0)?.text?.toString() ?: return false
                                onAppDropped(key)
                            } else {
                                onExternalContentDropped(clipData)
                            }
                            return true
                        }
                    },
                ),
            color = if (isDropTarget) {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.92f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f)
            },
            shape = if (zone.isLeftEdge) {
                RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp)
            } else {
                RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp)
            },
            shadowElevation = 8.dp,
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            painterResource(R.drawable.close_24px),
                            contentDescription = stringResource(R.string.close),
                        )
                    }
                }
                if (orderedItems.isEmpty()) {
                    Text(
                        text = stringResource(R.string.floating_launcher_panel_empty),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                } else if (editMode) {
                    LazyVerticalDragAndDropGrid(
                        state = dragState,
                        columns = GridCells.Fixed(columns),
                    ) {
                        items(orderedItems, key = { it.rawKey }) { entry ->
                            DraggableItem(state = dragState, key = entry.rawKey) {
                                when (entry) {
                                    is ZoneGridItem.AppEntry -> FavoriteIcon(
                                        item = entry.app,
                                        iconService = iconService,
                                        appRepository = appRepository,
                                        shutterSettings = shutterSettings,
                                        editMode = true,
                                        onClick = {},
                                        onRemove = { onRemoveApp(entry.app.key) },
                                    )

                                    is ZoneGridItem.FolderEntry -> FolderIcon(
                                        folder = entry.folder,
                                        iconService = iconService,
                                        searchableRepository = searchableRepository,
                                        editMode = true,
                                        onClick = { editingFolder = entry.folder },
                                        onRemove = { onDeleteFolder(entry.folder.id) },
                                    )
                                }
                            }
                        }
                    }
                } else {
                    LazyVerticalGrid(columns = GridCells.Fixed(columns)) {
                        items(orderedItems, key = { it.rawKey }) { entry ->
                            when (entry) {
                                is ZoneGridItem.AppEntry -> FavoriteIcon(
                                    item = entry.app,
                                    iconService = iconService,
                                    appRepository = appRepository,
                                    shutterSettings = shutterSettings,
                                    editMode = false,
                                    onClick = {
                                        val item = entry.app
                                        if (item is Application && freezeManager.isFrozen(item.componentName.packageName)) {
                                            coroutineScope.launch {
                                                freezeManager.unfreeze(item.componentName.packageName)
                                                item.launch(context, null)
                                            }
                                        } else {
                                            item.launch(context, null)
                                        }
                                        onAppLaunched()
                                    },
                                    onRemove = {},
                                )

                                is ZoneGridItem.FolderEntry -> FolderIcon(
                                    folder = entry.folder,
                                    iconService = iconService,
                                    searchableRepository = searchableRepository,
                                    editMode = false,
                                    onClick = { openFolder = entry.folder },
                                    onRemove = {},
                                )
                            }
                        }
                    }
                }
                // Bottom toolbar - the OxygenOS Smart Sidebar puts edit access here rather than
                // at the top, so managing this zone's icons doesn't require hunting through the
                // main settings tree.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    IconButton(onClick = { editMode = !editMode }) {
                        Icon(
                            painterResource(if (editMode) R.drawable.check_24px else R.drawable.edit_24px),
                            contentDescription = stringResource(
                                if (editMode) R.string.floating_launcher_edit_done
                                else R.string.floating_launcher_edit_start
                            ),
                        )
                    }
                    if (editMode) {
                        IconButton(
                            onClick = { showCreateFolder = true },
                            enabled = orderedItems.count { it is ZoneGridItem.AppEntry } >= 2,
                        ) {
                            Icon(
                                painterResource(R.drawable.folder_24px),
                                contentDescription = stringResource(R.string.floating_launcher_new_folder),
                            )
                        }
                    } else {
                        IconButton(onClick = { showAllApps = true }) {
                            Icon(
                                painterResource(R.drawable.apps_24px),
                                contentDescription = stringResource(R.string.floating_launcher_all_apps),
                            )
                        }
                        IconButton(onClick = { showFileDock = true }) {
                            Icon(
                                painterResource(R.drawable.content_copy_24px),
                                contentDescription = stringResource(R.string.floating_launcher_file_dock),
                                tint = if (fileDockItems.isNotEmpty()) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    LocalContentColor.current
                                },
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            onDismiss()
                            val intent = Intent(context, SettingsActivity::class.java).apply {
                                putExtra(
                                    SettingsActivity.EXTRA_ROUTE,
                                    SettingsActivity.ROUTE_FLOATING_LAUNCHER,
                                )
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        },
                    ) {
                        Icon(
                            painterResource(R.drawable.settings_24px),
                            contentDescription = stringResource(R.string.floating_launcher_panel_settings),
                        )
                    }
                }
            }
        }

        val folderToOpen = openFolder
        if (folderToOpen != null) {
            FolderContentsOverlay(
                zone = zone,
                folder = folderToOpen,
                columns = columns,
                searchableRepository = searchableRepository,
                iconService = iconService,
                freezeManager = freezeManager,
                appRepository = appRepository,
                shutterSettings = shutterSettings,
                onAppLaunched = {
                    openFolder = null
                    onAppLaunched()
                },
                onDismiss = { openFolder = null },
            )
        }

        if (showAllApps) {
            AllAppsOverlay(
                columns = columns,
                appRepository = appRepository,
                iconService = iconService,
                freezeManager = freezeManager,
                shutterSettings = shutterSettings,
                onAppLaunched = {
                    showAllApps = false
                    onAppLaunched()
                },
                onDismiss = { showAllApps = false },
            )
        }

        if (showFileDock) {
            FileDockOverlay(
                items = fileDockItems,
                onRemove = onRemoveFileDockItem,
                onDismiss = { showFileDock = false },
            )
        }
    }

    if (showCreateFolder) {
        CreateFolderDialog(
            candidateApps = orderedItems.filterIsInstance<ZoneGridItem.AppEntry>().map { it.app },
            onConfirm = { name, keys ->
                onCreateFolder(name, keys)
                showCreateFolder = false
            },
            onDismiss = { showCreateFolder = false },
        )
    }

    val folderBeingEdited = editingFolder
    if (folderBeingEdited != null) {
        RenameOrDeleteFolderDialog(
            folder = folderBeingEdited,
            onRename = { name ->
                onRenameFolder(folderBeingEdited.id, name)
                editingFolder = null
            },
            onDelete = {
                onDeleteFolder(folderBeingEdited.id)
                editingFolder = null
            },
            onDismiss = { editingFolder = null },
        )
    }
}

/**
 * A single cell in the panel's grid. Icons-only outside edit mode, matching the default OxygenOS
 * Smart Sidebar look; in edit mode it also shows its label (so a bare icon isn't ambiguous while
 * rearranging) and a small remove badge, and stops launching on tap since a tap there means
 * "grab to drag" instead (handled by the drag grid this is placed inside).
 */
@Composable
private fun FavoriteIcon(
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
    val shutterRef by remember(item.key) {
        if (item is Application) shutterSettings.widgetFor(item.componentName.packageName)
        else flowOf(null)
    }.collectAsState(null)
    var showShutter by remember(item.key) { mutableStateOf(false) }
    val hapticFeedback = LocalHapticFeedback.current

    Box(modifier = Modifier.size(ICON_CELL_SIZE)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onClick),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            ShapedLauncherIcon(
                size = if (editMode) 36.dp else 44.dp,
                icon = { icon },
                grayscale = isFrozen,
            )
            if (editMode) {
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
            label = item.labelOverride ?: item.label,
            widgetRef = shutterRef,
            onDismiss = { showShutter = false },
        )
    }
}

/**
 * A folder's tile in the grid: up to 4 of its apps' icons arranged in a small 2x2 preview inside
 * a rounded square, the same overall size as a single [FavoriteIcon] cell.
 */
@Composable
private fun FolderIcon(
    folder: FloatingLauncherFolder,
    iconService: IconService,
    searchableRepository: SavableSearchableRepository,
    editMode: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    val previewKeys = remember(folder.appKeys) { folder.appKeys.take(4) }
    val previewApps by remember(previewKeys) {
        searchableRepository.getByKeys(previewKeys)
    }.collectAsState(emptyList())
    val orderedPreview = remember(previewApps, previewKeys) {
        previewKeys.mapNotNull { key -> previewApps.firstOrNull { it.key == key } }
    }

    Box(modifier = Modifier.size(ICON_CELL_SIZE)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onClick),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(if (editMode) 36.dp else 44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                if (orderedPreview.isEmpty()) {
                    Icon(
                        painterResource(R.drawable.folder_24px),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                } else {
                    FlowRow(
                        modifier = Modifier.padding(3.dp),
                        maxItemsInEachRow = 2,
                    ) {
                        for (app in orderedPreview) {
                            val icon by remember(app.key) {
                                iconService.getIcon(app, 24)
                            }.collectAsState(null)
                            ShapedLauncherIcon(size = 14.dp, icon = { icon })
                        }
                    }
                }
            }
            if (editMode) {
                Text(
                    text = folder.name,
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
}

/** A folder's contents, shown as a nested panel layered above the zone's main panel. */
@Composable
private fun FolderContentsOverlay(
    zone: FloatingLauncherZone,
    folder: FloatingLauncherFolder,
    columns: Int,
    searchableRepository: SavableSearchableRepository,
    iconService: IconService,
    freezeManager: FreezeManager,
    appRepository: AppRepository,
    shutterSettings: ShutterSettings,
    onAppLaunched: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val apps by remember(folder.appKeys) {
        searchableRepository.getByKeys(folder.appKeys)
    }.collectAsState(emptyList())
    val orderedApps = remember(apps, folder.appKeys) {
        folder.appKeys.mapNotNull { key -> apps.firstOrNull { it.key == key } }
    }

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
                .widthIn(max = ICON_CELL_SIZE * columns + 32.dp)
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
                    Text(folder.name, style = MaterialTheme.typography.titleSmall)
                    IconButton(onClick = onDismiss) {
                        Icon(
                            painterResource(R.drawable.close_24px),
                            contentDescription = stringResource(R.string.close),
                        )
                    }
                }
                LazyVerticalGrid(columns = GridCells.Fixed(columns)) {
                    items(orderedApps, key = { it.key }) { item ->
                        FavoriteIcon(
                            item = item,
                            iconService = iconService,
                            appRepository = appRepository,
                            shutterSettings = shutterSettings,
                            editMode = false,
                            onClick = {
                                if (item is Application && freezeManager.isFrozen(item.componentName.packageName)) {
                                    coroutineScope.launch {
                                        freezeManager.unfreeze(item.componentName.packageName)
                                        item.launch(context, null)
                                    }
                                } else {
                                    item.launch(context, null)
                                }
                                onAppLaunched()
                            },
                            onRemove = {},
                        )
                    }
                }
            }
        }
    }
}

/** Every installed app, browsable from within the panel via the bottom toolbar's apps button. */
@Composable
private fun AllAppsOverlay(
    columns: Int,
    appRepository: AppRepository,
    iconService: IconService,
    freezeManager: FreezeManager,
    shutterSettings: ShutterSettings,
    onAppLaunched: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val apps by remember { appRepository.findMany() }.collectAsState(emptyList())
    val sortedApps = remember(apps) { apps.sortedBy { (it.labelOverride ?: it.label).lowercase() } }

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
                .widthIn(max = ICON_CELL_SIZE * columns + 32.dp)
                .heightIn(max = ICON_CELL_SIZE * 6)
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
                        text = stringResource(R.string.floating_launcher_all_apps),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            painterResource(R.drawable.close_24px),
                            contentDescription = stringResource(R.string.close),
                        )
                    }
                }
                LazyVerticalGrid(columns = GridCells.Fixed(columns)) {
                    items(sortedApps, key = { it.key }) { item ->
                        FavoriteIcon(
                            item = item,
                            iconService = iconService,
                            appRepository = appRepository,
                            shutterSettings = shutterSettings,
                            editMode = false,
                            onClick = {
                                if (freezeManager.isFrozen(item.componentName.packageName)) {
                                    coroutineScope.launch {
                                        freezeManager.unfreeze(item.componentName.packageName)
                                        item.launch(context, null)
                                    }
                                } else {
                                    item.launch(context, null)
                                }
                                onAppLaunched()
                            },
                            onRemove = {},
                        )
                    }
                }
            }
        }
    }
}

/**
 * The File Dock's contents: text and media dragged onto the floating launcher from other apps,
 * held temporarily. Tapping a text item copies it to the clipboard; tapping a media item opens
 * a share sheet for it (there's no general way to "paste" into whatever app happens to be in the
 * foreground from here, unlike a real in-app clipboard).
 */
@Composable
private fun FileDockOverlay(
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
                .heightIn(max = ICON_CELL_SIZE * 6)
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

@Composable
private fun CreateFolderDialog(
    candidateApps: List<SavableSearchable>,
    onConfirm: (name: String, keys: List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf(setOf<String>()) }
    var name by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f))
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onDismiss() })
            },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 320.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 8.dp,
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = stringResource(R.string.floating_launcher_new_folder),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.floating_launcher_folder_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.floating_launcher_folder_select_apps),
                    style = MaterialTheme.typography.labelMedium,
                )
                LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                    items(candidateApps, key = { it.key }) { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selected = if (app.key in selected) selected - app.key else selected + app.key
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = app.key in selected, onCheckedChange = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = app.labelOverride ?: app.label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.floating_launcher_cancel))
                    }
                    TextButton(
                        onClick = { onConfirm(name, selected.toList()) },
                        enabled = selected.size >= 2 && name.isNotBlank(),
                    ) {
                        Text(stringResource(R.string.floating_launcher_create))
                    }
                }
            }
        }
    }
}

@Composable
private fun RenameOrDeleteFolderDialog(
    folder: FloatingLauncherFolder,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(folder.id) { mutableStateOf(folder.name) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f))
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onDismiss() })
            },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 320.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 8.dp,
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = stringResource(R.string.floating_launcher_rename_folder),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.floating_launcher_folder_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onDelete) {
                        Text(
                            text = stringResource(R.string.floating_launcher_delete_folder),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    TextButton(onClick = { onRename(name) }, enabled = name.isNotBlank()) {
                        Text(stringResource(R.string.floating_launcher_save))
                    }
                }
            }
        }
    }
}
