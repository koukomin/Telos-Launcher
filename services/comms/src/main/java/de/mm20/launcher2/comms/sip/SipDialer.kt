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

    /** Places the call and opens the call screen. Returns false when SIP cannot be used right now. */
    suspend fun place(context: Context, number: String): Boolean {
        if (number.isBlank() || !canDialOut()) return false
        if (!SipEngine.dial(SipUri.target(number, settings.snapshot.first().sipDomain))) return false
        openCallScreen(context)
        return true
    }

    fun openCallScreen(context: Context) {
        context.startActivity(
            Intent().setClassName(context.packageName, CALL_ACTIVITY)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
    }
}
