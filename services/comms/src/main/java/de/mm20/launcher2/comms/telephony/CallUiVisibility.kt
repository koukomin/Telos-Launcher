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


internal enum class CallNotificationKind { None, IncomingFullScreen, IncomingActions, OngoingActions }

/** This bound InCallService is not a foreground service. Never post an unqualified CallStyle. */
internal fun callNotificationKind(state: InCallUiState, visible: Boolean, alertIncoming: Boolean): CallNotificationKind = when {
    !state.hasCall || visible -> CallNotificationKind.None
    state.incoming && alertIncoming -> CallNotificationKind.IncomingFullScreen
    state.incoming -> CallNotificationKind.IncomingActions
    else -> CallNotificationKind.OngoingActions
}

/** An async identity/settings emission cannot resurrect an ended or replaced call notification. */
internal fun currentNotificationState(requested: InCallUiState, live: InCallUiState): Boolean =
    live.hasCall && requested.hasCall && requested.actionToken == live.actionToken &&
        requested.incoming == live.incoming && requested.active == live.active &&
        requested.connecting == live.connecting && requested.onHold == live.onHold

internal fun clearOrphanCallNotification(telecomHasCall: Boolean?, sessionHasCall: Boolean): Boolean =
    telecomHasCall == false && !sessionHasCall
