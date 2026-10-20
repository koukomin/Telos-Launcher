package de.mm20.launcher2.comms.stremio

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.util.Locale

/** Shared with the isolated video process; call load/save on IO. Empty audio means no preference;
 * empty primary subtitle means disabled. Secondary choices are ordered fallbacks. */
data class StremioPlaybackSettings(
    val audio: String = Locale.getDefault().language,
    val audioFallback: String = "en",
    val subtitles: String = Locale.getDefault().language,
    val subtitleFallback: String = "en",
    val cacheGb: Int = 5,
) {
    val audioLanguages get() = listOf(audio, audioFallback).filter { it.isNotBlank() }.distinct()
    val subtitleLanguages get() = if (subtitles.isBlank()) emptyList() else listOf(subtitles, subtitleFallback).filter { it.isNotBlank() }.distinct()
    val cacheBytes get() = cacheGb.toLong() * 1024 * 1024 * 1024

    companion object {
        val cacheChoices = listOf(1, 2, 5, 10, 20)
        private fun file(context: Context) = File(context.applicationContext.filesDir, "stremio_playback.json")
        fun load(context: Context): StremioPlaybackSettings = runCatching {
            val j = JSONObject(file(context).readText())
            val d = StremioPlaybackSettings()
            fun lang(key: String, default: String) = j.optString(key, default).lowercase(Locale.ROOT).takeIf {
                it.isEmpty() || it in Locale.getISOLanguages().toSet()
            } ?: default
            d.copy(audio = lang("audio", d.audio), audioFallback = lang("audioFallback", d.audioFallback),
                subtitles = lang("subtitles", d.subtitles), subtitleFallback = lang("subtitleFallback", d.subtitleFallback),
                cacheGb = j.optInt("cacheGb", 5).takeIf { it in cacheChoices } ?: 5)
        }.getOrDefault(StremioPlaybackSettings())
        @Synchronized fun save(context: Context, settings: StremioPlaybackSettings) {
            val f = file(context)
            val tmp = File(f.parentFile, "${f.name}.tmp")
            tmp.writeText(JSONObject().put("audio", settings.audio).put("audioFallback", settings.audioFallback)
                .put("subtitles", settings.subtitles).put("subtitleFallback", settings.subtitleFallback)
                .put("cacheGb", settings.cacheGb).toString())
            check(tmp.renameTo(f)) { "Playback preferences could not be saved" }
        }
    }
}
