package de.mm20.launcher2.comms.sip

import de.mm20.launcher2.base.containedScope
import android.content.Context
import de.mm20.launcher2.preferences.comms.CommsSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Starts the [SipService] while a SIP account is switched on and complete, and stops it again when
 * it is switched off. When no account is switched on nothing of SIP runs and the native library is
 * never loaded.
 */
class SipController(context: Context, settings: CommsSettings) {
    init {
        val appContext = context.applicationContext
        containedScope(Dispatchers.Main).launch {
            settings.snapshot
                .map { Triple(it.sipEnabled, it.sipUser to it.sipDomain, it.sipPasswordEnc) }
                .distinctUntilChanged()
                .collect { (enabled, account, _) ->
                    val usable = enabled && account.first.isNotBlank() && account.second.isNotBlank()
                    if (usable && SipEngine.available) {
                        // a changed account restarts the service with the new data
                        SipService.stop(appContext)
                        SipService.start(appContext)
                    } else {
                        SipService.stop(appContext)
                    }
                }
        }
    }
}
