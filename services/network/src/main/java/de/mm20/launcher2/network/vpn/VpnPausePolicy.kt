package de.mm20.launcher2.network.vpn

internal object VpnPausePolicy {
    val durationsMs = listOf(5L, 30L, 60L, 120L).map { it * 60_000L }
    fun pending(wanted: Boolean, deadline: Long, now: Long) = wanted && deadline > now
    fun rebootCancels(previousBoot: Int, currentBoot: Int, startOnBoot: Boolean) =
        previousBoot >= 0 && currentBoot >= 0 && previousBoot != currentBoot && !startOnBoot
}
