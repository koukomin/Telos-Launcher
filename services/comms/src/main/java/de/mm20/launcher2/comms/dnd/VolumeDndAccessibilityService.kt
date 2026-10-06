package de.mm20.launcher2.comms.dnd

import android.accessibilityservice.AccessibilityService
import android.app.NotificationManager
import android.os.PowerManager
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import de.mm20.launcher2.preferences.comms.CommsSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.android.inject

class VolumeDndAccessibilityService : AccessibilityService() {
    private val commsSettings: CommsSettings by inject()
    private val seq = ArrayDeque<Int>()
    private var lastAt = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false
        if (event.keyCode != KeyEvent.KEYCODE_VOLUME_UP && event.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) {
            return false
        }
        val snap = runBlocking { commsSettings.snapshot.first() }
        if (!snap.volumeDnd) return false
        if (snap.volumeDndLockOnly) {
            val pm = getSystemService(PowerManager::class.java)
            if (pm?.isInteractive == true) return false
        }
        val now = System.currentTimeMillis()
        if (now - lastAt > 1600) seq.clear()
        lastAt = now
        seq.addLast(event.keyCode)
        while (seq.size > 4) seq.removeFirst()
        val want = listOf(
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_VOLUME_DOWN,
        )
        if (seq.toList() == want) {
            seq.clear()
            toggleDnd()
            return true
        }
        return false
    }

    private fun toggleDnd() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (!nm.isNotificationPolicyAccessGranted) return
        val all = NotificationManager.INTERRUPTION_FILTER_ALL
        val dnd = NotificationManager.INTERRUPTION_FILTER_PRIORITY
        nm.setInterruptionFilter(if (nm.currentInterruptionFilter == all) dnd else all)
    }
}
