package de.mm20.launcher2.network.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import de.mm20.launcher2.network.api.ConnectionType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.net.Inet4Address
import java.net.Inet6Address

/** One underlying (non-VPN) network that has internet access. */
internal data class NetInfo(
    val network: Network,
    val type: ConnectionType,
    val hasV4: Boolean,
    val hasV6: Boolean,
    val dnsServers: List<String>,
    /** 0 when Android does not tell (before API 29) */
    val mtu: Int,
    val metered: Boolean,
    val roaming: Boolean,
    val validated: Boolean,
)

/** All underlying networks, best first. */
internal data class NetSnapshot(val networks: List<NetInfo> = emptyList()) {
    val active: NetInfo? get() = networks.firstOrNull()
    val hasV4: Boolean get() = networks.any { it.hasV4 }
    val hasV6: Boolean get() = networks.any { it.hasV6 }
    val dnsServers: List<String> get() = networks.flatMap { it.dnsServers }.distinct()
    val minMtu: Int get() = networks.map { it.mtu }.filter { it > 0 }.minOrNull() ?: 0
}

/**
 * Follows the networks the device is connected to, ignoring VPNs (so also ours). The result drives
 * `setUnderlyingNetworks`, the system DNS, the routes that are put into the tunnel, and the
 * network type that firewall rules see.
 */
internal class NetworkMonitor(context: Context) {
    private val cm = context.getSystemService(ConnectivityManager::class.java)

    private val capabilities = LinkedHashMap<Network, NetworkCapabilities>()
    private val links = LinkedHashMap<Network, LinkProperties>()

    private val _snapshot = MutableStateFlow(NetSnapshot())
    val snapshot: StateFlow<NetSnapshot> = _snapshot

    private var registered = false

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            synchronized(this@NetworkMonitor) {
                cm.getNetworkCapabilities(network)?.let { capabilities[network] = it }
                cm.getLinkProperties(network)?.let { links[network] = it }
            }
            publish()
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            synchronized(this@NetworkMonitor) { capabilities[network] = networkCapabilities }
            publish()
        }

        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
            synchronized(this@NetworkMonitor) { links[network] = linkProperties }
            publish()
        }

        override fun onLost(network: Network) {
            synchronized(this@NetworkMonitor) {
                capabilities.remove(network)
                links.remove(network)
            }
            publish()
        }
    }

    fun start() {
        if (registered) return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        cm.registerNetworkCallback(request, callback)
        registered = true
    }

    fun stop() {
        if (!registered) return
        registered = false
        try {
            cm.unregisterNetworkCallback(callback)
        } catch (e: IllegalArgumentException) {
            // not registered
        }
    }

    private fun publish() {
        val infos = synchronized(this) {
            capabilities.mapNotNull { (network, caps) -> toInfo(network, caps, links[network]) }
        }
        _snapshot.value = NetSnapshot(infos.sortedWith(compareByDescending<NetInfo> { it.validated }.thenBy { priority(it.type) }))
    }

    private fun priority(type: ConnectionType) = when (type) {
        ConnectionType.Ethernet -> 0
        ConnectionType.Wifi -> 1
        ConnectionType.Mobile -> 2
        else -> 3
    }

    private fun toInfo(network: Network, caps: NetworkCapabilities, link: LinkProperties?): NetInfo {
        val type = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> ConnectionType.Ethernet
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> ConnectionType.Wifi
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> ConnectionType.Mobile
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> ConnectionType.Vpn
            else -> ConnectionType.Other
        }
        val addresses = link?.linkAddresses?.map { it.address }.orEmpty()
        return NetInfo(
            network = network,
            type = type,
            hasV4 = addresses.any { it is Inet4Address && !it.isLoopbackAddress },
            hasV6 = addresses.any { it is Inet6Address && !it.isLoopbackAddress && !it.isLinkLocalAddress },
            dnsServers = link?.dnsServers?.mapNotNull { it.hostAddress?.substringBefore('%') }.orEmpty(),
            mtu = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) link?.mtu ?: 0 else 0,
            metered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
            roaming = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING),
            validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
        )
    }
}
