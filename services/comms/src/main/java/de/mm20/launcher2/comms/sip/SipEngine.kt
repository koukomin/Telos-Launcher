package de.mm20.launcher2.comms.sip

import android.content.Context
import android.util.Log
import com.tutpro.baresip.Api
import com.tutpro.baresip.BaresipService
import de.mm20.launcher2.i18n.R as I18nR
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
    /** When the call was answered (epoch ms), 0 while it is not established */
    val establishedAt: Long = 0,
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
    @Volatile private var addresses = ""
    @Volatile private var nameservers = ""
    @Volatile private var restartWith: Pair<Context, SipAccount>? = null
    @Volatile private var appContext: Context? = null

    private fun text(id: Int): String = appContext?.getString(id).orEmpty()

    /** True once the account is registered and calls can be placed */
    val isReady: Boolean get() = _registration.value == SipRegistration.Registered

    /**
     * Starts baresip and registers [account] as soon as the engine is up. [linkAddresses] is the list
     * of local addresses as "ip;interface;ip;interface" and [dns] a comma separated list of name
     * servers, because Android does not let baresip discover them itself.
     */
    fun start(context: Context, account: SipAccount, linkAddresses: String = "", dns: String = "") {
        if (!available) return
        appContext = context.applicationContext
        if (running) {
            // A different account (or none) is already running: restart once it has stopped
            restartWith = context to account
            addresses = linkAddresses
            nameservers = dns
            service.baresipStop(false)
            return
        }
        running = true
        pendingAccount = account
        addresses = linkAddresses
        nameservers = dns
        _registration.value = SipRegistration.Registering
        val dir = File(context.filesDir, "sip").apply { mkdirs() }
        File(dir, "config").writeText(config())
        File(dir, "accounts").writeText("")
        File(dir, "contacts").writeText("")
        thread(name = "baresip", isDaemon = true) {
            runCatching { service.baresipStart(dir.absolutePath, addresses, 2, "Telos Phone") }
                .onFailure {
                    Log.e(TAG, "baresip failed", it)
                    running = false
                    _registration.value = SipRegistration.Failed
                }
        }
    }

    fun stop() {
        restartWith = null
        if (!running) return
        service.baresipStop(false)
    }

    /** Called when the network changed: new local addresses, name servers and a new registration */
    fun networkChanged(oldAddresses: List<String>, linkAddresses: String, dns: String) {
        if (!running || uap == 0L) return
        addresses = linkAddresses
        nameservers = dns
        for (ip in oldAddresses) Api.net_rm_address(ip)
        val parts = linkAddresses.split(";").filter { it.isNotEmpty() }
        for (i in 0 until parts.size / 2) Api.net_add_address_ifname(parts[i * 2], parts[i * 2 + 1])
        if (dns.isNotEmpty()) Api.net_use_nameserver(dns)
        Api.uag_reset_transp(true, true)
    }

    /** Places a call to a full SIP address (see SipUri.target). */
    fun dial(uri: String): Boolean {
        if (uap == 0L || _registration.value != SipRegistration.Registered) return false
        if (_call.value.state != SipCallState.None) return false
        val target = uri
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

    /** Base configuration, following the static config of baresip-studio. */
    private fun config(): String {
        val dnsLines = nameservers.split(",").filter { it.isNotBlank() }.joinToString("") {
            if (it.contains(':')) "dns_server [$it]:53\n" else "dns_server $it:53\n"
        }
        return """
            poll_method epoll
            call_local_timeout 60
            call_max_calls 4
            call_hold_other_calls yes
            filter_registrar udp,tcp,tls,ws,wss
            audio_player aaudio,default
            audio_source aaudio,default
            audio_alert aaudio,default
            audio_level no
            ausrc_format s16
            auplay_format s16
            auenc_format s16
            audec_format s16
            audio_buffer 20-160
            audio_silence -35.0
            audio_telev_pt 101
            audio_jitter_buffer_type adaptive
            audio_jitter_buffer_ms 100-200
            audio_jitter_buffer_size 50
            rtp_stats no
            rtp_timeout 60
            rtp_rxmode thread
            sip_verify_server no
            log_level 2
            module aaudio.so
            module stun.so
            module turn.so
            module ice.so
            module srtp.so
            module dtls_srtp.so
            module uuid.so
            module opus.so
            module g711.so
            module_app account.so
            module_app debug_cmd.so
            opus_samplerate 16000
            opus_stereo no
            opus_sprop_stereo no
            opus_cbr no
            opus_inbandfec yes
            opus_application voip
            opus_bitrate 28000
            dtls_srtp_use_ec prime256v1
        """.trimIndent() + "\n" + dnsLines
    }

    // ---- BaresipService.Listener (called from the baresip thread) ----

    override fun onStarted() {
        val account = pendingAccount ?: return
        if (nameservers.isNotEmpty()) Api.net_use_nameserver(nameservers)
        val user = android.net.Uri.encode(account.user)
        // nothing in the name or the server may end the account line or add parameters to it
        val cleanName = account.displayName.filter { it.isLetterOrDigit() || it == ' ' || it == '-' || it == '.' || it == '_' }.trim()
        val name = if (cleanName.isNotBlank()) "\"$cleanName\" " else ""
        if (!isValidDomain(account.domain)) {
            _registration.value = SipRegistration.Failed
            _lastError.value = text(I18nR.string.au_phoneb_sip_err_bad_server)
            return
        }
        // the password is quoted so that characters such as ; or > cannot break the account line
        val pass = account.password.replace("\\", "\\\\").replace("\"", "\\\"")
        val line = "${name}<sip:$user@${account.domain}>;auth_pass=\"$pass\";regint=300;answermode=manual"
        uap = Api.ua_alloc(line)
        if (uap != 0L) Api.ua_register(uap) else {
            _registration.value = SipRegistration.Failed
            _lastError.value = text(I18nR.string.au_phoneb_sip_err_account)
        }
    }

    override fun onStopped(error: String) {
        running = false
        uap = 0L
        _registration.value = SipRegistration.Offline
        _call.value = SipCall()
        if (error.isNotEmpty()) _lastError.value = error
        restartWith?.let { (context, account) ->
            restartWith = null
            start(context, account, addresses, nameservers)
        }
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
            "call incoming" -> {
                if (_call.value.state != SipCallState.None && _call.value.callp != callp) {
                    // already in a call: the second one is declined and must not replace the first
                    Api.ua_hangup(uap, callp, 486, "Busy Here")
                } else {
                    _call.value = SipCall(SipCallState.Incoming, ev.getOrElse(1) { "" }, uap, callp)
                }
            }
            "call ringing" -> _call.value = _call.value.copy(state = SipCallState.Ringing)
            "call established" -> {
                val c = _call.value
                _call.value = c.copy(
                    state = SipCallState.Established,
                    establishedAt = if (c.establishedAt > 0) c.establishedAt else System.currentTimeMillis(),
                )
            }
            "call closed" -> {
                val c = _call.value
                if (c.callp == callp || c.callp == 0L) {
                    if (callp != 0L) Api.call_destroy(callp)
                    // the microphone must not stay muted for the next call
                    Api.calls_mute(false)
                    _call.value = SipCall()
                } else if (callp != 0L) {
                    Api.call_destroy(callp)
                }
            }
        }
    }

    override fun onMessage(uap: Long, peerUri: String, contentType: String, body: ByteArray) = Unit

    override fun onMessageResponse(code: Int, reason: String, time: String) = Unit

    /** A host name or an IP address, optionally with a port */
    internal fun isValidDomain(domain: String): Boolean =
        domain.isNotBlank() && domain.length <= 253 && domain.all { it.isLetterOrDigit() || it == '.' || it == '-' || it == ':' || it == '_' }
}
