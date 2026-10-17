package de.mm20.launcher2.freeze

import android.content.Context
import android.content.pm.PackageManager
import de.mm20.launcher2.ktx.isAtLeastApiLevel

/**
 * Apps that usually need another package unfrozen (Gemini → Google, ReVanced → microG, Play-licensed
 * apps → Play Store, games the user tied to a game booster).
 */
object CompanionRules {
    const val PLAY_STORE = "com.android.vending"
    const val GOOGLE_APP = "com.google.android.googlequicksearchbox"
    const val GMS = "com.google.android.gms"

    private val gemini = setOf(
        "com.google.android.apps.bard",
        "com.google.android.googlequicksearchbox",
        "com.google.android.apps.searchlite",
    )
    private val youtube = setOf(
        "com.google.android.youtube",
        "app.revanced.android.youtube",
        "app.rvx.android.youtube",
        "app.morphe.android.youtube",
        "com.vanced.android.youtube",
        "app.revanced.android.youtube.debugging",
    )
    private val youtubeMusic = setOf(
        "com.google.android.apps.youtube.music",
        "app.revanced.android.apps.youtube.music",
        "app.rvx.android.apps.youtube.music",
        "app.morphe.android.apps.youtube.music",
        "com.vanced.android.apps.youtube.music",
    )
    private val microG = listOf(
        "com.google.android.gms",
        "app.revanced.android.gms",
        "com.mgoogle.android.gms",
        "org.microg.gms.droidguard",
        "com.google.android.gsf",
    )

    fun suggested(
        context: Context,
        master: String,
        extra: Map<String, String>,
        gameBoosterPackage: String,
        gameBoosterApps: Set<String>,
        installed: (String) -> Boolean = { pkg ->
            runCatching {
                context.packageManager.getApplicationInfo(pkg, PackageManager.MATCH_UNINSTALLED_PACKAGES)
                true
            }.getOrDefault(false)
        },
    ): List<String> {
        val out = linkedSetOf<String>()
        extra[master]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.let { out.addAll(it) }
        if (master in gemini) {
            if (master != GOOGLE_APP && installed(GOOGLE_APP)) out += GOOGLE_APP
            installedGms(installed)?.let { out += it }
        }
        if (master in youtube || master in youtubeMusic) {
            installedGms(installed)?.let { out += it }
        }
        if (gameBoosterPackage.isNotBlank() && master in gameBoosterApps && installed(gameBoosterPackage)) {
            out += gameBoosterPackage
        }
        if (installedFromPlay(context, master) && installed(PLAY_STORE)) {
            out += PLAY_STORE
        }
        out.remove(master)
        return out.toList()
    }

    private fun installedGms(installed: (String) -> Boolean): String? =
        microG.firstOrNull { installed(it) }

    private fun installedFromPlay(context: Context, packageName: String): Boolean {
        return runCatching {
            val pm = context.packageManager
            val installer = if (isAtLeastApiLevel(30)) {
                pm.getInstallSourceInfo(packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                pm.getInstallerPackageName(packageName)
            }
            installer == PLAY_STORE
        }.getOrDefault(false)
    }

    fun label(context: Context, packageName: String): String {
        return runCatching {
            val pm = context.packageManager
            pm.getApplicationInfo(packageName, PackageManager.MATCH_UNINSTALLED_PACKAGES).loadLabel(pm).toString()
        }.getOrDefault(packageName)
    }
}
