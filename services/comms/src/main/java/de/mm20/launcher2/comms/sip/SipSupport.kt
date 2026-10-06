package de.mm20.launcher2.comms.sip

import android.content.ContentValues
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioAttributes
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Uri
import android.os.Build
import android.provider.CallLog
import android.provider.ContactsContract
import de.mm20.launcher2.comms.remote.RemotePhonebook
import java.net.Inet4Address
import java.net.Inet6Address

/** Helpers around SIP addresses. */
object SipUri {
    /** "Name" <sip:0301234@fritz.box> or sip:0301234@fritz.box -> 0301234 */
    fun user(peer: String): String {
        val inner = peer.substringAfter('<', peer).substringBefore('>')
        return Uri.decode(inner.removePrefix("sip:").removePrefix("sips:").substringBefore('@').substringBefore(';'))
    }

    /** Builds the address to call; plain numbers are called on [domain] */
    fun target(number: String, domain: String): String {
        if (number.startsWith("sip:") || number.startsWith("sips:")) return number
        val user = number.replace(" ", "").replace("#", "%23").replace("*", "%2A")
        return if (number.contains('@')) "sip:$user" else "sip:$user@$domain"
    }

    /** Name from the contacts, else from the remote phonebook, else the number itself */
    fun displayName(context: Context, number: String): String {
        if (number.isBlank()) return number
        val fromContacts = runCatching {
            context.contentResolver.query(
                Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)),
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null
            )?.use { if (it.moveToFirst()) it.getString(0) else null }
        }.getOrNull()
        return fromContacts ?: RemotePhonebook.lookup(number) ?: number
    }
}

/** Writes SIP calls into the system call log so that they show up in Recents. */
object SipCallLog {
    const val ACCOUNT_ID = "telos-sip"

    fun insert(context: Context, number: String, type: Int, startMillis: Long, durationSeconds: Int) {
        runCatching {
            val values = ContentValues().apply {
                put(CallLog.Calls.NUMBER, number)
                put(CallLog.Calls.TYPE, type)
                put(CallLog.Calls.DATE, startMillis)
                put(CallLog.Calls.DURATION, durationSeconds)
                put(CallLog.Calls.NEW, if (type == CallLog.Calls.MISSED_TYPE) 1 else 0)
                put(CallLog.Calls.PHONE_ACCOUNT_ID, ACCOUNT_ID)
            }
            context.contentResolver.insert(CallLog.Calls.CONTENT_URI, values)
        }
    }
}

/** Network information that baresip cannot read on its own on Android. */
object SipNetwork {
    data class Info(val addresses: String, val ips: List<String>, val dns: String)

    fun current(context: Context): Info {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return Info("", emptyList(), "")
        val network = cm.activeNetwork ?: return Info("", emptyList(), "")
        return from(cm.getLinkProperties(network))
    }

    fun from(lp: LinkProperties?): Info {
        if (lp == null) return Info("", emptyList(), "")
        val name = lp.interfaceName ?: return Info("", emptyList(), "")
        val ips = ArrayList<String>()
        for (la in lp.linkAddresses) {
            val a = la.address
            if (a.isLoopbackAddress || a.isLinkLocalAddress) continue
            if (a is Inet4Address || a is Inet6Address) ips += a.hostAddress?.substringBefore('%') ?: continue
        }
        val dns = lp.dnsServers.mapNotNull { it.hostAddress?.substringBefore('%') }.joinToString(",")
        return Info(ips.joinToString(";") { "$it;$name" }, ips, dns)
    }
}

/** Audio routing and focus for SIP calls. */
class SipAudio(private val context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private var focus: AudioFocusRequest? = null

    fun enterCall() {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .build()
        focus = request
        audio.requestAudioFocus(request)
        audio.mode = AudioManager.MODE_IN_COMMUNICATION
    }

    fun leaveCall() {
        setSpeaker(false)
        audio.mode = AudioManager.MODE_NORMAL
        focus?.let { audio.abandonAudioFocusRequest(it) }
        focus = null
    }

    fun setSpeaker(on: Boolean) {
        if (Build.VERSION.SDK_INT >= 31) {
            if (on) {
                audio.availableCommunicationDevices
                    .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                    ?.let { audio.setCommunicationDevice(it) }
            } else {
                audio.clearCommunicationDevice()
            }
        } else {
            @Suppress("DEPRECATION")
            audio.isSpeakerphoneOn = on
        }
    }
}
