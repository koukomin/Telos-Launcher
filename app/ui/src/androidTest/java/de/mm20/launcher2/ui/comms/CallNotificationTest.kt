package de.mm20.launcher2.ui.comms

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import de.mm20.launcher2.comms.telephony.CallNotification
import de.mm20.launcher2.comms.telephony.InCallUiState
import org.junit.Assert.*
import org.junit.Test

/** Reproduces the reported policy and stale-notification failures without a carrier call. */
class CallNotificationTest {
    @Test fun ongoingNotificationPostsWithoutCallStyleAndStaleEndEmissionCancelsIt() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        if (Build.VERSION.SDK_INT >= 33) {
            val result = instrumentation.uiAutomation.executeShellCommand(
                "pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
            ParcelFileDescriptor.AutoCloseInputStream(result).use { it.readBytes() }
        }
        val state = InCallUiState(hasCall = true, active = true, actionToken = 42,
            name = "Synthetic call", connectedAtEpochMs = System.currentTimeMillis() - 2000)
        val notification = CallNotification.create(context, state, false)
        assertNull(notification.fullScreenIntent)
        assertFalse(notification.extras.getString(Notification.EXTRA_TEMPLATE).orEmpty().contains("CallStyle"))
        assertEquals(1, notification.actions.size)
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(notification.channelId, "Test calls", NotificationManager.IMPORTANCE_LOW))
        try {
            // This used to throw the exact IllegalArgumentException on Android 14+.
            manager.notify(7201, notification)
            await { manager.activeNotifications.any { it.id == 7201 } }
            // The live session has ended; even an old active-state emission must clear the notification.
            CallNotification.show(context, state)
            await { manager.activeNotifications.none { it.id == 7201 } }
        } finally { CallNotification.cancel(context) }
    }

    @Test fun onlyFirstIncomingPresentationUsesFullScreenCallStyle() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val ringing = InCallUiState(hasCall = true, incoming = true, actionToken = 43, name = "Synthetic caller")
        val first = CallNotification.create(context, ringing, true)
        assertNotNull(first.fullScreenIntent)
        val restored = CallNotification.create(context, ringing, false)
        assertNull(restored.fullScreenIntent)
        assertFalse(restored.extras.getString(Notification.EXTRA_TEMPLATE).orEmpty().contains("CallStyle"))
        assertEquals(2, restored.actions.size)
    }

    private fun await(condition: () -> Boolean) {
        val deadline = android.os.SystemClock.elapsedRealtime() + 5000
        while (!condition() && android.os.SystemClock.elapsedRealtime() < deadline) Thread.sleep(30)
        assertTrue(condition())
    }
}
