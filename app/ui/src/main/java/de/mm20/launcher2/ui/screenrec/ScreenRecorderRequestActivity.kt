package de.mm20.launcher2.ui.screenrec

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Asks Android for the permission to capture the screen and hands the answer to
 * [ScreenRecorderService]. It has no screen of its own, so that the recording starts from the app
 * that is in front (the sidebar starts it over whatever you are using).
 */
class ScreenRecorderRequestActivity : ComponentActivity() {

    private val request = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            ScreenRecorderService.start(this, result.resultCode, data)
        }
        finish()
    }

    // The notification permission only decides whether the recording notification is shown,
    // so the recording starts whatever the answer is.
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        launchCapture()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            launchCapture()
        }
    }

    private fun launchCapture() {
        val manager = getSystemService(MediaProjectionManager::class.java)
        request.launch(manager.createScreenCaptureIntent())
    }

    companion object {
        /** Starts a recording: asks for the capture permission first */
        fun launch(context: Context) {
            context.startActivity(
                Intent(context, ScreenRecorderRequestActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            )
        }
    }
}
