package de.mm20.launcher2.ui.launcher.widgets

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.mm20.launcher2.preferences.ui.UiSettings
import de.mm20.launcher2.widgets.Widget
import de.mm20.launcher2.widgets.WidgetRepository
import de.mm20.launcher2.widgets.withStackId
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.UUID

class WidgetsVM(
    private val parentId: UUID?,
) : ViewModel(), KoinComponent {
    private val widgetRepository: WidgetRepository by inject()

    private val uiSettings: UiSettings by inject()

    val editButton = uiSettings.widgetEditButton
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    val widgets = widgetRepository.get(parent = parentId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    /**
     * [widgets] grouped into home-screen slots: widgets sharing a non-null
     * [Widget.stackId] occupy one slot together (a "widget stack"), everything
     * else is its own solo slot. Order-preserving.
     */
    val slots = widgets
        .map { list -> list.groupBy { it.stackId ?: it.id }.values.toList() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    fun addWidget(widget: Widget, index: Int? = null) {
        val widgets = widgets.value.toMutableList()
        if (index == null) {
            widgets.add(widget)
        } else {
            widgets.add(index.coerceAtMost(widgets.size), widget)
        }
        widgetRepository.set(widgets, parentId)
    }

    fun removeWidget(widget: Widget) {
        widgetRepository.delete(widget)
    }

    fun updateWidget(widget: Widget) {
        widgetRepository.update(widget)
    }

    fun moveUp(index: Int) {
        val widgets = widgets.value.toMutableList()
        val widget = widgets.removeAt(index)
        widgets.add(index - 1, widget)
        widgetRepository.set(widgets, parentId)
    }

    fun moveDown(index: Int) {
        val widgets = widgets.value.toMutableList()
        val widget = widgets.removeAt(index)
        widgets.add(index + 1, widget)
        widgetRepository.set(widgets, parentId)
    }

    fun moveSlotUp(slotIndex: Int) {
        val slots = slots.value.toMutableList()
        val slot = slots.removeAt(slotIndex)
        slots.add(slotIndex - 1, slot)
        widgetRepository.set(slots.flatten(), parentId)
    }

    fun moveSlotDown(slotIndex: Int) {
        val slots = slots.value.toMutableList()
        val slot = slots.removeAt(slotIndex)
        slots.add(slotIndex + 1, slot)
        widgetRepository.set(slots.flatten(), parentId)
    }

    /**
     * Adds [newWidget] to [target]'s stack, creating a new stack out of the
     * two of them if [target] wasn't already stacked.
     */
    fun addToStack(target: Widget, newWidget: Widget) {
        val stackId = target.stackId ?: UUID.randomUUID()
        val widgets = widgets.value.toMutableList()
        val targetIndex = widgets.indexOfFirst { it.id == target.id }
        if (targetIndex == -1) return
        if (target.stackId == null) {
            widgets[targetIndex] = target.withStackId(stackId)
        }
        widgets.add(targetIndex + 1, newWidget.withStackId(stackId))
        widgetRepository.set(widgets, parentId)
    }

    /**
     * Removes [widget] from its stack. If that leaves only one widget behind
     * in the stack, that widget is un-stacked too, so a stack never lingers
     * at size 1.
     */
    fun removeFromStack(widget: Widget) {
        val stackId = widget.stackId ?: return
        val widgets = widgets.value.toMutableList()
        val index = widgets.indexOfFirst { it.id == widget.id }
        if (index == -1) return
        widgets[index] = widgets[index].withStackId(null)

        val remainingIndex = widgets
            .withIndex()
            .filter { it.value.id != widget.id && it.value.stackId == stackId }
            .map { it.index }
        if (remainingIndex.size == 1) {
            val i = remainingIndex[0]
            widgets[i] = widgets[i].withStackId(null)
        }
        widgetRepository.set(widgets, parentId)
    }

    companion object : KoinComponent {
        fun Factory(parentId: String) = viewModelFactory {
            initializer {
                val id = try {
                    UUID.fromString(parentId)
                } catch (e: IllegalArgumentException) {
                    Log.e("WidgetsVM", "Invalid parentId: $parentId", e)
                    null
                }
                WidgetsVM(id)
            }
        }
    }
}
