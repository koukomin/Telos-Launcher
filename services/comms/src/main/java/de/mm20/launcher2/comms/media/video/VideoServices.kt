package de.mm20.launcher2.comms.media.video

import de.mm20.launcher2.comms.remote.SecretBox
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
    val subtitlesEnabled: Boolean get() = subtitleKey.isNotBlank()
    fun subtitleSearch() = SubtitleSearch(subtitleKey, subtitleUser, subtitlePassword)
}

object VideoServices : KoinComponent {
    private val settings: CommsSettings by inject()

    suspend fun config(): VideoServicesConfig {
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
    }
}
