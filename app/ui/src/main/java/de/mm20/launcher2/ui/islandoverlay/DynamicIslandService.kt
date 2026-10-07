package de.mm20.launcher2.ui.islandoverlay

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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.music.MusicService
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.settings.SettingsActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import org.koin.android.ext.android.inject

/**
 * Foreground service hosting the Dynamic Island pill: a small always-on-top overlay near the top
 * of the screen showing whichever of an active call, a running timer, media playback, or
 * charging is currently most relevant (see [DynamicIslandContentProvider]), expandable on tap.
 * Reuses the exact SYSTEM_ALERT_WINDOW overlay mechanism [de.mm20.launcher2.ui.floating.FloatingLauncherService]
 * already established - a second, independent foreground service with its own small WRAP_CONTENT
 * window rather than a full-screen one, since the two features are otherwise unrelated and don't
 * need to share a window. Opt-in, off by default.
 */
class DynamicIslandService : Service(), SavedStateRegistryOwner {

    private val contentProvider: DynamicIslandContentProvider by inject()
    private val musicService: MusicService by inject()
    private val timerManager: TimerManager by inject()

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
            stopSelf()
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

        // The icons use theme attributes, which a Service does not have (see FloatingLauncherService)
        val themed = android.view.ContextThemeWrapper(this, androidx.appcompat.R.style.Theme_AppCompat_DayNight_NoActionBar)
        val view = ComposeView(themed).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setViewTreeLifecycleOwner(this@DynamicIslandService)
            setViewTreeSavedStateRegistryOwner(this@DynamicIslandService)
        }
        composeView = view

        view.setContent {
            DynamicIslandPill(
                contentProvider = contentProvider,
                musicService = musicService,
                onTimerCancel = { timerManager.cancel() },
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

        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            flags,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = TOP_MARGIN_PX
        }
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.dynamic_island_notification_channel),
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
            .setContentTitle(getString(R.string.dynamic_island_notification_title))
            .setContentText(getString(R.string.dynamic_island_notification_text))
            .setContentIntent(openSettings)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "dynamic_island"
        private const val NOTIFICATION_ID = 4821
        private const val TOP_MARGIN_PX = 48
    }
}

@Composable
private fun DynamicIslandPill(
    contentProvider: DynamicIslandContentProvider,
    musicService: MusicService,
    onTimerCancel: () -> Unit,
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

    val content by contentProvider.content.collectAsState(null)
    var expanded by remember { mutableStateOf(false) }

    // Capture the last non-null content to avoid crashes during exit animation
    var lastContent by remember { mutableStateOf<IslandContent?>(null) }
    LaunchedEffect(content) {
        if (content != null) {
            lastContent = content
        } else {
            expanded = false
        }
    }

    if (content == null && lastContent == null) return

    MaterialTheme(colorScheme = colorScheme) {
        AnimatedVisibility(
            visible = content != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            val currentToRender = content ?: lastContent
            if (currentToRender != null) {
                Surface(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .animateContentSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { expanded = !expanded },
                    shape = RoundedCornerShape(50),
                    color = Color.Black,
                    contentColor = Color.White,
                ) {
                    if (!expanded) {
                        CollapsedPill(currentToRender)
                    } else {
                        ExpandedPill(currentToRender, musicService, onTimerCancel)
                    }
                }
            }
        }
    }
}

@Composable
private fun CollapsedPill(content: IslandContent) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            painter = painterResource(islandIcon(content)),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = islandSummary(content),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ExpandedPill(
    content: IslandContent,
    musicService: MusicService,
    onTimerCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(16.dp)
            .widthIn(min = 220.dp, max = 320.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painter = painterResource(islandIcon(content)),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = islandSummary(content),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                islandDetail(content)?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        when (content) {
            is IslandContent.Media -> {
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { musicService.previous() }) {
                        Icon(painterResource(R.drawable.skip_previous_24px), contentDescription = null, tint = Color.White)
                    }
                    IconButton(onClick = { musicService.togglePause() }) {
                        Icon(
                            painterResource(if (content.playing) R.drawable.pause_24px else R.drawable.play_arrow_24px),
                            contentDescription = null,
                            tint = Color.White,
                        )
                    }
                    IconButton(onClick = { musicService.next() }) {
                        Icon(painterResource(R.drawable.skip_next_24px), contentDescription = null, tint = Color.White)
                    }
                }
            }

            is IslandContent.Timer -> {
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    IconButton(onClick = onTimerCancel) {
                        Icon(painterResource(R.drawable.close_24px), contentDescription = null, tint = Color.White)
                    }
                }
            }

            else -> {}
        }
    }
}

private fun islandIcon(content: IslandContent): Int = when (content) {
    is IslandContent.Call -> R.drawable.call_24px
    is IslandContent.Timer -> R.drawable.timer_24px
    is IslandContent.Media -> R.drawable.music_note_24px
    is IslandContent.Charging -> R.drawable.battery_charging_full_24px
}

@Composable
private fun islandSummary(content: IslandContent): String = when (content) {
    is IslandContent.Call -> stringResource(R.string.dynamic_island_call)
    is IslandContent.Timer -> formatRemaining(content.remainingMs)
    is IslandContent.Media -> content.title ?: stringResource(R.string.dynamic_island_media_playing)
    is IslandContent.Charging -> stringResource(R.string.dynamic_island_charging_percent, content.level)
}

@Composable
private fun islandDetail(content: IslandContent): String? = when (content) {
    is IslandContent.Media -> content.artist
    else -> null
}

private fun formatRemaining(remainingMs: Long): String {
    val totalSeconds = (remainingMs / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
