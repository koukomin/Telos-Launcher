package de.mm20.launcher2.comms.telephony

import android.content.Context
import android.telecom.Call
import de.mm20.launcher2.comms.recording.CallAudioRecorder
import de.mm20.launcher2.comms.recording.RecordingCoordinator
import de.mm20.launcher2.comms.recording.RecordingQuality
import de.mm20.launcher2.preferences.comms.CommsSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import de.mm20.launcher2.base.containedScope
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Starts auto-record when the call actually becomes ACTIVE, even if the in-call UI is not composed. */
internal object CallRecordingHook : KoinComponent {
    private val commsSettings: CommsSettings by inject()
    private val scope = containedScope(Dispatchers.IO)

    fun onActive(context: Context, call: Call) {
        if (CallAudioRecorder.isRecording.value) return
        val number = call.details?.handle?.schemeSpecificPart.orEmpty()
        val token = System.identityHashCode(call).toLong()
        scope.launch {
            val auto = commsSettings.autoRecordCalls.first()
            val mode = commsSettings.autoRecordMode.first()
            val prohibited = commsSettings.recordProhibitedNumbers.first()
            val mandatory = commsSettings.recordMandatoryNumbers.first()
            val clean = number.filter { it.isDigit() || it == '+' }
            val should = when {
                clean.isNotEmpty() && clean in prohibited -> false
                clean.isNotEmpty() && clean in mandatory -> true
                mode == "EXCEPTIONS_ONLY" -> false
                else -> auto
            }
            if (!should) return@launch
            val current = TelosCallSession.ui.value
            if (!current.active || current.actionToken != token) return@launch
            val quality = RecordingQuality.fromKey(commsSettings.recordingQuality.first())
            RecordingCoordinator.start(context, number, quality)
        }
    }
}
