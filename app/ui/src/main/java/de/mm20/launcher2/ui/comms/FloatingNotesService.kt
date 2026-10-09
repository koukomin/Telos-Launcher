package de.mm20.launcher2.ui.comms

import de.mm20.launcher2.ui.R
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import de.mm20.launcher2.preferences.comms.CommsSettings
import org.koin.android.ext.android.inject

class FloatingNotesService : Service() {
    private val commsSettings: CommsSettings by inject()
    private var windowManager: WindowManager? = null
    private var view: LinearLayout? = null
    private var number: String = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        number = intent?.getStringExtra("number").orEmpty()
        if (view != null) return START_NOT_STICKY
        val wm = getSystemService(WindowManager::class.java)
        windowManager = wm
        val input = EditText(this).apply {
            hint = getString(R.string.hc_note)
            minWidth = 400
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(0xEE222222.toInt())
            addView(TextView(this@FloatingNotesService).apply {
                text = getString(R.string.hc_call_note)
                setTextColor(0xFFFFFFFF.toInt())
            })
            addView(input)
            addView(Button(this@FloatingNotesService).apply {
                text = getString(R.string.hc_save)
                setOnClickListener {
                    if (number.isNotBlank()) commsSettings.setCallerNote(number, input.text.toString())
                    stopSelf()
                }
            })
        }
        view = root
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            y = 120
        }
        // without the overlay permission the view cannot be shown: do not keep an invisible service alive
        if (runCatching { wm.addView(root, params) }.isFailure) { view = null; stopSelf(); return START_NOT_STICKY }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        view?.let { v -> runCatching { windowManager?.removeView(v) } }
        view = null
        super.onDestroy()
    }
}
