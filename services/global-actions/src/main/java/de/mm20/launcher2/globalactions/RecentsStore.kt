package de.mm20.launcher2.globalactions

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RecentTask(
    val packageName: String,
    val className: String?,
    val label: CharSequence?,
    val time: Long,
)

object RecentsStore {
    private val ownPackages = setOf(
        "de.mm20.launcher2",
        "com.dimitris.telos",
        "com.dimitris.telos.debug",
    )
    private val _tasks = MutableStateFlow<List<RecentTask>>(emptyList())
    val tasks: StateFlow<List<RecentTask>> = _tasks.asStateFlow()

    fun record(packageName: String, className: String?, label: CharSequence?) {
        if (packageName.isBlank()) return
        if (ownPackages.any { packageName.startsWith(it) }) return
        if (packageName == "com.android.systemui") return
        val now = System.currentTimeMillis()
        val next = _tasks.value.filterNot { it.packageName == packageName }.toMutableList()
        next.add(0, RecentTask(packageName, className, label, now))
        _tasks.value = next.take(24)
    }

    fun remove(packageName: String) {
        _tasks.value = _tasks.value.filterNot { it.packageName == packageName }
    }

    fun clear() {
        _tasks.value = emptyList()
    }
}
