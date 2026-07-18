package de.mm20.launcher2.ui.floating

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
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
import de.mm20.launcher2.icons.IconService
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.preferences.FloatingLauncherZone
import de.mm20.launcher2.preferences.FloatingLauncherZoneConfig
import de.mm20.launcher2.preferences.ui.FloatingLauncherSettings
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.searchable.SavableSearchableRepository
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.ShapedLauncherIcon
import de.mm20.launcher2.ui.settings.SettingsActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
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
 */
class FloatingLauncherService : Service(), SavedStateRegistryOwner {

    private val floatingLauncherSettings: FloatingLauncherSettings by inject()
    private val iconService: IconService by inject()
    private val searchableRepository: SavableSearchableRepository by inject()

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
            )
        }

        wm.addView(view, buildLayoutParams())
    }

    private fun buildLayoutParams(): WindowManager.LayoutParams {
        val type = if (isAtLeastApiLevel(26)) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
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
    }
}

@Composable
private fun FloatingLauncherContent(
    settings: FloatingLauncherSettings,
    iconService: IconService,
    searchableRepository: SavableSearchableRepository,
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
    val columns by settings.columns.collectAsState(1)

    var expandedZone by remember { mutableStateOf<FloatingLauncherZone?>(null) }

    MaterialTheme(colorScheme = colorScheme) {
        Box(modifier = Modifier.fillMaxSize()) {
            for (zone in FloatingLauncherZone.entries) {
                val config = zones[zone] ?: continue
                if (!config.enabled) continue
                ZoneTab(
                    zone = zone,
                    thickness = thickness,
                    color = if (hideIndicator) Color.Transparent else Color(tabColor).copy(alpha = tabAlpha),
                    onClick = { expandedZone = zone },
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
                    searchableRepository = searchableRepository,
                    iconService = iconService,
                    onDismiss = { expandedZone = null },
                    onAppLaunched = { expandedZone = null },
                )
            }
        }
    }
}

@Composable
private fun ZoneTab(
    zone: FloatingLauncherZone,
    thickness: Int,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = if (zone.isLeftEdge) {
        RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
    } else {
        RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
    }
    Box(
        modifier = modifier
            .size(width = thickness.dp, height = 72.dp)
            .clip(shape)
            .background(color)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    )
}

@Composable
private fun ExpandedPanel(
    zone: FloatingLauncherZone,
    config: FloatingLauncherZoneConfig,
    columns: Int,
    searchableRepository: SavableSearchableRepository,
    iconService: IconService,
    onDismiss: () -> Unit,
    onAppLaunched: () -> Unit,
) {
    val context = LocalContext.current
    val apps by remember(config.apps) {
        searchableRepository.getByKeys(config.apps)
    }.collectAsState(emptyList())
    // getByKeys doesn't preserve order - restore the user's configured order.
    val orderedApps = remember(apps, config.apps) {
        config.apps.mapNotNull { key -> apps.firstOrNull { it.key == key } }
    }

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
        Surface(
            modifier = Modifier
                .widthIn(max = if (columns == 2) 360.dp else 280.dp)
                .heightIn(max = 480.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
            shape = if (zone.isLeftEdge) {
                RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)
            } else {
                RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp)
            },
            tonalElevation = 8.dp,
            shadowElevation = 8.dp,
        ) {
            Column(modifier = Modifier.padding(vertical = 12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.floating_launcher_panel_title),
                        style = MaterialTheme.typography.titleSmall,
                    )
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
                } else {
                    LazyVerticalGrid(columns = GridCells.Fixed(columns)) {
                        items(orderedApps, key = { it.key }) { item ->
                            FavoriteRow(
                                item = item,
                                iconService = iconService,
                                onClick = {
                                    item.launch(context, null)
                                    onAppLaunched()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoriteRow(
    item: SavableSearchable,
    iconService: IconService,
    onClick: () -> Unit,
) {
    val icon by remember(item.key) { iconService.getIcon(item, 48) }.collectAsState(null)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ShapedLauncherIcon(size = 32.dp, icon = { icon })
        Text(
            text = item.labelOverride ?: item.label,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
