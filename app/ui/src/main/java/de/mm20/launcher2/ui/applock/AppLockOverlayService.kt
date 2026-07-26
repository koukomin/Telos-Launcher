package de.mm20.launcher2.ui.applock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.os.IBinder
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import de.mm20.launcher2.applock.AppLockManager
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.base.ProvideCompositionLocals
import de.mm20.launcher2.ui.settings.SettingsActivity
import de.mm20.launcher2.ui.theme.LauncherTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * Shows/hides App Lock's blocking gate as a SYSTEM_ALERT_WINDOW overlay, reacting to
 * [AppLockManager.pendingLock]. Deliberately NOT a plain `startActivity()` from the background
 * monitor - confirmed on-device that Android's background-activity-launch restrictions silently
 * block that (a StrictMode BackgroundActivityLaunchViolation, no crash, the call just never
 * shows anything). An overlay window isn't subject to that restriction, and the overlay's own
 * "Unlock" button - a direct user tap on a window this app already owns - is a recognized BAL
 * exemption, so that tap (see [AppLockActivity]) is the only thing that ever launches an Activity.
 *
 * Started/stopped directly from the "Enable App Lock" toggle (see AppLockSettingsScreen), and
 * resumed on process restart if the setting was already on (see LauncherApplication) - same
 * pattern as [de.mm20.launcher2.ui.islandoverlay.DynamicIslandService]. Runs as a foreground
 * service the whole time the feature is on, since Android kills lingering background services
 * almost immediately and this one needs to keep observing [AppLockManager.pendingLock].
 */
class AppLockOverlayService : Service(), SavedStateRegistryOwner {

    private val appLockManager: AppLockManager by inject()

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private var shownForPackage: String? = null

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        startForeground(NOTIFICATION_ID, buildNotification())

        scope.launch {
            appLockManager.pendingLock.collect { packageName ->
                if (packageName != null) showOverlay(packageName) else hideOverlay()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        hideOverlay()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        scope.cancel()
        super.onDestroy()
    }

    private fun showOverlay(packageName: String) {
        if (shownForPackage == packageName) return
        hideOverlay()
        if (!Settings.canDrawOverlays(this)) return
        shownForPackage = packageName

        val appLabel = try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(packageName, 0)
            ).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }

        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val view = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setViewTreeLifecycleOwner(this@AppLockOverlayService)
            setViewTreeSavedStateRegistryOwner(this@AppLockOverlayService)
        }
        composeView = view

        view.setContent {
            ProvideCompositionLocals {
                LauncherTheme {
                    AppLockOverlayContent(
                        appLabel = appLabel,
                        onUnlockTapped = {
                            // TYPE_APPLICATION_OVERLAY windows draw above regular app windows,
                            // including an Activity this same process just launched - without
                            // hiding it here, AppLockActivity's biometric prompt would start
                            // successfully but stay invisible behind this overlay. pendingLock
                            // doesn't change yet (the gate isn't resolved), so the reactive
                            // collector in onCreate won't do this on its own.
                            hideOverlay()
                            startActivity(
                                Intent(this@AppLockOverlayService, AppLockActivity::class.java).apply {
                                    putExtra(AppLockActivity.EXTRA_PACKAGE_NAME, packageName)
                                    putExtra(AppLockActivity.EXTRA_INSTANT, true)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            )
                        },
                        onCancel = {
                            appLockManager.dismiss()
                            goHome()
                        },
                    )
                }
            }
        }

        try {
            wm.addView(view, buildLayoutParams())
        } catch (e: Exception) {
            composeView = null
            windowManager = null
            shownForPackage = null
        }
    }

    private fun hideOverlay() {
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
        shownForPackage = null
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun buildLayoutParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.app_lock_notification_channel),
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
            .setSmallIcon(R.drawable.lock_24px)
            .setContentTitle(getString(R.string.app_lock_notification_title))
            .setContentText(getString(R.string.app_lock_notification_text))
            .setContentIntent(openSettings)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "app_lock"
        private const val NOTIFICATION_ID = 4823
    }
}

@Composable
private fun AppLockOverlayContent(
    appLabel: String,
    onUnlockTapped: () -> Unit,
    onCancel: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                painterResource(R.drawable.lock_48px),
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.app_lock_locked_message, appLabel),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 16.dp),
            )
            Button(onClick = onUnlockTapped) {
                Text(stringResource(R.string.settings_locked_unlock))
            }
            TextButton(onClick = onCancel) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    }
}
