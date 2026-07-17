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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import de.mm20.launcher2.preferences.FloatingLauncherEdge
import de.mm20.launcher2.preferences.ui.FloatingLauncherSettings
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.services.favorites.FavoritesService
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
 * Foreground service hosting the floating quick launcher: a small tab pinned to a screen edge
 * (via a SYSTEM_ALERT_WINDOW overlay) that expands into a scrollable list of favorite apps.
 * Opt-in, off by default - started/stopped by the settings screen when the user toggles it.
 *
 * Deliberately does not include a search field: accepting text input in an overlay window means
 * making the window focusable, which briefly steals input focus from whatever app is in the
 * foreground. That interaction is hard to get right without a physical device to verify it on,
 * so this first version only offers the bounded, already-curated favorites list.
 */
class FloatingLauncherService : Service(), SavedStateRegistryOwner {

    private val floatingLauncherSettings: FloatingLauncherSettings by inject()
    private val favoritesService: FavoritesService by inject()
    private val iconService: IconService by inject()

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private var expanded = false

    private var currentEdge = FloatingLauncherEdge.Right
    private var currentPosition = 0.5f

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
        scope.launch {
            floatingLauncherSettings.edge.collect {
                currentEdge = it
                relayoutIfCollapsed()
            }
        }
        scope.launch {
            floatingLauncherSettings.position.collect {
                currentPosition = it
                relayoutIfCollapsed()
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
                favoritesService = favoritesService,
                iconService = iconService,
                onExpandedChange = { setExpanded(it) },
            )
        }

        wm.addView(view, buildLayoutParams(expanded = false))
    }

    private fun setExpanded(value: Boolean) {
        if (expanded == value) return
        expanded = value
        relayout()
    }

    private fun relayoutIfCollapsed() {
        if (!expanded) relayout()
    }

    private fun relayout() {
        val view = composeView ?: return
        val wm = windowManager ?: return
        try {
            wm.updateViewLayout(view, buildLayoutParams(expanded = expanded))
        } catch (_: Exception) {
        }
    }

    private fun buildLayoutParams(expanded: Boolean): WindowManager.LayoutParams {
        val type = if (isAtLeastApiLevel(26)) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        if (expanded) {
            return WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
            }
        }

        val metrics = resources.displayMetrics
        val tabHeightPx = (72 * metrics.density).toInt()
        val y = ((metrics.heightPixels - tabHeightPx) * currentPosition).toInt()

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or
                if (currentEdge == FloatingLauncherEdge.Left) Gravity.START else Gravity.END
            this.y = y
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
    favoritesService: FavoritesService,
    iconService: IconService,
    onExpandedChange: (Boolean) -> Unit,
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

    var expanded by remember { mutableStateOf(false) }
    val edge by settings.edge.collectAsState(FloatingLauncherEdge.Right)
    val thickness by settings.thickness.collectAsState(24)
    val tabColor by settings.color.collectAsState(0xFF6750A4.toInt())
    val tabAlpha by settings.alpha.collectAsState(0.6f)

    MaterialTheme(colorScheme = colorScheme) {
        if (!expanded) {
            CollapsedTab(
                thickness = thickness,
                color = Color(tabColor).copy(alpha = tabAlpha),
                edge = edge,
                onClick = {
                    expanded = true
                    onExpandedChange(true)
                },
            )
        } else {
            ExpandedPanel(
                edge = edge,
                favoritesService = favoritesService,
                iconService = iconService,
                onDismiss = {
                    expanded = false
                    onExpandedChange(false)
                },
                onAppLaunched = {
                    expanded = false
                    onExpandedChange(false)
                },
            )
        }
    }
}

@Composable
private fun CollapsedTab(
    thickness: Int,
    color: Color,
    edge: FloatingLauncherEdge,
    onClick: () -> Unit,
) {
    val shape = if (edge == FloatingLauncherEdge.Left) {
        RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
    } else {
        RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
    }
    Box(
        modifier = Modifier
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
    edge: FloatingLauncherEdge,
    favoritesService: FavoritesService,
    iconService: IconService,
    onDismiss: () -> Unit,
    onAppLaunched: () -> Unit,
) {
    val context = LocalContext.current
    val favorites by remember {
        favoritesService.getFavorites(includeTypes = listOf("app"), limit = 12)
    }.collectAsState(emptyList())

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onDismiss() })
            },
        contentAlignment = if (edge == FloatingLauncherEdge.Left) {
            Alignment.CenterStart
        } else {
            Alignment.CenterEnd
        },
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .heightIn(max = 480.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
            shape = if (edge == FloatingLauncherEdge.Left) {
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
                if (favorites.isEmpty()) {
                    Text(
                        text = stringResource(R.string.floating_launcher_panel_empty),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                } else {
                    LazyColumn {
                        items(favorites) { item ->
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
