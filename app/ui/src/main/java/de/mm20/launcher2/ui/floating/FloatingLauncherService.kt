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
import android.hardware.camera2.CameraManager
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import kotlinx.coroutines.flow.combine
import android.view.KeyEvent
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import de.mm20.launcher2.preferences.FloatingLauncherEdge
import de.mm20.launcher2.widgets.WidgetRepository

/**
 * Foreground service that hosts the Smart Sidebar: a thin handle on one screen edge, over every
 * app, that opens a card with tools, apps and widgets. Opt-in, off by default, started and stopped
 * by the settings screen when the user toggles it.
 *
 * Two overlay windows are used, and never more: the **handle** window is as small as the handle
 * (a window that is larger than what should be touchable would block the touch of the app below,
 * which locked the whole screen in an early version of this class), and the **panel** window is
 * full screen but only exists while the card or the editor is open, so tapping outside the card
 * can close it. [FloatingLauncherDisableReceiver] can switch the whole thing off with
 * `adb shell am broadcast -a de.mm20.launcher2.action.DISABLE_FLOATING_LAUNCHER -p de.mm20.launcher2`.
 *
 * The compose views are created with a themed context: the icons of the app use theme attributes
 * and a Service has no theme, which made drawing any of them fail.
 *
 * There is deliberately no search field in the card. The editor has one, and while it is open the
 * panel window is focusable (which takes the keyboard focus from the app in front).
 */
class FloatingLauncherService : Service(), SavedStateRegistryOwner, ViewModelStoreOwner {

    private val floatingLauncherSettings: FloatingLauncherSettings by inject()
    private val iconService: IconService by inject()
    private val searchableRepository: SavableSearchableRepository by inject()
    private val freezeManager: FreezeManager by inject()
    private val appRepository: AppRepository by inject()
    private val contextProfileManager: ContextProfileManager by inject()
    private val shutterSettings: ShutterSettings by inject()
    private val globalActions: GlobalActionsService by inject()
    private val widgetRepository: WidgetRepository by inject()

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    // widgets use view models
    override val viewModelStore: ViewModelStore = ViewModelStore()

    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private var windowManager: WindowManager? = null
    private var handleView: ComposeView? = null
    private var panelView: ComposeView? = null
    private var torchOn = false

    // Not persisted, like the temporary storage of the Smart Sidebar: cleared when the service restarts.
    private val fileDockItemsState = mutableStateOf<List<FileDockItem>>(emptyList())

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        startForeground(NOTIFICATION_ID, buildNotification())
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // the handle follows the settings: side, height and position
        scope.launch {
            combine(
                floatingLauncherSettings.side,
                floatingLauncherSettings.handleY,
                floatingLauncherSettings.handleHeight,
                floatingLauncherSettings.thickness,
            ) { side, y, height, thickness -> HandleLayout(side, y, height, thickness) }
                .distinctUntilChanged()
                .collect { layout -> showHandle(layout) }
        }
        scope.launch {
            floatingLauncherSettings.enabled.collect { enabled ->
                if (!enabled) stopSelf()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_OPEN_EDITOR) openPanel(startInEditMode = true)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // the height of the screen changed, most likely a rotation
        scope.launch { showHandle(lastLayout ?: return@launch) }
    }

    override fun onDestroy() {
        val wm = windowManager
        if (wm != null) {
            handleView?.let { runCatching { wm.removeView(it) } }
            panelView?.let { runCatching { wm.removeView(it) } }
        }
        handleView = null
        panelView = null
        windowManager = null
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        viewModelStore.clear()
        scope.cancel()
        super.onDestroy()
    }

    /**
     * The icons of this app use theme attributes (`?attr/colorControlNormal`). A Service has no
     * theme of its own, so without this wrapper drawing any of them throws "Failed to resolve attribute".
     */
    private fun themedContext(): Context =
        ContextThemeWrapper(this, androidx.appcompat.R.style.Theme_AppCompat_DayNight_NoActionBar)

    private data class HandleLayout(val side: FloatingLauncherEdge, val y: Float, val height: Int, val thickness: Int)

    private var lastLayout: HandleLayout? = null

    private fun showHandle(layout: HandleLayout) {
        val wm = windowManager ?: return
        lastLayout = layout
        handleView?.let { runCatching { wm.removeView(it) } }
        val view = ComposeView(themedContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setViewTreeLifecycleOwner(this@FloatingLauncherService)
            setViewTreeSavedStateRegistryOwner(this@FloatingLauncherService)
            setViewTreeViewModelStoreOwner(this@FloatingLauncherService)
            setContent {
                SidebarHandleContent(
                    side = layout.side,
                    heightDp = layout.height,
                    settings = floatingLauncherSettings,
                    contextProfileManager = contextProfileManager,
                    onOpen = { openPanel(startInEditMode = false) },
                    onAppDropped = { key -> floatingLauncherSettings.addItem(key) },
                    onExternalContentDropped = { clipData -> handleExternalDrop(clipData) },
                )
            }
        }
        try {
            wm.addView(view, buildHandleLayoutParams(layout))
            handleView = view
        } catch (e: Exception) {
            // Permission revoked, or no window token available - nothing sensible to do but stop.
            stopSelf()
        }
    }

    private fun openPanel(startInEditMode: Boolean) {
        val wm = windowManager ?: return
        closePanel()
        val view = ComposeView(themedContext())
        view.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setViewTreeLifecycleOwner(this@FloatingLauncherService)
            setViewTreeSavedStateRegistryOwner(this@FloatingLauncherService)
            setViewTreeViewModelStoreOwner(this@FloatingLauncherService)
            // The back key closes the editor or the card (it reaches the view while it has focus)
            setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    if (event.action == KeyEvent.ACTION_UP) closePanel()
                    true
                } else {
                    false
                }
            }
            setContent {
                val fileDockItems by fileDockItemsState
                SidebarPanelContent(
                    startInEditMode = startInEditMode,
                    settings = floatingLauncherSettings,
                    iconService = iconService,
                    searchableRepository = searchableRepository,
                    freezeManager = freezeManager,
                    appRepository = appRepository,
                    shutterSettings = shutterSettings,
                    widgetRepository = widgetRepository,
                    fileDockItems = fileDockItems,
                    onDismiss = { closePanel() },
                    onEditModeChanged = { editing -> setPanelFocusable(editing) },
                    onAppDropped = { key -> floatingLauncherSettings.addItem(key) },
                    onExternalContentDropped = { clipData -> handleExternalDrop(clipData) },
                    onRemoveFileDockItem = { id -> removeFileDockItem(id) },
                    onTool = { tool -> runTool(tool) },
                )
            }
        }
        panelView = view
        wm.addView(view, buildPanelLayoutParams(focusable = startInEditMode))
    }

    private fun closePanel() {
        val view = panelView ?: return
        val wm = windowManager ?: return
        try {
            wm.removeView(view)
        } catch (_: Exception) {
        }
        panelView = null
    }

    /** The editor has a text field, which needs a window that can take the keyboard focus */
    private fun setPanelFocusable(focusable: Boolean) {
        val wm = windowManager ?: return
        val view = panelView ?: return
        try {
            wm.updateViewLayout(view, buildPanelLayoutParams(focusable))
            if (focusable) {
                view.isFocusableInTouchMode = true
                view.requestFocus()
            }
        } catch (_: Exception) {
        }
    }

    private fun runTool(tool: SidebarTool) {
        closePanel()
        when (tool) {
            SidebarTool.Screenshot -> scope.launch {
                // let the panel disappear before the picture is taken
                delay(450)
                if (!globalActions.takeScreenshot()) {
                    Toast.makeText(this@FloatingLauncherService, R.string.floating_launcher_tool_needs_accessibility, Toast.LENGTH_LONG).show()
                }
            }

            SidebarTool.RecentFiles -> Unit // handled by the panel

            SidebarTool.Flashlight -> toggleTorch()
            SidebarTool.QuickSettings -> globalActions.openQuickSettings()
            SidebarTool.Notifications -> globalActions.openNotificationDrawer()
            SidebarTool.LockScreen -> if (!globalActions.lockScreenOrFalse()) {
                Toast.makeText(this, R.string.floating_launcher_tool_needs_accessibility, Toast.LENGTH_LONG).show()
            }
            SidebarTool.PowerMenu -> globalActions.openPowerDialog()
        }
    }

    private fun toggleTorch() {
        try {
            val cameras = getSystemService(CAMERA_SERVICE) as CameraManager
            val id = cameras.cameraIdList.firstOrNull {
                cameras.getCameraCharacteristics(it)
                    .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: throw IllegalStateException("no flash")
            torchOn = !torchOn
            cameras.setTorchMode(id, torchOn)
        } catch (e: Exception) {
            torchOn = false
            Toast.makeText(this, R.string.floating_launcher_flashlight_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleExternalDrop(clipData: ClipData) {
        scope.launch {
            for (i in 0 until clipData.itemCount) {
                val item = clipData.getItemAt(i)
                val uri = item.uri
                if (uri != null) {
                    val mimeType = clipData.description.getMimeType(0)
                    val cachedUri = copyToDockCache(this@FloatingLauncherService, uri, mimeType) ?: continue
                    fileDockItemsState.value = fileDockItemsState.value + FileDockItem.MediaItem(
                        id = UUID.randomUUID().toString(),
                        uri = cachedUri,
                        mimeType = mimeType,
                        label = uri.lastPathSegment ?: uri.toString(),
                    )
                } else {
                    val text = item.text?.toString()
                    if (!text.isNullOrBlank()) {
                        fileDockItemsState.value = fileDockItemsState.value +
                            FileDockItem.TextItem(UUID.randomUUID().toString(), text)
                    }
                }
            }
        }
    }

    private fun removeFileDockItem(id: String) {
        fileDockItemsState.value = fileDockItemsState.value.filterNot { it.id == id }
    }

    /**
     * WRAP_CONTENT-sized and placed on the chosen edge at the chosen height. A window that is
     * larger than the handle would take the touch of the app below it.
     */
    private fun buildHandleLayoutParams(layout: HandleLayout): WindowManager.LayoutParams {
        val density = resources.displayMetrics.density
        val screenHeightPx = resources.displayMetrics.heightPixels
        val handleHeightPx = ((layout.height + HANDLE_PADDING_DP) * density).roundToInt()
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or if (layout.side == FloatingLauncherEdge.Left) Gravity.START else Gravity.END
            x = 0
            y = computeTabY(screenHeightPx, handleHeightPx, layout.y)
        }
    }

    /**
     * Full screen, so that a tap outside the card closes it. It only exists while the card or the
     * editor is open. Not focusable, except while the editor is open (it has a search field).
     */
    private fun buildPanelLayoutParams(focusable: Boolean): WindowManager.LayoutParams {
        var flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        if (!focusable) flags = flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayWindowType(),
            flags,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
    }

    private fun overlayWindowType(): Int = if (isAtLeastApiLevel(26)) {
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    } else {
        @Suppress("DEPRECATION")
        WindowManager.LayoutParams.TYPE_PHONE
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

        /** Extra room around the handle that can be touched, in dp */
        private const val HANDLE_PADDING_DP = 24

        /** Opens the editor of the sidebar, started from the settings */
        const val ACTION_OPEN_EDITOR = "de.mm20.launcher2.action.OPEN_SIDEBAR_EDITOR"
    }
}

/**
 * Pulled out of [FloatingLauncherService.buildTabLayoutParams] as a pure function purely so it's
 * unit-testable without an Android runtime (`Resources`/`WindowManager` aren't available to a
 * plain JVM unit test). Replicates what
 * `BiasAlignment(verticalBias = verticalFraction * 2f - 1f)` used to compute for a child inside a
 * fullscreen Box back when all six zones shared one window - see the class doc for why that
 * window no longer exists, but the on-screen position each tab ends up at is unchanged.
 */
internal fun computeTabY(screenHeightPx: Int, tabHeightPx: Int, verticalFraction: Float): Int =
    ((screenHeightPx - tabHeightPx) * verticalFraction).roundToInt()


@Composable
private fun isHiddenForGaming(
    settings: FloatingLauncherSettings,
    contextProfileManager: ContextProfileManager,
): Boolean {
    val autoHideGaming by settings.autoHideGaming.collectAsState(false)
    // icon is the only field a ContextProfile carries that signals "this one's for gaming" -
    // profiles are otherwise fully user-defined with no built-in category.
    val activeProfile by contextProfileManager.activeProfile.collectAsState(null)
    return autoHideGaming && activeProfile?.icon == ContextProfileIcon.Gaming
}

/** The content of the handle window: renders nothing while the handle is hidden (gaming profile). */
@Composable
private fun SidebarHandleContent(
    side: FloatingLauncherEdge,
    heightDp: Int,
    settings: FloatingLauncherSettings,
    contextProfileManager: ContextProfileManager,
    onOpen: () -> Unit,
    onAppDropped: (String) -> Unit,
    onExternalContentDropped: (ClipData) -> Unit,
) {
    val thickness by settings.thickness.collectAsState(24)
    val color by settings.color.collectAsState(0xFF9E9E9E.toInt())
    val alpha by settings.alpha.collectAsState(0.8f)
    val hideIndicator by settings.hideIndicator.collectAsState(false)
    val haptic by settings.hapticFeedback.collectAsState(true)
    if (isHiddenForGaming(settings, contextProfileManager)) return

    SidebarHandle(
        side = side,
        thickness = thickness,
        heightDp = heightDp,
        color = if (hideIndicator) Color.Transparent else Color(color).copy(alpha = alpha),
        hapticFeedbackEnabled = haptic,
        onClick = onOpen,
        onAppDropped = onAppDropped,
        onExternalContentDropped = onExternalContentDropped,
    )
}

@Composable
private fun SidebarHandle(
    side: FloatingLauncherEdge,
    thickness: Int,
    heightDp: Int,
    color: Color,
    hapticFeedbackEnabled: Boolean,
    onClick: () -> Unit,
    onAppDropped: (String) -> Unit,
    onExternalContentDropped: (ClipData) -> Unit,
) {
    val isLeft = side == FloatingLauncherEdge.Left
    var isDropTarget by remember { mutableStateOf(false) }
    val hapticFeedback = LocalHapticFeedback.current
    // The visible handle is a thin pill on the screen edge, like the Smart Sidebar's. The window
    // around it is wider and taller than the pill, so that it is easy to hit and so that a drag
    // can start a little way in from the edge (swipes that start at the very edge belong to the
    // system's Back gesture).
    Box(
        modifier = Modifier
            .size(width = thickness.dp.coerceAtLeast(28.dp), height = (heightDp + 24).dp)
            // A tap opens the sidebar ...
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    if (hapticFeedbackEnabled) hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                },
            )
            // ... and so does dragging the handle toward the middle of the screen.
            .pointerInput(isLeft, hapticFeedbackEnabled) {
                val openThreshold = 16.dp.toPx()
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
                            val openingDrag = if (isLeft) totalDrag else -totalDrag
                            if (openingDrag > openThreshold) {
                                triggered = true
                                if (hapticFeedbackEnabled) hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                onClick()
                            }
                        }
                        change.consume()
                    },
                )
            }
            .dragAndDropTarget(
                // Accepts anything: our own app icons (disambiguated by clip label) as well as
                // content from other apps for the File Dock.
                shouldStartDragAndDrop = { true },
                target = object : DragAndDropTarget {
                    override fun onEntered(event: DragAndDropEvent) { isDropTarget = true }
                    override fun onExited(event: DragAndDropEvent) { isDropTarget = false }
                    override fun onEnded(event: DragAndDropEvent) { isDropTarget = false }
                    override fun onDrop(event: DragAndDropEvent): Boolean {
                        isDropTarget = false
                        val clipData = event.toAndroidDragEvent().clipData?.takeIf { it.itemCount > 0 } ?: return false
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
        contentAlignment = if (isLeft) Alignment.CenterStart else Alignment.CenterEnd,
    ) {
        Box(
            Modifier
                .size(width = if (isDropTarget) 12.dp else 6.dp, height = (if (isDropTarget) heightDp else heightDp * 3 / 4).dp)
                .clip(RoundedCornerShape(50))
                .background(if (isDropTarget) color.copy(alpha = (color.alpha + 0.35f).coerceAtMost(1f)) else color)
        )
    }
}
