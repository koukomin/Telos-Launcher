package de.mm20.launcher2.network.impl

import de.mm20.launcher2.ktx.PrivilegedShell

data class WifiChannelCongestion(
    val channel: Int,
    val frequencyMhz: Int,
    val networkCount: Int,
    val signalDbmAverage: Int
)

/**
 * Wi-Fi Channel Congestion Radar via Shizuku / Root.
 * Parses raw dumpsys wifi / iw scan data to map channel usage
 * and recommend the least congested Wi-Fi channel.
 */
object WifiChannelRadar {

    suspend fun analyzeChannelCongestion(): List<WifiChannelCongestion> {
        if (!PrivilegedShell.available()) return emptyList()
        val dump = PrivilegedShell.run("dumpsys wifi")?.output.orEmpty()
        if (dump.isBlank()) return emptyList()

        val channelMap = mutableMapOf<Int, MutableList<Int>>()
        val freqRegex = Regex("(?i)freq(?:uency)?[:=]\\s*(\\d{4})")
        val rssiRegex = Regex("(?i)rssi[:=]\\s*(-?\\d+)")

        val linesList = dump.lines()
        var currentFreq: Int? = null
        for (line in linesList) {
            val freqMatch = freqRegex.find(line)
            if (freqMatch != null) {
                currentFreq = freqMatch.groupValues[1].toIntOrNull()
            }
            val rssiMatch = rssiRegex.find(line)
            if (rssiMatch != null && currentFreq != null) {
                val rssi = rssiMatch.groupValues[1].toIntOrNull() ?: -80
                channelMap.getOrPut(currentFreq) { mutableListOf() }.add(rssi)
            }
        }

        return channelMap.map { (freq, rssis) ->
            val channel = mhzToChannel(freq)
            WifiChannelCongestion(
                channel = channel,
                frequencyMhz = freq,
                networkCount = rssis.size,
                signalDbmAverage = if (rssis.isNotEmpty()) rssis.average().toInt() else -100
            )
        }.sortedBy { it.channel }
    }

    private fun mhzToChannel(freq: Int): Int = when {
        freq in 2412..2484 -> (freq - 2407) / 5
        freq in 5170..5825 -> (freq - 5000) / 5
        freq in 5955..7115 -> (freq - 5950) / 5
        else -> 0
    }
}
