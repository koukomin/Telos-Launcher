package de.mm20.launcher2.appmanagement.shizuku

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * Finds the wireless-debugging pairing listener (`_adb-tls-pairing._tcp`),
 * the same mDNS type Shizuku-Next uses before it asks for the 6-digit code.
 */
@RequiresApi(Build.VERSION_CODES.R)
internal class TelosAdbPairingDiscovery(
    context: Context,
    private val serviceType: String = TYPE,
    private val onFound: (host: String, port: Int) -> Unit,
) {
    private val contextApp = context.applicationContext
    private val nsd = contextApp.getSystemService(NsdManager::class.java)
    private var running = false

    private val listener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(regType: String) = Unit
        override fun onDiscoveryStopped(serviceType: String) = Unit
        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit
        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            if (!running) return
            nsd.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
                override fun onServiceResolved(resolved: NsdServiceInfo) {
                    if (!running) return
                    val host = resolved.host?.hostAddress ?: return
                    val address = resolved.host ?: return
                    if (!address.isLoopbackAddress && java.net.NetworkInterface.getByInetAddress(address) == null) return
                    val port = resolved.port
                    if (port in 1..65535) onFound(host, port)
                }
            })
        }
    }

    fun start() {
        if (running) return
        if (Build.VERSION.SDK_INT >= 37 && contextApp.checkSelfPermission("android.permission.ACCESS_LOCAL_NETWORK") != android.content.pm.PackageManager.PERMISSION_GRANTED) return
        running = true
        runCatching { nsd.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener) }
    }

    fun stop() {
        if (!running) return
        running = false
        runCatching { nsd.stopServiceDiscovery(listener) }
    }

    companion object {
        const val TYPE = "_adb-tls-pairing._tcp"
    }
}
