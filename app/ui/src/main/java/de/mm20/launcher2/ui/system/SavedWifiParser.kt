package de.mm20.launcher2.ui.system

internal data class WifiEntry(
    val ssid: String,
    val psk: String?,
    val networkId: Int = -1,
    val security: String = "",
    val hidden: Boolean = false,
    val autoJoin: Boolean = true,
    val existInSystem: Boolean = true,
    val ephemeral: Boolean = false,
    val note: String? = null,
)

/** Never associate a later network's key with an earlier open network. */
internal object SavedWifiParser {
    private val networks = Regex("<Network>([\\s\\S]*?)</Network>")
    private val ssid = Regex("<string name=\"SSID\">([\\s\\S]*?)</string>")
    private val key = Regex("<string name=\"(?:PreSharedKey|Psk|WEPKeys)\">([\\s\\S]*?)</string>")
    private val legacy = Regex("network\\s*=\\s*\\{([^}]*)\\}")
    private val legacySsid = Regex("(?m)^\\s*ssid=\"([^\"]+)\"")
    private val legacyKey = Regex("(?m)^\\s*(?:psk|password)=\"([^\"]+)\"")

    // Dumpsys wifi / CLI output parser
    private val dumpsysSsid = Regex("(?i)(?:SSID|Ssid):\\s*\"?([^\"\r\n]+)\"?")
    private val dumpsysKey = Regex("(?i)(?:PreSharedKey|Psk|Password):\\s*\"?([^\"\r\n]+)\"?")

    private fun decode(value: String): String = value.replace("&quot;", "\"").replace("&apos;", "'")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&").trim('"')

    fun parse(raw: String): List<WifiEntry> {
        val xml = networks.findAll(raw).mapNotNull { block ->
            val name = ssid.find(block.value)?.groupValues?.get(1)?.let(::decode) ?: return@mapNotNull null
            val password = key.find(block.value)?.groupValues?.get(1)?.let(::decode)
                ?.takeIf { it.isNotBlank() && it != "*" && it != "null" }
            WifiEntry(name, password)
        }.toList()
        val conf = legacy.findAll(raw).mapNotNull { block ->
            val name = legacySsid.find(block.groupValues[1])?.groupValues?.get(1) ?: return@mapNotNull null
            val pass = legacyKey.find(block.groupValues[1])?.groupValues?.get(1)?.takeIf { it != "*" && it != "null" }
            WifiEntry(name, pass)
        }.toList()

        val dumpsys = if (xml.isEmpty() && conf.isEmpty()) {
            val blocks = raw.split(Regex("(?m)^(?=\\s*(?:Configured networks|Network|WifiConfiguration|SSID:))"))
            blocks.mapNotNull { block ->
                val name = dumpsysSsid.find(block)?.groupValues?.get(1)?.trim() ?: return@mapNotNull null
                if (name == "null" || name.isBlank()) return@mapNotNull null
                val pass = dumpsysKey.find(block)?.groupValues?.get(1)?.trim()
                    ?.takeIf { it.isNotBlank() && it != "*" && it != "null" }
                WifiEntry(name, pass)
            }
        } else emptyList()

        return (xml + conf + dumpsys).filter { it.ssid.isNotBlank() }.distinctBy { it.ssid }
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
