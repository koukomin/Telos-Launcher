package de.mm20.launcher2.network.web3

import java.net.URI

/** Explicit namespaces; an arbitrary blockchain name is not an ordinary DNS hostname. */
object Web3Names {
    val unstoppableSuffixes = setOf("crypto", "x", "polygon", "nft", "blockchain", "bitcoin", "dao", "888", "wallet", "binanceus", "hi", "klever", "kresus", "anime", "manga", "go", "zil")
    private val label = Regex("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?")

    /** ASCII only until an audited ENSIP-15 normalizer is integrated. Never substitute IDNA. */
    fun normalize(input: String): String? {
        val name = input.trim().trimEnd('.').lowercase(java.util.Locale.ROOT)
        if (name.length > 253 || input.any { it.code > 127 }) return null
        val labels = name.split('.')
        return name.takeIf { labels.size >= 2 && labels.all(label::matches) &&
            (labels.last() == "eth" || labels.last() in unstoppableSuffixes) }
    }

    fun httpsUrl(value: String): String? = try {
        val uri = URI(value)
        value.takeIf { uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.rawUserInfo == null }
    } catch (_: Exception) { null }

    fun ipfsGateway(hash: String): String? =
        hash.takeIf { Regex("(?:Qm[1-9A-HJ-NP-Za-km-z]{44}|b[a-z2-7]{20,})").matches(it) }
            ?.let { "https://ipfs.io/ipfs/$it/" }
}
