package de.mm20.launcher2.ui.system

internal data class WifiEntry(val ssid: String, val psk: String?)

/** Never associate a later network's key with an earlier open network. */
internal object SavedWifiParser {
    private val networks = Regex("<Network>([\\s\\S]*?)</Network>")
    private val ssid = Regex("<string name=\"SSID\">([\\s\\S]*?)</string>")
    private val key = Regex("<string name=\"PreSharedKey\">([\\s\\S]*?)</string>")
    private val legacy = Regex("network\\s*=\\s*\\{([^}]*)\\}")
    private val legacySsid = Regex("(?m)^\\s*ssid=\"([^\"]+)\"")
    private val legacyKey = Regex("(?m)^\\s*psk=\"([^\"]+)\"")
    private fun decode(value: String): String = value.replace("&quot;", "\"").replace("&apos;", "'")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&").trim('"')
    fun parse(raw: String): List<WifiEntry> {
        val xml = networks.findAll(raw).mapNotNull { block ->
            val name = ssid.find(block.value)?.groupValues?.get(1)?.let(::decode) ?: return@mapNotNull null
            val password = key.find(block.value)?.groupValues?.get(1)?.let(::decode)
                ?.takeIf { it.isNotBlank() && it != "*" }
            WifiEntry(name, password)
        }.toList()
        val conf = legacy.findAll(raw).mapNotNull { block ->
            val name = legacySsid.find(block.groupValues[1])?.groupValues?.get(1) ?: return@mapNotNull null
            WifiEntry(name, legacyKey.find(block.groupValues[1])?.groupValues?.get(1))
        }.toList()
        return (xml + conf).filter { it.ssid.isNotBlank() }.distinctBy { it.ssid }
    }
}

internal fun wifiQrPayload(ssid: String, password: String): String {
    fun escape(value: String) = buildString {
        value.forEach { ch ->
            if (ch == '\\' || ch == ';' || ch == ':' || ch == ',' || ch == '"') append('\\')
            append(ch)
        }
    }
    return "WIFI:T:WPA;S:${escape(ssid)};P:${escape(password)};;"
}
