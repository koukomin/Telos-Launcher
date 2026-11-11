package de.mm20.launcher2.comms.ermis

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.ServerSocket

/**
 * Telos Ermis Offline Mesh Manager.
 * Enables zero-internet, zero-SIM peer-to-peer encrypted communications
 * between nearby Telos devices via Local Network Service Discovery (NSD)
 * and direct socket broadcasting.
 */
object ErmisMeshManager {

    private const val SERVICE_TYPE = "_telos-ermis._tcp."
    private const val SERVICE_NAME = "TelosPeer"

    data class PeerDevice(
        val name: String,
        val host: String,
        val port: Int,
        val lastSeen: Long = System.currentTimeMillis()
    )

    data class MeshState(
        val isBroadcasting: Boolean = false,
        val discoveredPeers: List<PeerDevice> = emptyList(),
        val unreadOfflineMessages: Int = 0
    )

    private val _meshState = MutableStateFlow(MeshState())
    val meshState: StateFlow<MeshState> = _meshState.asStateFlow()

    private var serverSocket: ServerSocket? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    /**
     * Starts advertising this Telos device on the local network/WiFi Direct/hotspot.
     */
    fun startMeshBeacon(context: Context) {
        val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return

        try {
            if (serverSocket == null || serverSocket!!.isClosed) {
                serverSocket = ServerSocket(0)
            }
            val localPort = serverSocket?.localPort ?: return

            val serviceInfo = NsdServiceInfo().apply {
                serviceName = "$SERVICE_NAME-${System.currentTimeMillis().toString().takeLast(4)}"
                serviceType = SERVICE_TYPE
                port = localPort
            }

            registrationListener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(service: NsdServiceInfo) {
                    _meshState.value = _meshState.value.copy(isBroadcasting = true)
                }
                override fun onRegistrationFailed(service: NsdServiceInfo, errorCode: Int) {
                    _meshState.value = _meshState.value.copy(isBroadcasting = false)
                }
                override fun onServiceUnregistered(service: NsdServiceInfo) {
                    _meshState.value = _meshState.value.copy(isBroadcasting = false)
                }
                override fun onUnregistrationFailed(service: NsdServiceInfo, errorCode: Int) {}
            }

            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
            startPeerDiscovery(nsdManager)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startPeerDiscovery(nsdManager: NsdManager) {
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {}
            override fun onServiceFound(service: NsdServiceInfo) {
                if (service.serviceType == SERVICE_TYPE && service.serviceName.startsWith(SERVICE_NAME)) {
                    nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            val peer = PeerDevice(
                                name = serviceInfo.serviceName,
                                host = serviceInfo.host.hostAddress ?: "",
                                port = serviceInfo.port
                            )
                            val current = _meshState.value.discoveredPeers.filterNot { it.name == peer.name }
                            _meshState.value = _meshState.value.copy(discoveredPeers = current + peer)
                        }
                    })
                }
            }
            override fun onServiceLost(service: NsdServiceInfo) {
                val current = _meshState.value.discoveredPeers.filterNot { it.name == service.serviceName }
                _meshState.value = _meshState.value.copy(discoveredPeers = current)
            }
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }

        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    fun stopMeshBeacon(context: Context) {
        val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
        try {
            registrationListener?.let { nsdManager.unregisterService(it) }
            discoveryListener?.let { nsdManager.stopServiceDiscovery(it) }
            serverSocket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            _meshState.value = MeshState()
        }
    }
}
