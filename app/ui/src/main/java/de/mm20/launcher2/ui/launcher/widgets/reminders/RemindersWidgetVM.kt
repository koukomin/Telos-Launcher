package de.mm20.launcher2.ui.launcher.widgets.reminders

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.calendar.CalendarRepository
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.search.CalendarEvent
import de.mm20.launcher2.searchable.SavableSearchableRepository
import de.mm20.launcher2.searchable.VisibilityLevel
import de.mm20.launcher2.widgets.RemindersWidget
import de.mm20.launcher2.widgets.RemindersWidgetConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class RemindersWidgetVM : ViewModel(), KoinComponent {

    private val calendarRepository: CalendarRepository by inject()
    private val searchableRepository: SavableSearchableRepository by inject()
    private val permissionsManager: PermissionsManager by inject()

    private val widgetConfig = MutableStateFlow(RemindersWidgetConfig())

    val hasPermission = permissionsManager.hasPermission(PermissionGroup.Tasks)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    val tasks = mutableStateOf<List<CalendarEvent>>(emptyList())

    fun updateWidget(widget: RemindersWidget) {
        widgetConfig.value = widget.config
    }

    suspend fun onActive() {
        widgetConfig.collectLatest { config ->
            val now = System.currentTimeMillis()
            calendarRepository.findMany(
                from = now - 30L * 24 * 60 * 60 * 1000L,
                to = now + 365L * 24 * 60 * 60 * 1000L,
            ).collectLatest { events ->
                searchableRepository.getKeys(
                    includeTypes = listOf("tasks.org", "plugin.calendar"),
                    maxVisibility = VisibilityLevel.SearchOnly,
                    limit = 9999,
                ).collectLatest { hidden ->
                    tasks.value = events
                        .filter {
                            it.isTask &&
                                !hidden.contains(it.key) &&
                                (config.showCompleted || it.isCompleted != true)
                        }
                        .sortedBy { it.endTime }
                        .take(config.maxItems)
                }
            }
        }
    }

    fun requestTasksPermission(context: AppCompatActivity) {
        permissionsManager.requestPermission(context, PermissionGroup.Tasks)
    }
}
