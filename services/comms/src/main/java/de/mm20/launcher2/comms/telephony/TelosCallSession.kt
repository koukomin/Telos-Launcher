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
    private val identities = mutableMapOf<String, CallerIdentity>()
    private val states = mutableMapOf<Call, Int>()
    private val callbacks = mutableMapOf<Call, Call.Callback>()
    // when each call became active, kept over hold/unhold so that the call timer does not restart
    private val connectedAt = mutableMapOf<Call, Long>()
    private var call: Call? = null
    private var ringingStartedAt = 0L
    private var answeredThisCall = false
    // outgoing calls that did not yet connect, to vibrate once when the other side answers
    private val pendingOutgoing = mutableSetOf<Call>()
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
        @Suppress("DEPRECATION")
        val initialState = newCall.state
        states[newCall] = initialState
        val callback = object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                if (call !in calls) return
                states[call] = state
                publish()
                when (state) {
                    Call.STATE_RINGING -> {
                        if (ringingStartedAt == 0L) ringingStartedAt = System.currentTimeMillis()
                    }
                    Call.STATE_ACTIVE -> {
                        answeredThisCall = true
                        AutoRedial.onConnected()
                        if (pendingOutgoing.remove(call)) inCallService?.let { CallHaptics.onAnswered(it) }
                        inCallService?.let { svc ->
                            CallRecordingHook.onActive(svc.applicationContext, call)
                        }
                    }
                    Call.STATE_DISCONNECTED -> {
                        recordEnd(call)
                        pendingOutgoing.remove(call)
                        val ctx = inCallService ?: return
                        CallHaptics.onHangup(ctx)
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
            AutoRedial.onIncoming()
        } else {
            if (state != Call.STATE_ACTIVE) pendingOutgoing.add(newCall)
            if (number.isNotEmpty()) AutoRedial.onOutgoing(number)
        }
        publish()
    }

    private fun recordEnd(ended: Call) {
        val number = ended.details?.handle?.schemeSpecificPart.orEmpty()
        lastEndedNumber = number
        lastEndedName = callerName(identities[number]?.name, ended.details?.callerDisplayName,
            de.mm20.launcher2.comms.remote.RemotePhonebook.lookup(number))
        lastEndedRingMs = if (ringingStartedAt > 0) System.currentTimeMillis() - ringingStartedAt else 0L
        lastEndedWasMissed = !answeredThisCall && ringingStartedAt > 0
        ringingStartedAt = 0L
        answeredThisCall = false
    }

    fun onCallRemoved(removed: Call) {
        callbacks.remove(removed)?.let { removed.unregisterCallback(it) }
        states.remove(removed)
        connectedAt.remove(removed)
        pendingOutgoing.remove(removed)
        calls.remove(removed)
        call = pickPrimary()
        publish()
    }

    fun onAudioStateChanged(audioState: CallAudioState?) {
        val route = audioState?.route ?: return
        _ui.value = _ui.value.copy(
            speaker = route == CallAudioState.ROUTE_SPEAKER,
            bluetooth = route == CallAudioState.ROUTE_BLUETOOTH,
            muted = audioState.isMuted,
        )
    }

    fun setIdentity(number: String, identity: CallerIdentity) {
        // A lookup can finish after Telecom has replaced/removed the call.
        if (calls.none { it.details?.handle?.schemeSpecificPart == number }) return
        if (identities[number] == identity) return
        identities[number] = identity
        publish()
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
        val selected = pickPrimary() ?: return
        if (stateOf(selected) != Call.STATE_RINGING) return
        try {
            selected.answer(VideoProfile.STATE_AUDIO_ONLY)
        } catch (e: RuntimeException) {
            // A stale/disconnected OEM Telecom adapter must not crash the launcher process.
            android.util.Log.w("TelosCallSession", "Telecom could not answer the current call", e)
        }
    }

    fun reject() = hangup()

    fun hangup() {
        // Re-resolve against live Telecom calls rather than a cached/disconnected primary.
        val selected = pickPrimary() ?: return
        val current = selected.parent ?: selected
        try {
            if (stateOf(current) == Call.STATE_RINGING) current.reject(false, "")
            else current.disconnect()
        } catch (_: Exception) {
            runCatching { current.disconnect() }
        }
        Handler(Looper.getMainLooper()).postDelayed({
            val service = inCallService ?: return@postDelayed
            // Some OEM Telecom adapters ignore the first Call command. Never end a replacement
            // call, call-waiting call or conference through the global fallback.
            val live = calls.filter { isLive(it) }
            if (live.singleOrNull() !== current || service.calls.none { it === current }) return@postDelayed
            if (Build.VERSION.SDK_INT >= 28) runCatching {
                @Suppress("DEPRECATION")
                service.getSystemService(android.telecom.TelecomManager::class.java)?.endCall()
            }
        }, 2000)
    }

    fun onServiceDestroyed(service: InCallService) {
        if (inCallService !== service) return
        callbacks.forEach { (call, callback) -> runCatching { call.unregisterCallback(callback) } }
        callbacks.clear(); states.clear(); calls.clear(); connectedAt.clear(); pendingOutgoing.clear()
        call = null; inCallService = null
        publish()
    }

    private fun isLive(call: Call): Boolean = stateOf(call) !in
        setOf(Call.STATE_DISCONNECTED, Call.STATE_DISCONNECTING)

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
            identities.clear()
            _ui.value = InCallUiState()
            return
        }
        val other = otherCall(current)
        val state = stateOf(current)
        val details = current.details ?: return // OEM updates can temporarily omit call details.
        val number = details.handle?.schemeSpecificPart.orEmpty()
        val name = callerName(identities[number]?.name, details.callerDisplayName,
            de.mm20.launcher2.comms.remote.RemotePhonebook.lookup(number))
        if (state == Call.STATE_ACTIVE) connectedAt.getOrPut(current) { System.currentTimeMillis() }
        val connectedAtMs = connectedAt[current]
        _ui.value = InCallUiState(
            hasCall = true,
            actionToken = System.identityHashCode(current).toLong(),
            incoming = state == Call.STATE_RINGING,
            connecting = state == Call.STATE_DIALING || state == Call.STATE_CONNECTING,
            active = state == Call.STATE_ACTIVE,
            onHold = state == Call.STATE_HOLDING,
            muted = _ui.value.muted,
            speaker = _ui.value.speaker,
            bluetooth = _ui.value.bluetooth,
            number = number,
            name = name ?: identities[number]?.suggestedName,
            callerNameSuggested = name == null && !identities[number]?.suggestedName.isNullOrBlank(),
            likelySpam = identities[number]?.likelySpam == true,
            photoUri = identities[number]?.photoUri,
            callerSourceUrl = identities[number]?.sourceUrl,
            connectedAtEpochMs = connectedAtMs,
            secondNumber = other?.details?.handle?.schemeSpecificPart.orEmpty(),
            secondName = other?.details?.handle?.schemeSpecificPart?.let { second ->
                callerName(identities[second]?.name, other.details?.callerDisplayName,
                    de.mm20.launcher2.comms.remote.RemotePhonebook.lookup(second))
            },
            canMerge = current.conferenceableCalls.isNotEmpty() ||
                (details.callCapabilities and Call.Details.CAPABILITY_MERGE_CONFERENCE) != 0,
            canSwap = other != null,
            conferenceCount = current.children.size.coerceAtLeast(if (isConference(current)) calls.size else 0),
        )
    }

    private fun pickPrimary(): Call? {
        val live = calls.filter(::isLive)
        val roots = live.filter { it.parent == null }.ifEmpty { live }
        return roots.minByOrNull { callPriority(stateOf(it)) }
    }

    private fun otherCall(primary: Call): Call? =
        calls.firstOrNull { it != primary && !primary.children.contains(it) }

    private fun isConference(call: Call): Boolean =
        call.details.hasProperty(Call.Details.PROPERTY_CONFERENCE) || call.children.isNotEmpty()

    private fun stateOf(call: Call): Int = states[call] ?: run {
        @Suppress("DEPRECATION")
        call.state
    }
}
