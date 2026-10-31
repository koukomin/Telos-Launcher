package de.mm20.launcher2.data.store.installer

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object StoreTerminalLogs {
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs = _logs.asStateFlow()

    fun log(tag: String, message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        val entry = "[$timestamp][$tag] $message"
        _logs.value = (_logs.value + entry).takeLast(200)
    }

    fun clear() {
        _logs.value = emptyList()
    }
}
