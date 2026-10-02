package de.mm20.launcher2.ui.comms

import androidx.compose.runtime.Composable

/**
 * SMS/MMS thread data isn't wired up yet - reading `Telephony.Sms`/`Telephony.Mms` needs a new
 * `READ_SMS` permission group plus a `ContentObserver`-backed repository, which is Phase 2 scope
 * (see `MessageRepository`'s doc comment). The screen itself is real, navigable UI.
 */
@Composable
fun MessagesScreen() {
    EmptyCommsTab(
        title = "No conversations",
        message = "Messages aren't connected yet - coming in a future update.",
    )
}
