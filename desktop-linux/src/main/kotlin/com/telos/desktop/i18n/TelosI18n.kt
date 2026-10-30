package com.telos.desktop.i18n

import java.util.Locale

/**
 * Reads the same Android string XML as `:core:i18n` (copied into the desktop jar).
 * Desktop UI uses those keys; it does not keep a second translation table.
 */
// === TELOS_PENDING_REVIEW_START: telos_window_drag_and_ui_fix ===
object TelosI18n {
    private val table: Map<String, String> by lazy { load() }

    fun t(key: String, vararg args: Any): String {
        val raw = resolve(key) ?: key
        if (args.isEmpty()) return raw
        var out = raw
        args.forEachIndexed { i, v ->
            out = out.replace("%${i + 1}\$s", v.toString()).replace("%${i + 1}\$d", v.toString())
        }
        if (out.contains("%s")) out = out.replaceFirst("%s", args[0].toString())
        return out
    }

    private fun resolve(key: String): String? {
        var cur = table[key] ?: return null
        var guard = 0
        while (cur.startsWith("@string/") && guard++ < 8) {
            cur = table[cur.removePrefix("@string/")] ?: return cur
        }
        return cur
    }

    private fun load(): Map<String, String> {
        val cl = TelosI18n::class.java.classLoader
        val index = cl.getResourceAsStream("telos-android-i18n/index.txt")?.bufferedReader()?.readLines().orEmpty()
        val loc = Locale.getDefault()
        val dirs = localeDirs(loc)
        val out = LinkedHashMap<String, String>()
        for (dir in dirs.reversed()) {
            index.filter { it.startsWith("$dir/") }.forEach { rel ->
                cl.getResourceAsStream("telos-android-i18n/$rel")?.use { parseXml(it.readBytes().decodeToString(), out) }
            }
        }
        return out
    }

    private fun localeDirs(loc: Locale): List<String> {
        val lang = loc.language
        val country = loc.country
        val list = ArrayList<String>()
        list += "values"
        if (lang == "nb" || lang == "no") list += "values-nb-rNO"
        if (lang == "pt" && country == "BR") list += "values-pt-rBR" else if (lang == "pt") list += "values-pt"
        if (lang == "zh" && (country == "TW" || loc.script == "Hant")) list += "values-zh-rTW"
        else if (lang == "zh") list += "values-zh-rCN"
        if (lang.isNotBlank() && lang != "en") {
            if ("values-$lang" !in list) list += "values-$lang"
            if (country.isNotBlank()) {
                val tagged = "values-$lang-r$country"
                if (tagged !in list) list += tagged
            }
        }
        return list
    }

    private val stringTag = Regex("""<string\s+name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)

    private fun parseXml(xml: String, out: MutableMap<String, String>) {
        stringTag.findAll(xml).forEach { m ->
            val name = m.groupValues[1]
            var v = m.groupValues[2]
                .replace("\\n", "\n")
                .replace("\\'", "'")
                .replace("\\\"", "\"")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#160;", " ")
                .trim()
            if (v.startsWith("<")) return@forEach
            out[name] = v
        }
    }
}
// === TELOS_PENDING_REVIEW_END: telos_window_drag_and_ui_fix ===
