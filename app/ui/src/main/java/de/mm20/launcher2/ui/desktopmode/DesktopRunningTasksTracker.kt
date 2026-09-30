// === TELOS_PENDING_REVIEW_START: desktop_taskbar_running_apps ===
package de.mm20.launcher2.ui.desktopmode

import android.app.ActivityManager
import android.content.Context
import androidx.core.content.getSystemService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class RunningTaskInfo(
    val taskId: Int,
    val packageName: String,
)

class DesktopRunningTasksTracker(private val context: Context) {
    private val activityManager = context.getSystemService<ActivityManager>()
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private val _runningTasks = MutableStateFlow<List<RunningTaskInfo>>(emptyList())
    val runningTasks = _runningTasks.asStateFlow()

    init {
        scope.launch {
            while (isActive) {
                updateRunningTasks()
                delay(1000)
            }
        }
    }

    private fun updateRunningTasks() {
        val tasks = mutableListOf<RunningTaskInfo>()
        try {
            @Suppress("DEPRECATION")
            val recentTasks = activityManager?.getRecentTasks(15, ActivityManager.RECENT_IGNORE_UNAVAILABLE)
            recentTasks?.forEach { taskInfo ->
                val component = taskInfo.baseIntent?.component
                if (component != null && taskInfo.id >= 0) {
                    tasks.add(
                        RunningTaskInfo(
                            taskId = taskInfo.id,
                            packageName = component.packageName
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // Ignore SecurityExceptions
        }
        // distinct filtering by packageName
        _runningTasks.value = tasks.distinctBy { it.packageName }
    }
    
    fun bringTaskToFront(taskId: Int) {
        try {
            activityManager?.moveTaskToFront(taskId, ActivityManager.MOVE_TASK_WITH_HOME)
        } catch (e: Exception) {
            // Ignore
        }
    }
}
// === TELOS_PENDING_REVIEW_END: desktop_taskbar_running_apps ===
