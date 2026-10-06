package de.mm20.launcher2.comms.radio

import de.mm20.launcher2.comms.model.RadioStation

/** Reads and writes M3U / extended M3U / PLS station playlists (logic taken from Transistor). */
object RadioPlaylists {
    data class Entry(val name: String, val urls: List<String>)

    private val plsLine = Regex("^(File|Title)(\\d+)=(.*)$", RegexOption.IGNORE_CASE)

    fun parse(text: String): List<Entry> {
        val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val entries = mutableListOf<Entry>()
        if (lines.any { it.startsWith("[playlist]", ignoreCase = true) }) {
            val files = sortedMapOf<Int, String>()
            val titles = sortedMapOf<Int, String>()
            for (line in lines) {
                val m = plsLine.find(line) ?: continue
                val n = m.groupValues[2].toIntOrNull() ?: continue
                val value = m.groupValues[3].trim()
                if (m.groupValues[1].equals("File", ignoreCase = true)) files[n] = value
                else titles[n] = value
            }
            for ((n, url) in files) entries += Entry(titles[n].orEmpty(), listOf(url))
        } else {
            var pendingName: String? = null
            for (line in lines) {
                when {
                    line.startsWith("#EXTINF", ignoreCase = true) ->
                        pendingName = line.substringAfter(',', "").trim()
                    line.startsWith("#") -> Unit
                    else -> {
                        entries += Entry(pendingName.orEmpty(), listOf(line))
                        pendingName = null
                    }
                }
            }
        }
        return entries.filter { entry -> entry.urls.all { it.startsWith("http", ignoreCase = true) } }
    }

    fun createM3u(stations: List<RadioStation>): String = buildString {
        append("#EXTM3U\n")
        for (station in stations) {
            append('\n')
            append("#EXTINF:-1,").append(station.name).append('\n')
            append(station.streamUrl).append('\n')
        }
    }
}
