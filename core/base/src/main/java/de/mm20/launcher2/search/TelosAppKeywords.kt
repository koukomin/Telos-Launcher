package de.mm20.launcher2.search

/**
 * Extra search keywords of the Telos virtual apps (matched in addition to the label, with the same
 * Greek-aware matching). Keyed by the domain of the virtual app key (the part before "://").
 */
object TelosAppKeywords {

    private val byDomain: Map<String, List<String>> = mapOf(
        "telos_store_app" to listOf("store", "apps", "updates", "github", "fdroid", "εφαρμογές", "ενημερώσεις", "κατάστημα"),
        "telos_phone_app" to listOf("dialer", "calls", "call", "contacts", "τηλέφωνο", "κλήσεις", "επαφές"),
        "telos_messages_app" to listOf("sms", "mms", "text", "chat", "μηνύματα"),
        "telos_media_app" to listOf(
            "media", "hub", "music", "radio", "video", "player", "audio", "movies", "films", "songs", "stations", "mousiki", "tainies", "radiofono", "vinteo",
            "μέσα", "πολυμέσα", "μουσική", "ραδιόφωνο", "βίντεο", "ταινίες",
        ),
        "telos_radio_app" to listOf("fm", "stations", "ραδιόφωνο", "ραδιοφωνικοί σταθμοί"),
        "telos_music_app" to listOf("songs", "player", "audio", "tracks", "μουσική", "τραγούδια"),
        "telos_video_app" to listOf("movies", "player", "films", "βίντεο", "ταινίες"),
        "telos_photos_app" to listOf(
            "photos", "gallery", "pictures", "images", "camera roll", "albums",
            "φωτογραφίες", "γκαλερί", "εικόνες", "άλμπουμ",
        ),
        "telos_viewer_app" to listOf(
            "viewer", "documents", "docs", "pdf", "office", "word", "excel", "powerpoint", "presentations", "spreadsheets", "ebook", "epub", "reader", "files",
            "προβολή", "έγγραφα", "αρχεία", "παρουσιάσεις", "λογιστικά φύλλα", "αναγνώστης",
        ),
        "telos_files_app" to listOf("file manager", "folders", "storage", "αρχεία", "φάκελοι"),
        "telos_calculator_app" to listOf("calc", "math", "αριθμομηχανή", "υπολογιστής"),
        "telos_notes_app" to listOf("memo", "notebook", "todo", "σημειώσεις"),
        "telos_calendar_app" to listOf("events", "agenda", "schedule", "ημερολόγιο", "εκδηλώσεις"),
        "telos_downloads_app" to listOf("download", "downloader", "λήψεις", "κατεβάσματα"),
        "telos_network_app" to listOf("vpn", "dns", "firewall", "wifi", "internet", "δίκτυο", "τείχος προστασίας"),
        "telos_voice_recorder_app" to listOf("recorder", "recording", "dictaphone", "audio", "καταγραφή ήχου", "εγγραφή φωνής"),
        "telos_screen_recorder_app" to listOf("recorder", "recording", "screencast", "εγγραφή οθόνης"),
        "telos_screenshot_app" to listOf("screen capture", "screengrab", "στιγμιότυπο", "αποτύπωση οθόνης"),
    )

    fun forDomain(domain: String): List<String> = byDomain[domain].orEmpty()

    /** [key] is a virtual app key such as `telos_photos_app://photos`. */
    fun forKey(key: String): List<String> = forDomain(key.substringBefore("://"))
}
