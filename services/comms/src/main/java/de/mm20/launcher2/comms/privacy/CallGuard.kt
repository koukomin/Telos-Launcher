package de.mm20.launcher2.comms.privacy

import android.content.Context
import android.telecom.PhoneAccountHandle
import androidx.fragment.app.FragmentActivity
import de.mm20.launcher2.comms.AuthManager
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.comms.telephony.SimRouter
import de.mm20.launcher2.comms.telephony.TelosDialer
import de.mm20.launcher2.preferences.comms.CommsSettings
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

object CallGuard : KoinComponent {
    private val commsSettings: CommsSettings by inject()
    private val authManager = AuthManager()

    suspend fun place(context: Context, number: String, handle: PhoneAccountHandle? = null) {
        if (number.isEmpty()) return
        val snap = commsSettings.snapshot.first()
        val clirNumber = TelosDialer.withClir(number, snap.clirEnabled, snap.clirPrefix)
        if (requiresAuth(clirNumber, snap.callProtectMode, snap.protectedCallNumbers)) {
            val activity = context as? FragmentActivity ?: return
            if (!authManager.authenticateNative(activity, activity.getString(I18nR.string.au_phoneb_confirm_call))) return
        }
        // Calls without an explicit SIM go over SIP when the account is set to be preferred
        if (handle == null && de.mm20.launcher2.comms.sip.SipDialer.isDefault() &&
            de.mm20.launcher2.comms.sip.SipDialer.place(context, number)
        ) return
        SimRouter.place(context, clirNumber, handle)
    }

    /** Places the call over the SIP account. Returns false when SIP cannot be used right now. */
    suspend fun placeSip(context: Context, number: String): Boolean {
        if (number.isEmpty()) return false
        val snap = commsSettings.snapshot.first()
        if (requiresAuth(number, snap.callProtectMode, snap.protectedCallNumbers)) {
            val activity = context as? FragmentActivity ?: return false
            if (!authManager.authenticateNative(activity, activity.getString(I18nR.string.au_phoneb_confirm_call))) return false
        }
        return de.mm20.launcher2.comms.sip.SipDialer.place(context, number)
    }

    private fun requiresAuth(
        number: String,
        mode: String,
        listed: Map<String, String>,
    ): Boolean {
        return when (mode) {
            "all" -> true
            "listed" -> HiddenContacts.matches(number, listed)
            else -> false
        }
    }
}
