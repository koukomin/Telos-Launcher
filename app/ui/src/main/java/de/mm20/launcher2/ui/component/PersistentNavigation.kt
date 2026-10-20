package de.mm20.launcher2.ui.component

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.json.JSONArray
import org.json.JSONObject

/** Restore navigation, never open a dialog or replay an action after process death. */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Composable
fun PersistNavigation(backStack: NavBackStack<NavKey>, root: NavKey) {
    val context = LocalContext.current.applicationContext
    LaunchedEffect(backStack, root) {
        val prefs = context.getSharedPreferences("telos_ui_routes", Context.MODE_PRIVATE)
        val key = root.javaClass.name
        val initial = backStack.toList()
        val restored = withContext(Dispatchers.IO) {
            runCatching {
                val raw = prefs.getString(key, null) ?: return@runCatching emptyList<NavKey>()
                require(raw.length <= 65536)
                val array = JSONArray(raw)
                require(array.length() <= 32)
                (0 until array.length()).map { index ->
                    val entry = array.getJSONObject(index)
                    val name = entry.getString("type")
                    require(name.startsWith("de.mm20.launcher2.ui.") && name.endsWith("Route"))
                    require(!name.contains("Dialog") && !name.contains("Confirm"))
                    val type = Class.forName(name)
                    require(NavKey::class.java.isAssignableFrom(type))
                    Json.decodeFromString(serializer(type), entry.getString("value")) as NavKey
                }
            }.getOrDefault(emptyList())
        }
        if (backStack.toList() == initial && restored.firstOrNull() == root) {
            backStack.clear()
            backStack.addAll(restored)
        }
        snapshotFlow { backStack.toList() }.collect { entries ->
            withContext(Dispatchers.IO) {
                runCatching {
                    if (entries.isEmpty()) { prefs.edit().remove(key).apply(); return@runCatching }
                    val array = JSONArray()
                    for (entry in entries.take(32)) {
                        val name = entry.javaClass.name
                        // Stop before transient prompts; resume the preceding content page.
                        if (!name.endsWith("Route") || name.contains("Dialog") || name.contains("Confirm")) break
                        array.put(JSONObject().put("type", name).put("value", Json.encodeToString(serializer(entry.javaClass), entry)))
                    }
                    val raw = array.toString()
                    if (raw.length <= 65536) prefs.edit().putString(key, raw).apply()
                }
            }
        }
    }
}
