package de.mm20.launcher2.comms.sip

import android.content.Context
import android.content.Intent
import de.mm20.launcher2.preferences.comms.CommsSettings
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Entry point for placing and for opening SIP calls. */
object SipDialer : KoinComponent {
    const val CALL_ACTIVITY = "de.mm20.launcher2.ui.comms.SipCallActivity"

    private val settings: CommsSettings by inject()

    /** True when a SIP account is switched on, registered, and may be used for calling out. */
    suspend fun canDialOut(): Boolean {
        val snap = settings.snapshot.first()
        return snap.sipEnabled && snap.sipOutgoing != "off" && SipEngine.isReady
    }

    /** SIP is preferred over the SIM for calls that have no explicit SIM choice. */
    suspend fun isDefault(): Boolean = canDialOut() && settings.snapshot.first().sipOutgoing == "default"

    /** Why the last [place] failed, for a message to the user */
    @Volatile
    var lastFailure: String = ""
        private set

    /** Places the call and opens the call screen. Returns false when SIP cannot be used right now. */
    suspend fun place(context: Context, number: String): Boolean {
        if (number.isBlank()) return false
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.RECORD_AUDIO
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            lastFailure = "Microphone permission is needed for SIP calls"
            return false
        }
        if (SipEngine.call.value.state != SipCallState.None) {
            lastFailure = "A SIP call is already in progress"
            return false
        }
        if (!canDialOut()) {
            lastFailure = "SIP account is not connected"
            return false
        }
        if (!SipEngine.dial(SipUri.target(number, settings.snapshot.first().sipDomain))) return false
        openCallScreen(context)
        lastFailure = ""
        return true
    }

    fun openCallScreen(context: Context) {
        context.startActivity(
            Intent().setClassName(context.packageName, CALL_ACTIVITY)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
    }
}
