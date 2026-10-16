package de.mm20.launcher2.network.vpn

/** Android restarts a sticky service with null; always-on starts use the VPN interface action. */
internal object VpnRecoveryPolicy {
    const val MAX_RETRIES = 3
    fun shouldStart(action: String?, wanted: Boolean): Boolean = when (action) {
        "de.mm20.launcher2.network.STOP" -> false
        "de.mm20.launcher2.network.START", "android.net.VpnService" -> true
        else -> wanted
    }
    fun restoreAfterBroadcast(isBoot: Boolean, wanted: Boolean, startOnBoot: Boolean) =
        wanted && (!isBoot || startOnBoot)
}
