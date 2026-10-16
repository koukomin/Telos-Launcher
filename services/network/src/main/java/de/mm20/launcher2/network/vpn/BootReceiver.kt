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
 * Restores Telos Network after reboot (opt-in) or app replacement when:
 *  - the user switched on "start on boot",
 *  - the user had the VPN switched on when the device went down (it was not explicitly stopped),
 *  - Android's VPN consent was granted earlier (otherwise a dialog would be needed, which a boot
 *    receiver cannot show).
 * In every other case nothing happens.
 */
class BootReceiver : BroadcastReceiver(), KoinComponent {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)) return
        try {
            val settings = get<NetworkSettings>().current
            if (!VpnRecoveryPolicy.restoreAfterBroadcast(intent?.action == Intent.ACTION_BOOT_COMPLETED, settings.engineWanted, settings.startOnBoot)) return
            if (VpnService.prepare(context) != null) return
            get<NetworkEngine>().restoreIfWanted()
        } catch (e: Exception) {
            // never let a failure here crash the boot sequence of the app
        }
    }
}
