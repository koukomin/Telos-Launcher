package de.mm20.launcher2.appmanagement.shizuku

/** Never join separate labels: IP addresses and ports are not pairing codes. */
internal object PairingCodeParser {
    private val code = Regex("^[0-9]{3}\\s?[0-9]{3}$")
    fun extract(texts: List<String>): String? = texts.asSequence().map(String::trim)
        .firstOrNull { code.matches(it) }?.filter { it in '0'..'9' }
}
