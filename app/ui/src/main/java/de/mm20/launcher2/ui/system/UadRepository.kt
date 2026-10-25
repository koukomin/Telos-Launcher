package de.mm20.launcher2.ui.system

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UadInfo(
    val description: String,
    val removal: String, // Safe, Recommended, Advanced, Expert, Unsafe
)

object UadRepository {
    private const val UAD_URL = "https://raw.githubusercontent.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/main/resources/assets/uad_lists.json"
    private var memoryCache: Map<String, UadInfo>? = null

    suspend fun getList(context: Context): Map<String, UadInfo> = withContext(Dispatchers.IO) {
        memoryCache?.let { return@withContext it }
        val cacheFile = File(context.cacheDir, "uad_lists.json")
        if (!cacheFile.exists() || System.currentTimeMillis() - cacheFile.lastModified() > 7 * 24 * 3600 * 1000L) {
            runCatching {
                val conn = URL(UAD_URL).openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                if (conn.responseCode == 200) {
                    val json = conn.inputStream.bufferedReader().use { it.readText() }
                    cacheFile.writeText(json)
                }
            }
        }
        val map = mutableMapOf<String, UadInfo>()
        if (cacheFile.exists()) {
            runCatching {
                val root = JSONObject(cacheFile.readText())
                for (key in root.keys()) {
                    val obj = root.optJSONObject(key) ?: continue
                    map[key] = UadInfo(
                        description = obj.optString("description", ""),
                        removal = obj.optString("removal", "Unknown")
                    )
                }
            }
        }
        memoryCache = map
        map
    }
}
