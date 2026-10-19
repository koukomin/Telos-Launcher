package de.mm20.launcher2.network.nextdns

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

interface NextDnsApi {
    @GET("/v1/logs")
    suspend fun getLogs(
        @Header("Authorization") auth: String,
        @Query("limit") limit: Int = 100,
        @Query("cursor") cursor: String? = null,
    ): NextDnsLogResponse

    @POST("/v1/rules")
    suspend fun postRule(
        @Header("Authorization") auth: String,
        @Body req: NextDnsRuleRequest,
    ): NextDnsRuleResponse
}

// Models
@kotlinx.serialization.Serializable
data class NextDnsLogEntry(
    val timestamp: String,
    val device: String?,
    val ip: String?,
    val domain: String,
    val action: String,
    val id: String,
)

@kotlinx.serialization.Serializable
data class NextDnsLogResponse(
    val items: List<NextDnsLogEntry>,
    val cursor: String? = null,
)

@kotlinx.serialization.Serializable
data class NextDnsRuleRequest(
    val type: String = "domain",
    val value: String,
    val action: String, // "allow" or "deny"
)

@kotlinx.serialization.Serializable
data class NextDnsRuleResponse(
    val success: Boolean = true,
)
