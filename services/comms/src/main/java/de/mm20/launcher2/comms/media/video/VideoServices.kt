package de.mm20.launcher2.comms.media.video

import de.mm20.launcher2.base.ProcessInfo
import de.mm20.launcher2.comms.remote.SecretBox
import org.json.JSONObject
import java.io.File
import de.mm20.launcher2.preferences.comms.CommsSettings
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** The settings of the online services of Telos Video, with keys and passwords decrypted. */
data class VideoServicesConfig(
    val tmdbKey: String,
    val subtitleKey: String,
    val subtitleUser: String,
    val subtitlePassword: String,
    val languages: String,
    val autoDownload: Boolean,
    val torrentWifiOnly: Boolean,
) {
    /** Posters work without a key (Wikipedia); a TMDB key gives better results */
    val postersEnabled: Boolean get() = true
}

object VideoServices : KoinComponent {
    private val settings: CommsSettings by inject()

    private const val MIRROR = "video_services.json"

    private fun mirrorFile() = ProcessInfo.appContext?.let { File(it.filesDir, MIRROR) }

    /**
     * The player process cannot open the settings store, so the main process keeps a copy of the
     * video settings in a file (keys and passwords stay encrypted in it).
     */
    suspend fun mirror() {
        val s = settings.snapshot.first()
        val file = mirrorFile() ?: return
        val json = JSONObject()
            .put("tmdb", s.tmdbApiKeyEnc).put("subKey", s.subtitleApiKeyEnc)
            .put("subUser", s.subtitleUser).put("subPass", s.subtitlePasswordEnc)
            .put("languages", s.subtitleLanguages).put("auto", s.subtitleAutoDownload)
            .put("wifiOnly", s.torrentWifiOnly)
        val tmp = File(file.parentFile, "$MIRROR.tmp")
        tmp.writeText(json.toString())
        tmp.renameTo(file)
    }

    private fun configFromMirror(): VideoServicesConfig {
        val j = runCatching { JSONObject(mirrorFile()!!.readText()) }.getOrNull()
            ?: return VideoServicesConfig("", "", "", "", "en", false, true)
        return VideoServicesConfig(
            tmdbKey = SecretBox.decrypt(j.optString("tmdb")),
            subtitleKey = SecretBox.decrypt(j.optString("subKey")),
            subtitleUser = j.optString("subUser"),
            subtitlePassword = SecretBox.decrypt(j.optString("subPass")),
            languages = j.optString("languages").ifBlank { "en" },
            autoDownload = j.optBoolean("auto"),
            torrentWifiOnly = j.optBoolean("wifiOnly", true),
        )
    }

    suspend fun config(): VideoServicesConfig {
        if (ProcessInfo.isolatedPlayer) return configFromMirror()
        val s = settings.snapshot.first()
        return VideoServicesConfig(
            tmdbKey = SecretBox.decrypt(s.tmdbApiKeyEnc),
            subtitleKey = SecretBox.decrypt(s.subtitleApiKeyEnc),
            subtitleUser = s.subtitleUser,
            subtitlePassword = SecretBox.decrypt(s.subtitlePasswordEnc),
            languages = s.subtitleLanguages.ifBlank { "en" },
            autoDownload = s.subtitleAutoDownload,
            torrentWifiOnly = s.torrentWifiOnly,
        )
    }

    fun save(config: VideoServicesConfig) {
        settings.setVideoServices(
            tmdbKeyEnc = SecretBox.encrypt(config.tmdbKey.trim()),
            subtitleKeyEnc = SecretBox.encrypt(config.subtitleKey.trim()),
            subtitleUser = config.subtitleUser,
            subtitlePasswordEnc = SecretBox.encrypt(config.subtitlePassword),
            languages = config.languages,
            autoDownload = config.autoDownload,
            torrentWifiOnly = config.torrentWifiOnly,
        )
        // write the copy for the player process from what was just saved
        runCatching {
            val file = mirrorFile() ?: return@runCatching
            val j = JSONObject()
                .put("tmdb", SecretBox.encrypt(config.tmdbKey.trim())).put("subKey", SecretBox.encrypt(config.subtitleKey.trim()))
                .put("subUser", config.subtitleUser.trim()).put("subPass", SecretBox.encrypt(config.subtitlePassword))
                .put("languages", config.languages).put("auto", config.autoDownload).put("wifiOnly", config.torrentWifiOnly)
            val tmp = File(file.parentFile, "$MIRROR.tmp")
            tmp.writeText(j.toString())
            tmp.renameTo(file)
        }
    }
}
