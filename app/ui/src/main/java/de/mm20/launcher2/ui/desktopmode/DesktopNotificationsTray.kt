package de.mm20.launcher2.ui.desktopmode

import android.app.PendingIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.ktx.sendWithBackgroundPermission
import de.mm20.launcher2.notifications.Notification
import de.mm20.launcher2.notifications.NotificationRepository
import de.mm20.launcher2.ui.R
import org.koin.compose.koinInject

@Composable
internal fun DesktopNotificationsTrayIcon() {
    val repository = koinInject<NotificationRepository>()
    val notifications by repository.notifications.collectAsState(emptyList())
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Box {
        DesktopTrayIconButton(
            icon = R.drawable.notifications_24px,
            contentDescription = stringResource(R.string.desktop_mode_tray_notifications),
            caption = if (notifications.isNotEmpty()) notifications.size.toString() else null,
            active = expanded,
            onClick = { expanded = !expanded },
        )
    }

    if (expanded) {
        Popup(
            onDismissRequest = { expanded = false },
            properties = PopupProperties(focusable = true),
        ) {
            Surface(
                modifier = Modifier
                    .padding(bottom = DesktopTaskbarHeight + 4.dp)
                    .width(320.dp),
                shape = MaterialTheme.shapes.medium,
                tonalElevation = 4.dp,
                shadowElevation = 4.dp,
            ) {
                if (notifications.isEmpty()) {
                    Text(
                        text = stringResource(R.string.desktop_mode_tray_notifications_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                } else {
                    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                        items(notifications, key = { it.key }) { notification ->
                            DesktopNotificationRow(
                                notification = notification,
                                onClick = {
                                    try {
                                        notification.contentIntent?.sendWithBackgroundPermission(context)
                                    } catch (e: PendingIntent.CanceledException) {
                                        CrashReporter.logException(e)
                                    }
                                    expanded = false
                                },
                                onClear = { repository.cancelNotification(notification) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DesktopNotificationRow(
    notification: Notification,
    onClick: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(
                    if (notification.color != 0) androidx.compose.ui.graphics.Color(notification.color)
                    else MaterialTheme.colorScheme.secondaryContainer
                )
                .width(8.dp)
        ) {}
        Column(modifier = Modifier.weight(1f)) {
            if (notification.title != null) {
                Text(
                    text = notification.title!!,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (notification.text != null) {
                Text(
                    text = notification.text!!,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (notification.isClearable) {
            Text(
                text = stringResource(R.string.close),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.clickable(onClick = onClear),
            )
        }
    }
}
