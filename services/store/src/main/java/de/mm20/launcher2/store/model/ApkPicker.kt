package de.mm20.launcher2.store.model

import android.os.Build

/** One downloadable file of a release. */
data class ReleaseAsset(val name: String, val url: String, val size: Long? = null)

/**
 * Chooses the APK to install from the files of a release: only .apk files, only those matching the
 * user's filter, and when a release has one APK per CPU type the one that fits this phone.
 */
object ApkPicker {
    private val abiTokens = listOf("arm64-v8a", "arm64", "aarch64", "armeabi-v7a", "armv7", "armeabi", "arm", "x86_64", "x86", "i386")

    fun pick(assets: List<ReleaseAsset>, nameRegex: String?): ReleaseAsset? {
        var candidates = assets.filter { it.name.substringBefore('?').endsWith(".apk", ignoreCase = true) }
        if (!nameRegex.isNullOrBlank()) {
            val regex = runCatching { Regex(nameRegex) }.getOrNull()
            if (regex != null) candidates = candidates.filter { regex.containsMatchIn(it.name) }
        }
        if (candidates.size <= 1) return candidates.firstOrNull()
        val preferred = Build.SUPPORTED_ABIS.toList()
        fun rank(a: ReleaseAsset): Int {
            val n = a.name.lowercase()
            val mentioned = abiTokens.filter { n.contains(it) }
            if (mentioned.isEmpty()) return 1 // universal or unknown
            val index = preferred.indexOfFirst { abi -> aliases(abi).any { n.contains(it) } }
            return if (index >= 0) 0 else 3 // exactly for this phone / for another CPU
        }
        return candidates.sortedBy { rank(it) }.first()
    }

    private fun aliases(abi: String): List<String> = when (abi) {
        "arm64-v8a" -> listOf("arm64-v8a", "arm64", "aarch64")
        "armeabi-v7a" -> listOf("armeabi-v7a", "armv7", "armeabi")
        "x86_64" -> listOf("x86_64", "x64")
        "x86" -> listOf("x86", "i386")
        else -> listOf(abi)
    }
}
