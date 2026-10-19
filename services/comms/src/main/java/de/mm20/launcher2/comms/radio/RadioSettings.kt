package de.mm20.launcher2.comms.radio

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Radio-only choices. Instances share one observable state for UI and playback service. */
class RadioSettings(context: Context) {
    data class Values(
        val theme: String = "system",
        val dynamicColors: Boolean = true,
        val translucent: Boolean = true,
        val controlsScroll: Boolean = false,
        val tapPlays: Boolean = true,
        val updateImages: Boolean = true,
        val largeBuffer: Boolean = false,
        val editMetadata: Boolean = false,
        val editStreamUrls: Boolean = false,
    )
    private val shared = synchronized(RadioSettings::class.java) {
        instance ?: Shared(context.applicationContext).also { instance = it }
    }
    val values: StateFlow<Values> get() = shared.state
    fun update(change: (Values) -> Values) = shared.update(change)

    private class Shared(context: Context) {
        private val prefs = context.getSharedPreferences("telos_radio_settings", Context.MODE_PRIVATE)
        val state = MutableStateFlow(read())
        private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> state.value = read() }
        init { prefs.registerOnSharedPreferenceChangeListener(listener) }
        private fun read() = Values(
            theme = prefs.getString("theme", "system").orEmpty().takeIf { it in setOf("system", "light", "dark") } ?: "system",
            dynamicColors = prefs.getBoolean("dynamicColors", true),
            translucent = prefs.getBoolean("translucent", true),
            controlsScroll = prefs.getBoolean("controlsScroll", false),
            tapPlays = prefs.getBoolean("tapPlays", true),
            updateImages = prefs.getBoolean("updateImages", true),
            largeBuffer = prefs.getBoolean("largeBuffer", false),
            editMetadata = prefs.getBoolean("editMetadata", false),
            editStreamUrls = prefs.getBoolean("editStreamUrls", false),
        )
        @Synchronized fun update(change: (Values) -> Values) {
            val value = change(state.value)
            state.value = value
            prefs.edit().putString("theme", value.theme)
                .putBoolean("dynamicColors", value.dynamicColors).putBoolean("translucent", value.translucent)
                .putBoolean("controlsScroll", value.controlsScroll).putBoolean("tapPlays", value.tapPlays)
                .putBoolean("updateImages", value.updateImages).putBoolean("largeBuffer", value.largeBuffer)
                .putBoolean("editMetadata", value.editMetadata).putBoolean("editStreamUrls", value.editStreamUrls).apply()
        }
    }
    companion object { private var instance: Shared? = null }
}
