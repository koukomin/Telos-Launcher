package de.mm20.launcher2.ktx

import android.content.Context
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Single shared [OkHttpClient] singleton pool across Telos Launcher (Coil image loader,
 * Telos Store APK downloads, Telos Media YouTube extractor, and network services).
 *
 * Configured with a 20MB disk cache and a 5-connection pool with 5-minute keep-alive
 * to eliminate duplicate socket allocations, thread churn, and memory leaks.
 */
object TelosNetworkClient {

    @Volatile
    private var clientInstance: OkHttpClient? = null

    fun getInstance(context: Context): OkHttpClient {
        return clientInstance ?: synchronized(this) {
            clientInstance ?: createClient(context.applicationContext).also {
                clientInstance = it
            }
        }
    }

    val instance: OkHttpClient
        get() = clientInstance ?: synchronized(this) {
            clientInstance ?: createDefaultClient().also {
                clientInstance = it
            }
        }

    private fun createClient(appContext: Context): OkHttpClient {
        val cacheDir = File(appContext.cacheDir, "http_cache")
        return OkHttpClient.Builder()
            .cache(Cache(cacheDir, 20L * 1024 * 1024))
            .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private fun createDefaultClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }
}
