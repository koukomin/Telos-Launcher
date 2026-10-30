package de.mm20.launcher2.globalactions

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.preferences.ui.GestureSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import java.lang.ref.WeakReference

class LauncherAccessibilityService: AccessibilityService() {

    private val permissionManager: PermissionsManager by inject()
    private val foregroundBridge: AccessibilityForegroundBridge by inject()
    private val gestureSettings: GestureSettings by inject()

    private var scope: CoroutineScope? = null
    private var overlay: TelosNavOverlay? = null
    private var useTelosRecents = false

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        foregroundBridge.onForegroundPackageChanged(packageName)
        RecentsStore.record(
            packageName,
            event.className?.toString(),
            event.text.firstOrNull(),
        )
    }

    override fun onInterrupt() {
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = WeakReference(this)
        permissionManager.reportAccessibilityServiceState(true)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        this.scope = scope
        overlay = TelosNavOverlay(this) {
            if (useTelosRecents) openTelosRecents()
            else performGlobalAction(GLOBAL_ACTION_RECENTS)
        }
        scope.launch {
            gestureSettings.telosNavigation.collect { overlay?.setEnabled(it) }
        }
        scope.launch {
            gestureSettings.telosRecents.collect { useTelosRecents = it }
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        permissionManager.reportAccessibilityServiceState(false)
        overlay?.setEnabled(false)
        overlay = null
        scope?.cancel()
        scope = null
        instance = null
        return super.onUnbind(intent)
    }

    fun openTelosRecents() {
        runCatching {
            startActivity(Intent().apply {
                setClassName(packageName, "de.mm20.launcher2.ui.launcher.TelosRecentsActivity")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            })
        }
    }

    companion object {
        private var instance: WeakReference<LauncherAccessibilityService>? = null
        internal fun getInstance(): LauncherAccessibilityService? {
            return instance?.get()
        }
    }
}
