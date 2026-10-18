package de.mm20.launcher2.ui.files.remote

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.merge
import java.net.InetAddress

data class SmbHost(val name: String, val host: String, val port: Int)

/**
 * Finds Windows / Samba computers on the local network by name (mDNS) and
 * accepts a typed host as an IP, a DNS name, a `.local` name, or `smb://host/share`.
 */
object SmbLan {
    fun parseHost(input: String): Pair<String, String> {
        var s = input.trim()
        if (s.startsWith("smb://", true)) s = s.removePrefix("smb://").removePrefix("SMB://")
        s = s.trim().trim('/')
        val host = s.substringBefore('/').substringBefore(':').ifBlank { s }
        val share = s.substringAfter('/', "").substringBefore('/').trim('/', '\\')
        return host to share
    }

    fun discover(context: Context): Flow<List<SmbHost>> {
        val nsd = context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager
        return merge(watch(nsd, "_smb._tcp."), watch(nsd, "_microsoft-ds._tcp."))
    }

    private fun watch(nsd: NsdManager, type: String): Flow<List<SmbHost>> = callbackFlow {
        val found = LinkedHashMap<String, SmbHost>()
        fun emit() { trySend(found.values.sortedBy { it.name.lowercase() }) }
        val listener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {}
            override fun onDiscoveryStarted(serviceType: String?) {}
            override fun onDiscoveryStopped(serviceType: String?) {}
            override fun onServiceFound(service: NsdServiceInfo) {
                val resolve = object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {}
                    override fun onServiceResolved(info: NsdServiceInfo) {
                        val host = hostOf(info) ?: return
                        val name = info.serviceName?.ifBlank { host } ?: host
                        found[host.lowercase()] = SmbHost(name, host, if (info.port > 0) info.port else 445)
                        emit()
                    }
                }
                runCatching { nsd.resolveService(service, resolve) }
            }
            override fun onServiceLost(service: NsdServiceInfo) {
                found.remove(hostOf(service)?.lowercase())
                emit()
            }
        }
        nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener)
        awaitClose { runCatching { nsd.stopServiceDiscovery(listener) } }
    }

    private fun hostOf(info: NsdServiceInfo): String? {
        val addr: InetAddress? = if (Build.VERSION.SDK_INT >= 34) {
            info.hostAddresses.firstOrNull()
        } else {
            @Suppress("DEPRECATION")
            info.host
        }
        return addr?.hostAddress ?: addr?.hostName ?: info.serviceName
    }
}
