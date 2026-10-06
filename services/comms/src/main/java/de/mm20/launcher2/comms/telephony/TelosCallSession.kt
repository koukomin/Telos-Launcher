package de.mm20.launcher2.comms.telephony

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.VideoProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object TelosCallSession {
    @Volatile
    var inCallService: InCallService? = null

    private val calls = mutableListOf<Call>()
    private val callbacks = mutableMapOf<Call, Call.Callback>()
    private var call: Call? = null
    private var ringingStartedAt = 0L
    private var answeredThisCall = false
    var lastEndedWasMissed: Boolean = false
        private set
    var lastEndedRingMs: Long = 0L
        private set
    var lastEndedNumber: String = ""
        private set
    var lastEndedName: String? = null
        private set

    private val _ui = MutableStateFlow(InCallUiState())
    val ui: StateFlow<InCallUiState> = _ui.asStateFlow()

    fun onCallAdded(newCall: Call) {
        if (newCall in calls) return
        calls.add(newCall)
        val callback = object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                publish()
                when (state) {
                    Call.STATE_RINGING -> {
                        if (ringingStartedAt == 0L) ringingStartedAt = System.currentTimeMillis()
                    }
                    Call.STATE_ACTIVE -> {
                        answeredThisCall = true
                        AutoRedial.onConnected()
                    }
                    Call.STATE_DISCONNECTED -> {
                        recordEnd(call)
                        val ctx = inCallService ?: return
                        AutoRedial.onDisconnected(ctx, call)
                    }
                }
            }

            override fun onDetailsChanged(call: Call, details: Call.Details) {
                publish()
            }

            override fun onConferenceableCallsChanged(call: Call, conferenceableCalls: MutableList<Call>) {
                publish()
            }
        }
        callbacks[newCall] = callback
        newCall.registerCallback(callback)
        call = pickPrimary()
        val number = newCall.details?.handle?.schemeSpecificPart.orEmpty()
        val state = stateOf(newCall)
        if (state == Call.STATE_RINGING) {
            ringingStartedAt = System.currentTimeMillis()
            answeredThisCall = false
        } else if (number.isNotEmpty()) {
            AutoRedial.onOutgoing(number)
        }
        publish()
    }

    private fun recordEnd(ended: Call) {
        val number = ended.details?.handle?.schemeSpecificPart.orEmpty()
        lastEndedNumber = number
        lastEndedName = ended.details?.callerDisplayName?.ifBlank { null }
        lastEndedRingMs = if (ringingStartedAt > 0) System.currentTimeMillis() - ringingStartedAt else 0L
        lastEndedWasMissed = !answeredThisCall && ringingStartedAt > 0
        ringingStartedAt = 0L
        answeredThisCall = false
    }

    fun onCallRemoved(removed: Call) {
        callbacks.remove(removed)?.let { removed.unregisterCallback(it) }
        calls.remove(removed)
        call = pickPrimary()
        publish()
    }

    fun onAudioStateChanged(audioState: CallAudioState?) {
        val route = audioState?.route ?: return
        _ui.value = _ui.value.copy(
            speaker = route == CallAudioState.ROUTE_SPEAKER,
            bluetooth = route == CallAudioState.ROUTE_BLUETOOTH,
        )
    }

    fun setPhoto(uri: String?) {
        if (_ui.value.photoUri == uri) return
        _ui.value = _ui.value.copy(photoUri = uri)
    }

    fun cycleAudioRoute() {
        val service = inCallService ?: return
        val state = service.callAudioState ?: return
        val mask = state.supportedRouteMask
        val order = listOf(
            CallAudioState.ROUTE_WIRED_OR_EARPIECE,
            CallAudioState.ROUTE_BLUETOOTH,
            CallAudioState.ROUTE_SPEAKER,
        ).filter { mask and it == it }
        if (order.isEmpty()) return
        val idx = order.indexOf(state.route).let { if (it < 0) 0 else (it + 1) % order.size }
        service.setAudioRoute(order[idx])
    }

    fun answer() {
        call?.answer(VideoProfile.STATE_AUDIO_ONLY)
    }

    fun reject() {
        val current = call ?: return
        try {
            current.reject(false, "")
        } catch (_: Exception) {
            current.disconnect()
        }
    }

    fun hangup() {
        val current = call ?: return
        val state = stateOf(current)
        if (state == Call.STATE_RINGING) {
            reject()
        } else {
            current.disconnect()
        }
    }

    fun merge() {
        val current = call ?: return
        val conferenceable = current.conferenceableCalls
        if (conferenceable.isNotEmpty()) {
            current.conference(conferenceable.first())
            return
        }
        if ((current.details.callCapabilities and Call.Details.CAPABILITY_MERGE_CONFERENCE) != 0) {
            current.mergeConference()
        }
    }

    fun swap() {
        val holding = calls.find { stateOf(it) == Call.STATE_HOLDING } ?: return
        call?.hold()
        holding.unhold()
        call = holding
        publish()
    }

    fun addCall(context: android.content.Context) {
        call?.hold()
        TelosDialer.openDialpad(context)
    }

    fun toggleMute() {
        val service = inCallService ?: return
        val muted = !_ui.value.muted
        service.setMuted(muted)
        _ui.value = _ui.value.copy(muted = muted)
    }

    fun toggleSpeaker() {
        val service = inCallService ?: return
        val speaker = !_ui.value.speaker
        service.setAudioRoute(
            if (speaker) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_WIRED_OR_EARPIECE
        )
        _ui.value = _ui.value.copy(speaker = speaker)
    }

    fun toggleHold() {
        val current = call ?: return
        if (stateOf(current) == Call.STATE_HOLDING) current.unhold() else current.hold()
    }

    fun playDtmf(digit: Char) {
        val current = call ?: return
        current.playDtmfTone(digit)
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                current.stopDtmfTone()
            } catch (_: Exception) {
            }
        }, 150)
    }

    private fun publish() {
        call = pickPrimary()
        val current = call
        if (current == null) {
            _ui.value = InCallUiState()
            return
        }
        val other = otherCall(current)
        val state = stateOf(current)
        val number = current.details.handle?.schemeSpecificPart.orEmpty()
        val name = current.details.callerDisplayName?.ifBlank { null }
            ?: de.mm20.launcher2.comms.remote.RemotePhonebook.lookup(number)
        val wasActive = _ui.value.active
        val connectedAt = when {
            state == Call.STATE_ACTIVE && _ui.value.connectedAtEpochMs != null -> _ui.value.connectedAtEpochMs
            state == Call.STATE_ACTIVE && !wasActive -> System.currentTimeMillis()
            state == Call.STATE_ACTIVE -> _ui.value.connectedAtEpochMs ?: System.currentTimeMillis()
            else -> null
        }
        _ui.value = InCallUiState(
            hasCall = true,
            incoming = state == Call.STATE_RINGING,
            connecting = state == Call.STATE_DIALING || state == Call.STATE_CONNECTING,
            active = state == Call.STATE_ACTIVE,
            onHold = state == Call.STATE_HOLDING,
            muted = _ui.value.muted,
            speaker = _ui.value.speaker,
            bluetooth = _ui.value.bluetooth,
            number = number,
            name = name,
            photoUri = _ui.value.photoUri,
            connectedAtEpochMs = connectedAt,
            secondNumber = other?.details?.handle?.schemeSpecificPart.orEmpty(),
            secondName = other?.details?.callerDisplayName?.ifBlank { null }
                ?: other?.details?.handle?.schemeSpecificPart?.let { de.mm20.launcher2.comms.remote.RemotePhonebook.lookup(it) },
            canMerge = current.conferenceableCalls.isNotEmpty() ||
                (current.details.callCapabilities and Call.Details.CAPABILITY_MERGE_CONFERENCE) != 0,
            canSwap = other != null,
            conferenceCount = current.children.size.coerceAtLeast(if (isConference(current)) calls.size else 0),
        )
    }

    private fun pickPrimary(): Call? {
        return calls.find { stateOf(it) == Call.STATE_RINGING }
            ?: calls.find { stateOf(it) == Call.STATE_ACTIVE }
            ?: calls.find { stateOf(it) == Call.STATE_DIALING || stateOf(it) == Call.STATE_CONNECTING }
            ?: calls.firstOrNull()
    }

    private fun otherCall(primary: Call): Call? =
        calls.firstOrNull { it != primary && !primary.children.contains(it) }

    private fun isConference(call: Call): Boolean =
        call.details.hasProperty(Call.Details.PROPERTY_CONFERENCE) || call.children.isNotEmpty()

    private fun stateOf(call: Call): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            call.details.state
        } else {
            @Suppress("DEPRECATION")
            call.state
        }
    }
}
