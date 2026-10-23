package de.mm20.launcher2.network.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.net.VpnService
import androidx.core.content.ContextCompat
import de.mm20.launcher2.network.NetworkEngine
import de.mm20.launcher2.network.api.NetworkSettings
import de.mm20.launcher2.network.privatedns.WifiDnsAutomationService
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

/**
 * Restores opt-in Network services after reboot or app replacement. The VPN additionally requires:
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
            val locationGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
            val notificationsGranted = Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
            if (settings.wifiDnsAutomationEnabled && locationGranted && notificationsGranted) {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, WifiDnsAutomationService::class.java)
                        .setAction(WifiDnsAutomationService.ACTION_START),
                )
            }
            if (VpnRecoveryPolicy.restoreAfterBroadcast(intent?.action == Intent.ACTION_BOOT_COMPLETED, settings.engineWanted, settings.startOnBoot) &&
                VpnService.prepare(context) == null
            ) {
                get<NetworkEngine>().restoreIfWanted()
            }
        } catch (e: Exception) {
            // never let a failure here crash the boot sequence of the app
        }
    }
}
