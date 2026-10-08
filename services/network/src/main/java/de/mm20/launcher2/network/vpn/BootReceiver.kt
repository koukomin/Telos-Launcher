package de.mm20.launcher2.network.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import de.mm20.launcher2.network.NetworkEngine
import de.mm20.launcher2.network.api.NetworkSettings
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

/**
 * Starts Telos Network after a reboot, but only when ALL of these hold:
 *  - the user switched on "start on boot",
 *  - the user had the VPN switched on when the device went down (it was not stopped, did not fail),
 *  - Android's VPN consent was granted earlier (otherwise a dialog would be needed, which a boot
 *    receiver cannot show).
 * In every other case nothing happens.
 */
class BootReceiver : BroadcastReceiver(), KoinComponent {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        try {
            val settings = get<NetworkSettings>().current
            if (!settings.startOnBoot || !settings.engineWanted) return
            if (VpnService.prepare(context) != null) return
            get<NetworkEngine>().start()
        } catch (e: Exception) {
            // never let a failure here crash the boot sequence of the app
        }
    }
}
