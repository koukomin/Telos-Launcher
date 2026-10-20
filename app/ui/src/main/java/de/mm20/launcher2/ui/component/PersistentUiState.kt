package de.mm20.launcher2.ui.component

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Small navigation choices only. Never store credentials, dialogs or pending actions here. */
@Composable
fun <T> rememberPersistentUiState(
    key: String,
    initial: T,
    encode: (T) -> String,
    decode: (String) -> T?,
): MutableState<T> {
    val context = LocalContext.current.applicationContext
    val prefs = remember(context) { context.getSharedPreferences("telos_ui_navigation", Context.MODE_PRIVATE) }
    val state = remember(key) { mutableStateOf(initial) }
    var edited by remember(key) { mutableStateOf(false) }
    LaunchedEffect(key) {
        val saved = withContext(Dispatchers.IO) { prefs.getString(key, null)?.let { runCatching { decode(it) }.getOrNull() } }
        if (!edited && saved != null) state.value = saved
    }
    return remember(key) {
        object : MutableState<T> {
            override var value: T
                get() = state.value
                set(value) {
                    edited = true
                    state.value = value
                    prefs.edit().putString(key, encode(value)).apply()
                }
            override fun component1(): T = value
            override fun component2(): (T) -> Unit = { value = it }
        }
    }
}

@Composable
fun rememberPersistentUiState(key: String, initial: Int): MutableState<Int> =
    rememberPersistentUiState(key, initial, { it.toString() }, { it.toIntOrNull() })
