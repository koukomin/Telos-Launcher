package de.mm20.launcher2.ui.component

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import de.mm20.launcher2.preferences.ui.AppFontSettings

/**
 * Applies the saved per-app font scale to [LocalDensity] for the whole subtree.
 * The slider UI only stored the value; screens never read it.
 */
@Composable
fun ProvidePerAppFontScale(appKey: String?, content: @Composable () -> Unit) {
    val key = appKey?.takeIf { it.isNotBlank() }
    if (key == null) {
        content()
        return
    }
    val context = LocalContext.current
    val settings = remember(context) { AppFontSettings(context) }
    var scale by remember(key) { mutableFloatStateOf(settings.getFontScale(key)) }
    DisposableEffect(key, settings) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changed ->
            if (changed == settings.prefKey(key)) {
                scale = settings.getFontScale(key)
            }
        }
        settings.register(listener)
        onDispose { settings.unregister(listener) }
    }
    val base = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(base.density, base.fontScale * scale),
        content = content,
    )
}

fun telosFontKeyFromPageKey(pageKey: String): String? {
    val slug = pageKey.substringAfter("://", "").ifBlank {
        pageKey.substringAfter("telos_").substringBefore("_app")
    }
    return when (slug) {
        "phone", "messages" -> slug
        "media", "radio", "music", "video" -> "media"
        "photos", "viewer" -> "viewer"
        "files" -> "files"
        "calculator" -> "calculator"
        "notes" -> "notes"
        "calendar" -> "calendar"
        "downloads" -> "downloads"
        "network" -> "network"
        "system" -> "system"
        "voice_recorder", "voice" -> "voice_recorder"
        "screen_recorder" -> "screen_recorder"
        "screenshot" -> "screenshot"
        "sync" -> "sync"
        "backup" -> "backup"
        "weather" -> "weather"
        "store" -> "store"
        "camera" -> "camera"
        "clock" -> "clock"
        "ermis" -> "ermis"
        "flashlight" -> "flashlight"
        else -> slug.ifBlank { null }
    }
}
