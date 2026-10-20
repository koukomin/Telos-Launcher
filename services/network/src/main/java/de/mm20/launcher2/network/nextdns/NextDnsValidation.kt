package de.mm20.launcher2.network.nextdns

import java.net.IDN
import java.util.Locale

object NextDnsValidation {
    private val profile = Regex("[a-f0-9]{6}")
    private val label = Regex("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?")
    fun profileId(value: String): String = value.trim().lowercase(Locale.ROOT).also {
        if (value.any(Char::isISOControl)) throw NextDnsException(NextDnsErrorKind.INVALID)
        if (!profile.matches(it)) throw NextDnsException(NextDnsErrorKind.INVALID)
    }
    fun apiKey(value: String): String = value.trim().also {
        if (value.any(Char::isISOControl)) throw NextDnsException(NextDnsErrorKind.INVALID)
        if (it.length !in 16..256 || it.any { c -> c !in 'a'..'z' && c !in 'A'..'Z' && c !in '0'..'9' && c != '-' && c != '_' })
            throw NextDnsException(NextDnsErrorKind.INVALID)
    }
    fun domain(value: String): String {
        val raw = value.trim().removeSuffix(".")
        if (raw.length !in 3..253 || raw.any { it.isWhitespace() || it in "/\\:@?#%*\u0000" })
            throw NextDnsException(NextDnsErrorKind.INVALID)
        val ascii = try { IDN.toASCII(raw, IDN.USE_STD3_ASCII_RULES).lowercase(Locale.ROOT) }
            catch (_: IllegalArgumentException) { throw NextDnsException(NextDnsErrorKind.INVALID) }
        val parts = ascii.split('.')
        if (ascii.length > 253 || parts.size < 2 || parts.any { !label.matches(it) } || parts.last().all(Char::isDigit))
            throw NextDnsException(NextDnsErrorKind.INVALID)
        return ascii
    }
}
