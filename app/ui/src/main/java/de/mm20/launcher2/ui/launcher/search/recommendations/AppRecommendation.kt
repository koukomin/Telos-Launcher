package de.mm20.launcher2.ui.launcher.search.recommendations

import de.mm20.launcher2.ui.R

/**
 * A category of app recommendations, data-driven so new categories/items can be added without
 * touching any UI code. [keywords] are matched against the user's search query (lowercase,
 * substring match) to decide whether this category's recommendation is relevant to what they're
 * looking for.
 */
enum class RecommendationCategory(
    val labelRes: Int,
    val iconRes: Int,
    val keywords: List<String>,
) {
    Vpn(R.string.recommendation_category_vpn, R.drawable.encrypted_24px, listOf("vpn")),
    Mail(R.string.recommendation_category_mail, R.drawable.mail_24px, listOf("mail", "email", "e-mail")),
    PasswordManagers(R.string.recommendation_category_password_managers, R.drawable.lock_24px, listOf("password", "passwords")),
    CloudStorage(R.string.recommendation_category_cloud_storage, R.drawable.storage_24px, listOf("cloud", "storage", "backup")),
    Productivity(R.string.recommendation_category_productivity, R.drawable.docs_24px, listOf("office", "productivity", "docs", "document", "note", "notes", "task", "tasks", "todo")),
    Browsers(R.string.recommendation_category_browsers, R.drawable.public_24px, listOf("browser", "browsers")),
}

/**
 * A single curated app suggestion. [storeUrl] is a plain Play Store listing URL - there is
 * currently no signed affiliate program for any of these apps. The card UI still discloses this
 * relationship as "Affiliate / Supports Telos" ahead of any real deal being in place, so the
 * disclosure is ready the moment one is signed; until then, keep icons generic (not official
 * brand marks) and never use urgency/scarcity language in [descriptionRes].
 */
data class AppRecommendation(
    val category: RecommendationCategory,
    val name: String,
    val descriptionRes: Int,
    val storeUrl: String,
)

object AppRecommendations {
    val all: List<AppRecommendation> = listOf(
        // VPN
        AppRecommendation(
            RecommendationCategory.Vpn,
            "NordVPN",
            R.string.recommendation_desc_nordvpn,
            "https://play.google.com/store/apps/details?id=com.nordvpn.android",
        ),
        AppRecommendation(
            RecommendationCategory.Vpn,
            "Surfshark",
            R.string.recommendation_desc_surfshark,
            "https://play.google.com/store/apps/details?id=com.surfshark.vpnclient.android",
        ),
        AppRecommendation(
            RecommendationCategory.Vpn,
            "Proton VPN",
            R.string.recommendation_desc_protonvpn,
            "https://play.google.com/store/apps/details?id=ch.protonvpn.android",
        ),
        // Mail
        AppRecommendation(
            RecommendationCategory.Mail,
            "Proton Mail",
            R.string.recommendation_desc_protonmail,
            "https://play.google.com/store/apps/details?id=ch.protonmail.android",
        ),
        AppRecommendation(
            RecommendationCategory.Mail,
            "Tutanota",
            R.string.recommendation_desc_tutanota,
            "https://play.google.com/store/apps/details?id=de.tutao.tutanota",
        ),
        // Password managers
        AppRecommendation(
            RecommendationCategory.PasswordManagers,
            "Bitwarden",
            R.string.recommendation_desc_bitwarden,
            "https://play.google.com/store/apps/details?id=com.x8bit.bitwarden",
        ),
        AppRecommendation(
            RecommendationCategory.PasswordManagers,
            "1Password",
            R.string.recommendation_desc_1password,
            "https://play.google.com/store/apps/details?id=com.onepassword.android",
        ),
        // Cloud storage
        AppRecommendation(
            RecommendationCategory.CloudStorage,
            "pCloud",
            R.string.recommendation_desc_pcloud,
            "https://play.google.com/store/apps/details?id=com.pcloud.pcloud",
        ),
        AppRecommendation(
            RecommendationCategory.CloudStorage,
            "MEGA",
            R.string.recommendation_desc_mega,
            "https://play.google.com/store/apps/details?id=mega.privacy.android.app",
        ),
        // Productivity / note-taking
        AppRecommendation(
            RecommendationCategory.Productivity,
            "Notion",
            R.string.recommendation_desc_notion,
            "https://play.google.com/store/apps/details?id=notion.id",
        ),
        AppRecommendation(
            RecommendationCategory.Productivity,
            "Todoist",
            R.string.recommendation_desc_todoist,
            "https://play.google.com/store/apps/details?id=com.todoist",
        ),
        // Browsers
        AppRecommendation(
            RecommendationCategory.Browsers,
            "Brave",
            R.string.recommendation_desc_brave,
            "https://play.google.com/store/apps/details?id=com.brave.browser",
        ),
        AppRecommendation(
            RecommendationCategory.Browsers,
            "Firefox",
            R.string.recommendation_desc_firefox,
            "https://play.google.com/store/apps/details?id=org.mozilla.firefox",
        ),
    )

    /** First category whose keywords match [query], or null if none do / query is blank. */
    fun matchQuery(query: String): AppRecommendation? {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return null
        // Whole-word match: a plain substring match made "cloud" fire for "soundcloud" and
        // "note" for "notebook", i.e. promotions appeared while looking for unrelated apps.
        val words = q.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
        val category = RecommendationCategory.entries.firstOrNull { cat ->
            cat.keywords.any { it == q || words.contains(it) }
        } ?: return null
        return all.firstOrNull { it.category == category }
    }

    /** A single recommendation for the empty-query drawer view, rotating by day so it isn't
     * static, without needing per-recomposition randomness. */
    fun forDrawer(dayOfYear: Int): AppRecommendation {
        return all[((dayOfYear % all.size) + all.size) % all.size]
    }
}
