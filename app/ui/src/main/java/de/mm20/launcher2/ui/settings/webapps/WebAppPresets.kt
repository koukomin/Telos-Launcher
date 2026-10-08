package de.mm20.launcher2.ui.settings.webapps

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import de.mm20.launcher2.ui.R

/** A suggested website for a [WebAppPresetCategory]. Brand names are intentionally not translated. */
data class WebAppPreset(val name: String, val url: String)

/**
 * A predefined folder users can add with one tap. These are only suggestions: nothing is created
 * until the user asks for it, and the created folder / web apps are ordinary, editable items.
 */
data class WebAppPresetCategory(
    /** Stable id, stored in [de.mm20.launcher2.preferences.WebAppGroup.category]. */
    val id: String,
    @StringRes val nameRes: Int,
    @DrawableRes val iconRes: Int,
    val apps: List<WebAppPreset>,
)

object WebAppPresets {
    val categories: List<WebAppPresetCategory> = listOf(
        WebAppPresetCategory(
            "social", R.string.web_app_preset_category_social, R.drawable.ic_webcat_social,
            listOf(
                WebAppPreset("Facebook", "https://www.facebook.com/"),
                WebAppPreset("Instagram", "https://www.instagram.com/"),
                WebAppPreset("X", "https://x.com/"),
                WebAppPreset("Reddit", "https://www.reddit.com/"),
                WebAppPreset("LinkedIn", "https://www.linkedin.com/"),
                WebAppPreset("Mastodon", "https://mastodon.social/"),
                WebAppPreset("TikTok", "https://www.tiktok.com/"),
                WebAppPreset("Pinterest", "https://www.pinterest.com/"),
            ),
        ),
        WebAppPresetCategory(
            "email", R.string.web_app_preset_category_email, R.drawable.ic_webcat_email,
            listOf(
                WebAppPreset("Gmail", "https://mail.google.com/"),
                WebAppPreset("Outlook", "https://outlook.live.com/mail/"),
                WebAppPreset("Proton Mail", "https://mail.proton.me/"),
                WebAppPreset("Yahoo Mail", "https://mail.yahoo.com/"),
                WebAppPreset("Tuta", "https://app.tuta.com/"),
                WebAppPreset("Fastmail", "https://app.fastmail.com/"),
                WebAppPreset("iCloud Mail", "https://www.icloud.com/mail"),
            ),
        ),
        WebAppPresetCategory(
            "messaging", R.string.web_app_preset_category_messaging, R.drawable.ic_webcat_messaging,
            listOf(
                WebAppPreset("WhatsApp Web", "https://web.whatsapp.com/"),
                WebAppPreset("Telegram Web", "https://web.telegram.org/"),
                WebAppPreset("Messenger", "https://www.messenger.com/"),
                WebAppPreset("Discord", "https://discord.com/app"),
                WebAppPreset("Slack", "https://app.slack.com/client"),
                WebAppPreset("Element", "https://app.element.io/"),
                WebAppPreset("Google Messages", "https://messages.google.com/web/"),
            ),
        ),
        WebAppPresetCategory(
            "video", R.string.web_app_preset_category_video, R.drawable.ic_webcat_video,
            listOf(
                WebAppPreset("YouTube", "https://www.youtube.com/"),
                WebAppPreset("YouTube Music", "https://music.youtube.com/"),
                WebAppPreset("Spotify", "https://open.spotify.com/"),
            ),
        ),
        WebAppPresetCategory(
            "work", R.string.web_app_preset_category_work, R.drawable.ic_webcat_work,
            listOf(
                WebAppPreset("Google Drive", "https://drive.google.com/"),
                WebAppPreset("Google Docs", "https://docs.google.com/"),
                WebAppPreset("Google Calendar", "https://calendar.google.com/"),
                WebAppPreset("Notion", "https://www.notion.so/"),
            ),
        ),
    )

    fun byId(id: String?): WebAppPresetCategory? = categories.find { it.id == id }

    /** Normalizes a URL for duplicate detection: ignores scheme, "www." and trailing slashes. */
    fun comparableUrl(url: String): String =
        url.trim().lowercase()
            .removePrefix("https://").removePrefix("http://")
            .removePrefix("www.")
            .trimEnd('/')
}
