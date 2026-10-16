package de.mm20.launcher2.network.web3

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.net.HttpURLConnection
import java.net.URL

data class Web3Resolution(
    val name: String,
    val wallets: Map<String, String>,
    val website: String?,
    val providers: List<String>,
    val partialFailure: Boolean = false,
)

/** Read-only lookup. No AI, signing, wallet connection, or automatic navigation. */
class Web3Resolver(private val fetch: ((String) -> JsonObject)? = null) {
    suspend fun resolve(input: String, unstoppableProxy: String): Web3Resolution = withContext(Dispatchers.IO) {
        val name = Web3Names.normalize(input) ?: throw IllegalArgumentException("Unsupported or invalid name")
        if (name.endsWith(".eth")) {
            val result = runCatching { request("https://api.ensideas.com/ens/resolve/$name") }
            ensureActive()
            val address = result.getOrNull()?.get("address")?.jsonPrimitive?.contentOrNull
                ?.takeIf { Regex("0x[0-9a-fA-F]{40}").matches(it) && it.drop(2).any { c -> c != '0' } }
            ensureActive()
            // eth.limo's DoH endpoint serves DNSLink TXT records, not A/AAAA records.
            val dns = runCatching { request("https://dns.eth.limo/dns-query?name=$name&type=TXT") }
            ensureActive()
            val hasContent = dns.getOrNull()?.get("Answer")?.jsonArray.orEmpty().any {
                it.jsonObject["data"]?.jsonPrimitive?.contentOrNull?.trim('"')?.startsWith("dnslink=/") == true
            }
            Web3Resolution(name, address?.let { mapOf("ETH" to it) }.orEmpty(),
                if (hasContent) "https://$name.limo/" else null,
                listOf("https://api.ensideas.com", "https://dns.eth.limo"),
                result.isFailure || dns.isFailure || result.getOrNull()?.get("error")?.let { it !is JsonNull } == true ||
                    dns.getOrNull()?.get("Status")?.jsonPrimitive?.contentOrNull?.toIntOrNull()?.let { it != 0 } == true)
        } else {
            // The official UD API requires a backend-held API key. Never ship a shared key in APKs.
            val base = Web3Names.httpsUrl(unstoppableProxy.trim().trimEnd('/'))
                ?.takeIf { URL(it).query == null && URL(it).ref == null }
                ?: throw IllegalArgumentException("Configure an HTTPS Unstoppable Domains API proxy")
            val result = request("$base/$name")
            ensureActive()
            val records = checkNotNull(result["records"]?.jsonObject) { "Invalid resolution response" }
            val wallets = records.entries.mapNotNull { (key, value) ->
                val address = value.jsonPrimitive.contentOrNull.orEmpty()
                if (key.startsWith("crypto.") && key.endsWith(".address") && address.isNotBlank() && address.length <= 256)
                    key.removePrefix("crypto.").removeSuffix(".address") to address else null
            }.toMap().toSortedMap()
            val ipfs = records["dweb.ipfs.hash"]?.jsonPrimitive?.contentOrNull?.let(Web3Names::ipfsGateway)
            val redirect = records["browser.redirect_url"]?.jsonPrimitive?.contentOrNull?.let(Web3Names::httpsUrl)
            Web3Resolution(name, wallets, ipfs ?: redirect, listOf(base))
        }
    }

    private fun request(url: String): JsonObject = fetch?.invoke(url) ?: getJson(url)

    private fun getJson(url: String): JsonObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/json")
            check(connection.responseCode in 200..299) { "HTTP ${connection.responseCode}" }
            val bytes = connection.inputStream.use { it.readBytesLimited(512 * 1024) }
            return Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
        } finally { connection.disconnect() }
    }

    private fun java.io.InputStream.readBytesLimited(limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            check(output.size() + count <= limit) { "Response too large" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}
