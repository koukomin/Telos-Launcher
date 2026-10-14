package de.mm20.launcher2.ui.trash

import android.app.NotificationManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import de.mm20.launcher2.ui.theme.LauncherTheme

/** Notification delete actions ask the same trash/permanent question as the app screens. */
class FileDeleteActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent.getStringExtra(EXTRA_URI)?.let(Uri::parse) ?: return finish()
        setContent {
            LauncherTheme {
                UriDeleteDialog(uri, onDeleted = {
                    if (intent.hasExtra(EXTRA_NOTIFICATION_ID)) getSystemService(NotificationManager::class.java).cancel(intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0))
                    finish()
                }, onDismiss = { finish() })
            }
        }
    }
    companion object { const val EXTRA_URI = "uri"; const val EXTRA_NOTIFICATION_ID = "notification_id" }
}
