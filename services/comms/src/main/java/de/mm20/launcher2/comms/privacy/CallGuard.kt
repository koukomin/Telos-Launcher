package de.mm20.launcher2.comms.privacy

import android.content.Context
import android.telecom.PhoneAccountHandle
import androidx.fragment.app.FragmentActivity
import de.mm20.launcher2.comms.AuthManager
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
            if (!authManager.authenticateNative(activity, "Confirm call")) return
        }
        SimRouter.place(context, clirNumber, handle)
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
