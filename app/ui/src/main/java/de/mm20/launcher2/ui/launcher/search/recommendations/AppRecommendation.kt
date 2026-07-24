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
    Browsers(R.string.recommendation_category_browsers, R.drawable.public_24px, listOf("browser", "browsers")),
    Mail(R.string.recommendation_category_mail, R.drawable.mail_24px, listOf("mail", "email", "e-mail")),
    Productivity(R.string.recommendation_category_productivity, R.drawable.docs_24px, listOf("office", "productivity", "docs", "document")),
    Tools(R.string.recommendation_category_tools, R.drawable.handyman_24px, listOf("tool", "tools", "utility", "utilities")),
    PasswordManagers(R.string.recommendation_category_password_managers, R.drawable.lock_24px, listOf("password", "passwords")),
    CloudStorage(R.string.recommendation_category_cloud_storage, R.drawable.storage_24px, listOf("cloud", "storage", "backup")),
    Security(R.string.recommendation_category_security, R.drawable.local_police_24px, listOf("security", "antivirus", "virus", "malware")),
    Communication(R.string.recommendation_category_communication, R.drawable.sms_24px, listOf("chat", "messaging", "messenger")),
}

/**
 * A single curated app suggestion. [storeUrl] is a plain Play Store listing URL, not an affiliate
 * link - there is no affiliate program wired up. If one is added later, replace [storeUrl] with
 * the real tracking URL; until then this must not be labeled "sponsored"/"affiliate" in the UI,
 * since that would claim a commercial relationship that doesn't exist.
 */
data class AppRecommendation(
    val category: RecommendationCategory,
    val name: String,
    val descriptionRes: Int,
    val storeUrl: String,
)

object AppRecommendations {
    val all: List<AppRecommendation> = listOf(
        AppRecommendation(
            RecommendationCategory.Vpn,
            "Proton VPN",
            R.string.recommendation_desc_vpn,
            "https://play.google.com/store/apps/details?id=ch.protonvpn.android",
        ),
        AppRecommendation(
            RecommendationCategory.Browsers,
            "Firefox",
            R.string.recommendation_desc_browsers,
            "https://play.google.com/store/apps/details?id=org.mozilla.firefox",
        ),
        AppRecommendation(
            RecommendationCategory.Mail,
            "Proton Mail",
            R.string.recommendation_desc_mail,
            "https://play.google.com/store/apps/details?id=ch.protonmail.android",
        ),
        AppRecommendation(
            RecommendationCategory.Productivity,
            "Collabora Office",
            R.string.recommendation_desc_productivity,
            "https://play.google.com/store/apps/details?id=com.collabora.libreoffice",
        ),
        AppRecommendation(
            RecommendationCategory.Tools,
            "Simple Gallery",
            R.string.recommendation_desc_tools,
            "https://play.google.com/store/apps/details?id=com.simplemobiletools.gallery.pro",
        ),
        AppRecommendation(
            RecommendationCategory.PasswordManagers,
            "Bitwarden",
            R.string.recommendation_desc_password_managers,
            "https://play.google.com/store/apps/details?id=com.x8bit.bitwarden",
        ),
        AppRecommendation(
            RecommendationCategory.CloudStorage,
            "Nextcloud",
            R.string.recommendation_desc_cloud_storage,
            "https://play.google.com/store/apps/details?id=com.nextcloud.client",
        ),
        AppRecommendation(
            RecommendationCategory.Security,
            "Malwarebytes",
            R.string.recommendation_desc_security,
            "https://play.google.com/store/apps/details?id=org.malwarebytes.antimalware",
        ),
        AppRecommendation(
            RecommendationCategory.Communication,
            "Signal",
            R.string.recommendation_desc_communication,
            "https://play.google.com/store/apps/details?id=org.thoughtcrime.securesms",
        ),
    )

    /** First category whose keywords match [query], or null if none do / query is blank. */
    fun matchQuery(query: String): AppRecommendation? {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return null
        val category = RecommendationCategory.entries.firstOrNull { cat ->
            cat.keywords.any { q.contains(it) }
        } ?: return null
        return all.firstOrNull { it.category == category }
    }

    /** A single recommendation for the empty-query drawer view, rotating by day so it isn't
     * static, without needing per-recomposition randomness. */
    fun forDrawer(dayOfYear: Int): AppRecommendation {
        return all[((dayOfYear % all.size) + all.size) % all.size]
    }
}
