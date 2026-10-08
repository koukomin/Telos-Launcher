package de.mm20.launcher2.network

import android.content.Intent

/** What Telos Network is doing right now. Observe it with [NetworkEngine.state]. */
sealed interface NetState {
    /** The VPN is off. This is the state after every start of the app unless "start on boot" is on. */
    data object Off : NetState

    /** The VPN is being set up. */
    data object Starting : NetState

    /** The VPN is running. */
    data object On : NetState

    /**
     * The VPN stopped because something failed (or could not start). The internet works normally
     * again. [message] is already translated and can be shown to the user. Leaves this state with
     * [NetworkEngine.start] or [NetworkEngine.acknowledgeError].
     */
    data class Error(val message: String) : NetState

    /**
     * Android needs the user's consent to set up a VPN. Start [intent] with
     * `startActivityForResult` / an `ActivityResultContract.StartActivityForResult`; when the
     * result is `RESULT_OK`, call [NetworkEngine.start] again. If the user declines, call
     * [NetworkEngine.acknowledgeError] to return to [Off].
     */
    data class NeedsPermission(val intent: Intent) : NetState
}
