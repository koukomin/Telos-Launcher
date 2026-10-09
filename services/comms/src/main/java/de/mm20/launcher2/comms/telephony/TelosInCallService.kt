package de.mm20.launcher2.comms.telephony

import android.content.Intent
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import de.mm20.launcher2.comms.overlay.CallOverlayIntents
import de.mm20.launcher2.comms.recording.RecordingCoordinator
import de.mm20.launcher2.preferences.comms.CommsSettings
import de.mm20.launcher2.base.containedScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
        // read the data of the ended call now, a new call may replace it while the settings load
        val missed = TelosCallSession.lastEndedWasMissed
        val name = TelosCallSession.lastEndedName
        val number = TelosCallSession.lastEndedNumber
        val ringMs = TelosCallSession.lastEndedRingMs
        val context = applicationContext
        // not tied to the service: it may be unbound as soon as the last call is gone
        popupScope.launch {
            val snap = commsSettings.snapshot.first()
            // a missed call needs the missed call popup, any other call the post-call popup
            if (missed && !snap.missedCallPopup) return@launch
            if (!missed && !snap.postCallPopup) return@launch
            val popup = Intent().setClassName(context.packageName, "de.mm20.launcher2.ui.comms.MissedCallPopupActivity")
            popup.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            popup.putExtra(CallOverlayIntents.EXTRA_NAME, name)
            popup.putExtra(CallOverlayIntents.EXTRA_NUMBER, number)
            popup.putExtra(CallOverlayIntents.EXTRA_RING_MS, ringMs)
            try {
                context.startActivity(popup)
            } catch (_: Exception) {
            }
        }
    }

    private companion object {
        val popupScope = containedScope(Dispatchers.Main.immediate)
    }
}
