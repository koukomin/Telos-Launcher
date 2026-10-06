package de.mm20.launcher2.comms.telephony

import android.content.Intent
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import de.mm20.launcher2.comms.overlay.CallOverlayIntents
import de.mm20.launcher2.comms.recording.RecordingCoordinator
import de.mm20.launcher2.preferences.comms.CommsSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class TelosInCallService : InCallService(), KoinComponent {
    private val commsSettings: CommsSettings by inject()

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        TelosCallSession.inCallService = this
        TelosCallSession.onCallAdded(call)
        CallNotification.show(this, TelosCallSession.ui.value)
        startCallUi()
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        TelosCallSession.onCallRemoved(call)
        if (!TelosCallSession.ui.value.hasCall) {
            RecordingCoordinator.stop()
            CallNotification.cancel(this)
            maybeShowPopup()
            TelosCallSession.inCallService = null
        } else {
            CallNotification.show(this, TelosCallSession.ui.value)
            startCallUi()
        }
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        super.onCallAudioStateChanged(audioState)
        TelosCallSession.onAudioStateChanged(audioState)
    }

    private fun startCallUi() {
        val intent = Intent().setClassName(packageName, TelosDialer.CALL_ACTIVITY)
        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        )
        try {
            startActivity(intent)
        } catch (_: Exception) {
        }
    }

    private fun maybeShowPopup() {
        val snap = runBlocking { commsSettings.snapshot.first() }
        val missed = TelosCallSession.lastEndedWasMissed
        if (!snap.missedCallPopup && !(snap.postCallPopup && !missed)) return
        if (missed && !snap.missedCallPopup) return
        if (!missed && !snap.postCallPopup) return
        val popup = Intent().setClassName(packageName, "de.mm20.launcher2.ui.comms.MissedCallPopupActivity")
        popup.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        popup.putExtra(CallOverlayIntents.EXTRA_NAME, TelosCallSession.lastEndedName)
        popup.putExtra(CallOverlayIntents.EXTRA_NUMBER, TelosCallSession.lastEndedNumber)
        popup.putExtra(CallOverlayIntents.EXTRA_RING_MS, TelosCallSession.lastEndedRingMs)
        try {
            startActivity(popup)
        } catch (_: Exception) {
        }
    }
}
