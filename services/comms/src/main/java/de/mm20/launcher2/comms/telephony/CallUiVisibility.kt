package de.mm20.launcher2.comms.telephony

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Owner tokens contain no Activity reference; overlapping/rotating hosts cannot clear each other. */
object CallUiVisibility {
    private val owners = mutableSetOf<Any>()
    private val _visible = MutableStateFlow(false)
    val visible = _visible.asStateFlow()

    @Synchronized fun setVisible(owner: Any, visible: Boolean) {
        if (visible) owners.add(owner) else owners.remove(owner)
        _visible.value = owners.isNotEmpty()
    }
}

internal fun showCallNotification(hasCall: Boolean, fullScreenVisible: Boolean): Boolean =
    hasCall && !fullScreenVisible
