package de.mm20.launcher2.ui.media.photos

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/** Local favorites, EPUB reading prefs and PDF highlight marks. On-device only. */
internal object ViewerLibrary {
    private const val PREFS = "telos_viewer_library"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun favorites(context: Context): Set<String> =
        prefs(context).getStringSet("fav", emptySet()).orEmpty()

    fun isFavorite(context: Context, uri: Uri): Boolean =
        favorites(context).contains(uri.toString())

    fun toggleFavorite(context: Context, uri: Uri): Boolean {
        val s = uri.toString()
        val next = favorites(context).toMutableSet()
        val now = if (s in next) { next.remove(s); false } else { next.add(s); true }
        prefs(context).edit().putStringSet("fav", next).apply()
        return now
    }

    data class EpubStyle(
        val fontSp: Int = 18,
        val marginDp: Int = 20,
        val serif: Boolean = true,
        val night: Boolean = false,
    )

    fun epubStyle(context: Context): EpubStyle {
        val p = prefs(context)
        return EpubStyle(
            fontSp = p.getInt("epub_font", 18),
            marginDp = p.getInt("epub_margin", 20),
            serif = p.getBoolean("epub_serif", true),
            night = p.getBoolean("epub_night", false),
        )
    }

    fun saveEpubStyle(context: Context, style: EpubStyle) {
        prefs(context).edit()
            .putInt("epub_font", style.fontSp)
            .putInt("epub_margin", style.marginDp)
            .putBoolean("epub_serif", style.serif)
            .putBoolean("epub_night", style.night)
            .apply()
    }

    data class Mark(
        val page: Int,
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val text: String = "",
    )

    fun marks(context: Context, key: String): List<Mark> = runCatching {
        val arr = JSONArray(prefs(context).getString("marks_$key", "[]"))
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            val y = o.optDouble("y", Double.NaN).toFloat()
            if (!y.isNaN() && !o.has("l")) {
                Mark(o.getInt("p"), 0f, y, 1f, (y + 0.08f).coerceAtMost(1f), o.optString("t"))
            } else Mark(
                page = o.getInt("p"),
                left = o.optDouble("l").toFloat(),
                top = o.optDouble("t").toFloat(),
                right = o.optDouble("r", 1.0).toFloat(),
                bottom = o.optDouble("b", 0.08).toFloat(),
                text = o.optString("tx"),
            )
        }
    }.getOrDefault(emptyList())

    fun addMark(context: Context, key: String, mark: Mark) {
        saveMarks(context, key, marks(context, key) + mark)
    }

    fun removeMark(context: Context, key: String, mark: Mark) {
        saveMarks(context, key, marks(context, key).filter { it != mark })
    }

    fun saveMarks(context: Context, key: String, list: List<Mark>) {
        val arr = JSONArray()
        list.takeLast(200).forEach {
            arr.put(
                JSONObject()
                    .put("p", it.page)
                    .put("l", it.left.toDouble())
                    .put("t", it.top.toDouble())
                    .put("r", it.right.toDouble())
                    .put("b", it.bottom.toDouble())
                    .put("tx", it.text),
            )
        }
        prefs(context).edit().putString("marks_$key", arr.toString()).apply()
    }

    fun clearMarks(context: Context, key: String) {
        prefs(context).edit().remove("marks_$key").apply()
    }
}
