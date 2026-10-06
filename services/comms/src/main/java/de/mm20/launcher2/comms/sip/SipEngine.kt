package de.mm20.launcher2.comms.sip

import android.content.Context
import android.util.Log
import com.tutpro.baresip.Api
import com.tutpro.baresip.BaresipService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import kotlin.concurrent.thread

/** Account data for one SIP registration, for example a FRITZ!Box IP telephone. */
data class SipAccount(
    val user: String,
    val password: String,
    val domain: String,
    val displayName: String = "",
)

enum class SipRegistration { Offline, Registering, Registered, Failed }

enum class SipCallState { None, Outgoing, Ringing, Incoming, Established }

data class SipCall(
    val state: SipCallState = SipCallState.None,
    val peer: String = "",
    val uap: Long = 0,
    val callp: Long = 0,
)

/**
 * Telos SIP engine on top of baresip (through the JNI bridge from baresip-studio).
 * Everything is a no-op when the app was built without the native libraries ([available] is false).
 */
object SipEngine : BaresipService.Listener {

    private const val TAG = "SipEngine"

    val available: Boolean get() = BaresipService.available

    private val service = BaresipService().also { it.listener = this }

    private val _registration = MutableStateFlow(SipRegistration.Offline)
    val registration: StateFlow<SipRegistration> = _registration

    private val _call = MutableStateFlow(SipCall())
    val call: StateFlow<SipCall> = _call

    private val _lastError = MutableStateFlow("")
    val lastError: StateFlow<String> = _lastError

    @Volatile private var running = false
    @Volatile private var pendingAccount: SipAccount? = null
    @Volatile private var uap = 0L

    /** Starts baresip and registers [account] as soon as the engine is up. */
    fun start(context: Context, account: SipAccount) {
        if (!available || running) return
        running = true
        pendingAccount = account
        _registration.value = SipRegistration.Registering
        val dir = File(context.filesDir, "sip").apply { mkdirs() }
        File(dir, "config").writeText(config())
        File(dir, "accounts").writeText("")
        File(dir, "contacts").writeText("")
        thread(name = "baresip", isDaemon = true) {
            runCatching { service.baresipStart(dir.absolutePath, "", 2, "Telos Phone") }
                .onFailure { Log.e(TAG, "baresip failed", it) }
            running = false
        }
    }

    fun stop() {
        if (!running) return
        service.baresipStop(false)
    }

    /** Places a call to a number or SIP address; plain numbers are called on the registered domain. */
    fun dial(target: String): Boolean {
        val account = pendingAccount ?: return false
        if (uap == 0L || _registration.value != SipRegistration.Registered) return false
        val uri = if (target.startsWith("sip:")) target else "sip:${target.replace(" ", "")}@${account.domain}"
        val callp = Api.ua_call_alloc(uap, 0, Api.VIDMODE_OFF)
        if (callp == 0L) return false
        if (Api.call_connect(callp, uri) != 0) return false
        _call.value = SipCall(SipCallState.Outgoing, target, uap, callp)
        return true
    }

    fun answer() {
        val c = _call.value
        if (c.state == SipCallState.Incoming) Api.ua_answer(c.uap, c.callp, Api.VIDMODE_OFF)
    }

    fun hangUp() {
        val c = _call.value
        if (c.callp != 0L) Api.ua_hangup(c.uap, c.callp, 0, "")
    }

    fun setMuted(muted: Boolean) = Api.calls_mute(muted)

    fun sendDigit(digit: Char) {
        val c = _call.value
        if (c.callp != 0L) Api.call_send_digit(c.callp, digit)
    }

    private fun config() = """
        audio_player aaudio,default
        audio_source aaudio,default
        audio_alert aaudio,default
        call_local_timeout 120
        sip_verify_server no
        module opus.so
        module g711.so
        module aaudio.so
        module stun.so
        module turn.so
        module ice.so
        module account.so
        module natpmp.so
        module srtp.so
        module dtls_srtp.so
        module uuid.so
        opus_bitrate 28000
    """.trimIndent() + "\n"

    // ---- BaresipService.Listener (called from the baresip thread) ----

    override fun onStarted() {
        val account = pendingAccount ?: return
        val user = account.user
        val name = if (account.displayName.isNotBlank()) "\"${account.displayName}\" " else ""
        val line = "${name}<sip:$user@${account.domain}>;auth_pass=${account.password};regint=300;answermode=manual"
        uap = Api.ua_alloc(line)
        if (uap != 0L) Api.ua_register(uap) else {
            _registration.value = SipRegistration.Failed
            _lastError.value = "Could not create the SIP account"
        }
    }

    override fun onStopped(error: String) {
        running = false
        uap = 0L
        _registration.value = SipRegistration.Offline
        _call.value = SipCall()
        if (error.isNotEmpty()) _lastError.value = error
    }

    override fun onUaEvent(event: String, uap: Long, callp: Long) {
        val ev = event.split(",")
        when (ev[0]) {
            "registering" -> _registration.value = SipRegistration.Registering
            "registered" -> _registration.value = SipRegistration.Registered
            "registering failed" -> {
                _registration.value = SipRegistration.Failed
                _lastError.value = ev.drop(1).joinToString(",")
            }
            // A SIP INVITE arrived: callp is the SIP message, accept it to create the call
            "incoming call" -> Api.ua_accept(uap, callp)
            "call incoming" -> _call.value = SipCall(SipCallState.Incoming, ev.getOrElse(1) { "" }, uap, callp)
            "call ringing" -> _call.value = _call.value.copy(state = SipCallState.Ringing)
            "call established" -> _call.value = _call.value.copy(state = SipCallState.Established)
            "call closed" -> {
                val c = _call.value
                if (c.callp == callp || c.callp == 0L) {
                    if (callp != 0L) Api.call_destroy(callp)
                    _call.value = SipCall()
                }
            }
        }
    }

    override fun onMessage(uap: Long, peerUri: String, contentType: String, body: ByteArray) = Unit

    override fun onMessageResponse(code: Int, reason: String, time: String) = Unit
}
