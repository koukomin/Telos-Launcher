package de.mm20.launcher2.oem

import android.content.Context
import android.content.Intent

/**
 * Custom ROM Game Space Hardware Booster.
 * Triggers hardware performance modes in LineageOS, crDroid, Evolution X,
 * and ArrowOS when launching games from Telos Launcher.
 */
object GameSpaceBooster {

    private val GAME_SPACE_INTENTS = listOf(
        Intent("org.lineageos.gameSpace.ACTION_GAME_START"),
        Intent("io.crdroid.gamespace.ACTION_GAME_START"),
        Intent("com.android.settings.action.GAME_SPACE"),
    )

    fun boostGame(context: Context, packageName: String): Boolean {
        for (intent in GAME_SPACE_INTENTS) {
            val targeted = Intent(intent).putExtra("package", packageName)
            if (context.packageManager.queryIntentActivities(targeted, 0).isNotEmpty()) {
                runCatching { context.sendBroadcast(targeted) }
                return true
            }
        }
        return false
    }
}
