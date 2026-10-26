package de.mm20.launcher2.ui.comms

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import de.mm20.launcher2.comms.telephony.InCallUiState

@Preview(widthDp = 360, heightDp = 800, name = "Incoming")
@Preview(widthDp = 720, heightDp = 420, fontScale = 2f, name = "Incoming landscape large text")
@Composable
private fun IncomingCallPreview() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        CallScreenContent(InCallUiState(hasCall = true, incoming = true), "Δοκιμή επαφής", "Εισερχόμενη κλήση", {},
            incomingActions = { IncomingCallActions("", "buttons", onAnswer = {}, onReject = {}) })
    }
}

@Preview(widthDp = 360, heightDp = 800, name = "Active")
@Composable
private fun ActiveCallPreview() {
    MaterialTheme(colorScheme = lightColorScheme()) {
        CallScreenContent(InCallUiState(hasCall = true, active = true), "Δοκιμή επαφής", "00:24", {},
            activeControls = { Text("Κλήση σε εξέλιξη") })
    }
}
