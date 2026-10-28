package dev.izumi.appopsnext.settings

import java.util.Locale

/** The embedded component edits the host-wide Android application language. */
enum class AppLanguage(val languageTags: String) {
    SYSTEM(""),
    ENGLISH("en"),
    GREEK("el"),
    SIMPLIFIED_CHINESE("zh-Hans"),
    TRADITIONAL_CHINESE("zh-Hant"),
    AR("ar"),
    AZ("az"),
    BE("be"),
    BG("bg"),
    BN("bn"),
    CA("ca"),
    CS("cs"),
    DA("da"),
    DE("de"),
    EO("eo"),
    ES("es"),
    ET("et"),
    EU("eu"),
    FA("fa"),
    FI("fi"),
    FR("fr"),
    GA("ga"),
    GRC("grc"),
    HI("hi"),
    HR("hr"),
    HU("hu"),
    IA("ia"),
    IN("id"),
    IT("it"),
    IW("he"),
    JA("ja"),
    KO("ko"),
    LA("la"),
    LT("lt"),
    LV("lv"),
    MS("ms"),
    MT("mt"),
    NB_NO("nb-NO"),
    NL("nl"),
    PL("pl"),
    PT("pt"),
    PT_BR("pt-BR"),
    RO("ro"),
    RU("ru"),
    SK("sk"),
    SL("sl"),
    SV("sv"),
    TH("th"),
    TR("tr"),
    UK("uk"),
    VI("vi"),
    ;
    companion object {
        fun fromLanguageTags(languageTags: String): AppLanguage {
            if (languageTags.isBlank()) return SYSTEM
            val locale = Locale.forLanguageTag(languageTags.substringBefore(',').trim())
            if (locale.language == "zh") return if (locale.script == "Hant" || locale.country in setOf("TW", "HK", "MO")) TRADITIONAL_CHINESE else SIMPLIFIED_CHINESE
            return entries.firstOrNull { it.languageTags.equals(locale.toLanguageTag(), true) }
                ?: entries.firstOrNull { it != SYSTEM && Locale.forLanguageTag(it.languageTags).language == locale.language }
                ?: SYSTEM
        }
    }
}
