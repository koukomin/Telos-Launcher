package de.mm20.launcher2.ui.comms

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Call log data isn't wired up yet - reading `CallLog.Calls` needs a new `READ_CALL_LOG`
 * permission group plus a `ContentObserver`-backed repository, which is Phase 2 scope (see
 * `CallLogRepository`'s doc comment). The screen itself is real, navigable UI.
 */
@Composable
fun RecentsScreen() {
    EmptyCommsTab(
        title = "No recent calls",
        message = "Call history isn't connected yet - coming in a future update.",
    )
}

@Composable
internal fun EmptyCommsTab(title: String, message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1f))
    }
}
