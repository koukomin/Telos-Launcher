package de.mm20.launcher2.network.nextdns

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.create

class NextDnsRepository(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("nextdns_prefs", Context.MODE_PRIVATE)

    fun saveToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    private fun authHeader(): String? = getToken()?.let { "Bearer $it" }

    private val api: NextDnsApi by lazy {
        val json = Json { ignoreUnknownKeys = true }
        val contentType = "application/json".toMediaType()
        val client = OkHttpClient.Builder().build()
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.nextdns.io")
            .addConverterFactory(json.asConverterFactory(contentType))
            .client(client)
            .build()
        retrofit.create()
    }

    suspend fun fetchLogs(limit: Int = 100, cursor: String? = null): Result<NextDnsLogResponse> = withContext(Dispatchers.IO) {
        try {
            val auth = authHeader() ?: return@withContext Result.failure(Exception("No token"))
            val resp = api.getLogs(auth, limit, cursor)
            Result.success(resp)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun postRule(domain: String, allow: Boolean): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val auth = authHeader() ?: return@withContext Result.failure(Exception("No token"))
            val req = NextDnsRuleRequest(value = domain, action = if (allow) "allow" else "deny")
            val resp = api.postRule(auth, req)
            Result.success(resp.success)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        private const val KEY_TOKEN = "nextdns_token"
    }
}
