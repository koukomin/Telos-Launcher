package de.mm20.launcher2.comms.sip

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.CallLog
import android.telephony.TelephonyManager
import androidx.core.app.NotificationCompat
import de.mm20.launcher2.comms.remote.SecretBox
import de.mm20.launcher2.preferences.comms.CommsSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Keeps the SIP account registered while it is switched on, so that calls can be received, and
 * drives the ringing, the call notification, the audio mode and the call log of SIP calls.
 * It only runs while a SIP account is switched on in the settings (see [SipController]).
 */
class SipService : Service(), KoinComponent {

    private val settings: CommsSettings by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var audio: SipAudio
    private var ringtone: Ringtone? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var lastIps: List<String> = emptyList()
    private var lastAddresses = ""

    // bookkeeping for the call log
    private var callStart = 0L
    private var callEstablished = 0L
    private var callPeer = ""
    private var callIncoming = false
    private var inCallAudio = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        audio = SipAudio(this)
        createChannels()
        startInForeground(statusNotification("Starting…"))
        scope.launch { startEngine() }
        scope.launch { SipEngine.registration.collect { updateStatus() } }
        scope.launch { SipEngine.call.collect { onCallChanged(it) } }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ANSWER -> SipEngine.answer()
            ACTION_DECLINE, ACTION_HANGUP -> SipEngine.hangUp()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        networkCallback?.let { runCatching { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(it) } }
        stopRinging()
        if (inCallAudio) audio.leaveCall()
        SipEngine.stop()
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun startEngine() {
        val snap = settings.snapshot.first()
        if (snap.sipUser.isBlank() || snap.sipDomain.isBlank()) {
            stopSelf()
            return
        }
        val info = SipNetwork.current(this)
        lastIps = info.ips
        lastAddresses = info.addresses
        SipEngine.start(
            this,
            SipAccount(snap.sipUser, SecretBox.decrypt(snap.sipPasswordEnc), snap.sipDomain, snap.sipDisplayName),
            info.addresses,
            info.dns,
        )
        watchNetwork()
    }

    private fun watchNetwork() {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                val info = SipNetwork.from(linkProperties)
                if (info.addresses == lastAddresses || info.addresses.isEmpty()) return
                SipEngine.networkChanged(lastIps, info.addresses, info.dns)
                lastIps = info.ips
                lastAddresses = info.addresses
            }
        }
        networkCallback = callback
        runCatching { cm.registerDefaultNetworkCallback(callback) }
    }

    // ---- call handling ----

    private fun onCallChanged(call: SipCall) {
        when (call.state) {
            SipCallState.Incoming -> {
                val busy = getSystemService(TelephonyManager::class.java)?.callState != TelephonyManager.CALL_STATE_IDLE
                if (busy) {
                    SipEngine.hangUp()
                    return
                }
                callStart = System.currentTimeMillis()
                callEstablished = 0
                callPeer = SipUri.user(call.peer)
                callIncoming = true
                startRinging()
                notify(INCOMING_ID, incomingNotification(call))
            }
            SipCallState.Outgoing -> {
                callStart = System.currentTimeMillis()
                callEstablished = 0
                callPeer = SipUri.user(call.peer)
                callIncoming = false
                enterCallAudio()
            }
            SipCallState.Ringing -> Unit
            SipCallState.Established -> {
                stopRinging()
                cancel(INCOMING_ID)
                if (callEstablished == 0L) callEstablished = System.currentTimeMillis()
                enterCallAudio()
                updateStatus()
            }
            SipCallState.None -> {
                if (callPeer.isNotEmpty()) finishCall()
            }
        }
    }

    private fun finishCall() {
        stopRinging()
        cancel(INCOMING_ID)
        if (inCallAudio) {
            audio.leaveCall()
            inCallAudio = false
        }
        val answered = callEstablished > 0
        val seconds = if (answered) ((System.currentTimeMillis() - callEstablished) / 1000).toInt() else 0
        val type = when {
            callIncoming && answered -> CallLog.Calls.INCOMING_TYPE
            callIncoming -> CallLog.Calls.MISSED_TYPE
            else -> CallLog.Calls.OUTGOING_TYPE
        }
        SipCallLog.insert(this, callPeer, type, callStart, seconds)
        if (callIncoming && !answered) notify(MISSED_ID, missedNotification(callPeer))
        callPeer = ""
        callEstablished = 0
        updateStatus()
    }

    private fun enterCallAudio() {
        if (inCallAudio) return
        audio.enterCall()
        inCallAudio = true
    }

    private fun startRinging() {
        if (ringtone?.isPlaying == true) return
        runCatching {
            ringtone = RingtoneManager.getRingtone(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
                ?.also { it.play() }
        }
        runCatching {
            val vibrator = getSystemService(Vibrator::class.java)
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 800), 0))
        }
    }

    private fun stopRinging() {
        runCatching { ringtone?.stop() }
        ringtone = null
        runCatching { getSystemService(Vibrator::class.java)?.cancel() }
    }

    // ---- notifications ----

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_STATUS, "SIP account", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Shows that the SIP account is registered so that calls can be received"
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_CALLS, "SIP calls", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(false)
            }
        )
    }

    private fun startInForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= 30) {
            startForeground(STATUS_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL)
        } else {
            startForeground(STATUS_ID, notification)
        }
    }

    private fun updateStatus() {
        val call = SipEngine.call.value
        val text = when {
            call.state == SipCallState.Established -> "In call with ${SipUri.displayName(this, SipUri.user(call.peer))}"
            call.state != SipCallState.None -> "Call in progress"
            else -> when (SipEngine.registration.value) {
                SipRegistration.Registered -> "Registered, ready for calls"
                SipRegistration.Registering -> "Connecting…"
                SipRegistration.Failed -> "Registration failed: " + SipEngine.lastError.value
                SipRegistration.Offline -> "Offline"
            }
        }
        notify(STATUS_ID, statusNotification(text, call.state != SipCallState.None))
    }

    private fun screenIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent().setClassName(packageName, SipDialer.CALL_ACTIVITY)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun action(name: String, requestCode: Int) = PendingIntent.getService(
        this, requestCode, Intent(this, SipService::class.java).setAction(name),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun statusNotification(text: String, inCall: Boolean = false): Notification =
        NotificationCompat.Builder(this, CHANNEL_STATUS)
            .setSmallIcon(android.R.drawable.stat_sys_phone_call)
            .setContentTitle("Telos SIP")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(screenIntent())
            .apply { if (inCall) addAction(0, "Hang up", action(ACTION_HANGUP, 3)) }
            .build()

    private fun incomingNotification(call: SipCall): Notification {
        val name = SipUri.displayName(this, SipUri.user(call.peer))
        return NotificationCompat.Builder(this, CHANNEL_CALLS)
            .setSmallIcon(android.R.drawable.stat_sys_phone_call)
            .setContentTitle("Incoming SIP call")
            .setContentText(name)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setFullScreenIntent(screenIntent(), true)
            .setContentIntent(screenIntent())
            .addAction(0, "Decline", action(ACTION_DECLINE, 1))
            .addAction(0, "Answer", action(ACTION_ANSWER, 2))
            .build()
    }

    private fun missedNotification(number: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_CALLS)
            .setSmallIcon(android.R.drawable.sym_call_missed)
            .setContentTitle("Missed SIP call")
            .setContentText(SipUri.displayName(this, number))
            .setAutoCancel(true)
            .build()

    private fun notify(id: Int, notification: Notification) {
        runCatching { getSystemService(NotificationManager::class.java).notify(id, notification) }
    }

    private fun cancel(id: Int) {
        runCatching { getSystemService(NotificationManager::class.java).cancel(id) }
    }

    companion object {
        const val ACTION_ANSWER = "de.mm20.launcher2.sip.ANSWER"
        const val ACTION_DECLINE = "de.mm20.launcher2.sip.DECLINE"
        const val ACTION_HANGUP = "de.mm20.launcher2.sip.HANGUP"
        private const val CHANNEL_STATUS = "sip_status"
        private const val CHANNEL_CALLS = "sip_calls"
        private const val STATUS_ID = 7301
        private const val INCOMING_ID = 7302
        private const val MISSED_ID = 7303

        fun start(context: Context) {
            runCatching { androidx.core.content.ContextCompat.startForegroundService(context, Intent(context, SipService::class.java)) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SipService::class.java))
        }
    }
}
