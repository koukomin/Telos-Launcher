package de.mm20.launcher2.ui.floating

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipDescription
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
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
                shouldStartDragAndDrop = { it.mimeTypes().contains(ClipDescription.MIMETYPE_TEXT_PLAIN) },
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
                        val key = event.toAndroidDragEvent().clipData
                            ?.takeIf { it.itemCount > 0 }
                            ?.getItemAt(0)?.text?.toString()
                            ?: return false
                        onAppDropped(key)
                        return true
                    }
                },
            ),
    )
}

/** Icon cell size in the icons-only grid - matches OxygenOS Smart Sidebar's compact square tiles. */
private val ICON_CELL_SIZE = 72.dp

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
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val apps by remember(config.apps) {
        searchableRepository.getByKeys(config.apps)
    }.collectAsState(emptyList())
    // getByKeys doesn't preserve order - restore the user's configured order.
    val orderedApps = remember(apps, config.apps) {
        config.apps.mapNotNull { key -> apps.firstOrNull { it.key == key } }
    }
    var isDropTarget by remember { mutableStateOf(false) }
    var editMode by remember(zone) { mutableStateOf(false) }
    val dragState = rememberLazyDragAndDropGridState(
        onItemMove = { from, to ->
            val current = orderedApps.toMutableList()
            val fromIndex = current.indexOfFirst { it.key == from.key }
            val toIndex = current.indexOfFirst { it.key == to.key }
            if (fromIndex != -1 && toIndex != -1) {
                val moved = current.removeAt(fromIndex)
                current.add(toIndex, moved)
                onReorder(current.map { it.key })
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
                    shouldStartDragAndDrop = { it.mimeTypes().contains(ClipDescription.MIMETYPE_TEXT_PLAIN) },
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
                            val key = event.toAndroidDragEvent().clipData
                                ?.takeIf { it.itemCount > 0 }
                                ?.getItemAt(0)?.text?.toString()
                                ?: return false
                            onAppDropped(key)
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
                if (orderedApps.isEmpty()) {
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
                        items(orderedApps, key = { it.key }) { item ->
                            DraggableItem(state = dragState, key = item.key) {
                                FavoriteIcon(
                                    item = item,
                                    iconService = iconService,
                                    appRepository = appRepository,
                                    shutterSettings = shutterSettings,
                                    editMode = true,
                                    onClick = {},
                                    onRemove = { onRemoveApp(item.key) },
                                )
                            }
                        }
                    }
                } else {
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
                .then(
                    if (editMode) {
                        Modifier
                    } else if (shuttersEnabled && item is Application) {
                        Modifier.combinedClickable(
                            onClick = onClick,
                            onLongClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                showShutter = true
                            },
                        )
                    } else {
                        Modifier.clickable(onClick = onClick)
                    }
                ),
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
