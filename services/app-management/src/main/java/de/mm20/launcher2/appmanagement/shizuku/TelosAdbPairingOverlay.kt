package de.mm20.launcher2.appmanagement.shizuku

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import de.mm20.launcher2.i18n.R as I18nR
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Floating code field over the system Wireless debugging pairing dialog.
 * Settings stays resumed so the 6-digit code is not cancelled.
 */
internal class TelosAdbPairingOverlay(
    private val context: Context,
    private val windowType: Int,
) {
    private val app = context.applicationContext
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var root: View? = null
    private var input: EditText? = null
    private var host: String? = null
    private var port: Int? = null

    fun isShowing(): Boolean = root != null

    fun show(prefill: String, host: String?, port: Int?) {
        this.host = host
        this.port = port
        if (!scope.isActive) {
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        }
        val existing = input
        if (existing != null) {
            val digits = prefill.filter { it.isDigit() }.take(6)
            if (digits.isNotEmpty() && existing.text.toString() != digits) {
                existing.setText(digits)
                existing.setSelection(digits.length)
            }
            return
        }
        val density = context.resources.displayMetrics.density
        val pad = (16 * density).toInt()
        val field = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            filters = arrayOf(InputFilter.LengthFilter(6))
            hint = context.getString(I18nR.string.telos_shizuku_pair_code)
            imeOptions = EditorInfo.IME_ACTION_DONE
            val digits = prefill.filter { it.isDigit() }.take(6)
            setText(digits)
            setSelection(digits.length)
        }
        input = field
        val pair = Button(context).apply {
            text = context.getString(I18nR.string.telos_shizuku_pair_submit)
            setOnClickListener { submit() }
        }
        field.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_DONE) {
                submit()
                true
            } else false
        }
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(0xF2222222.toInt())
            addView(
                TextView(context).apply {
                    text = context.getString(I18nR.string.telos_shizuku_pair_prompt_body)
                    setTextColor(0xFFFFFFFF.toInt())
                },
            )
            addView(field)
            addView(pair)
        }
        val flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            flags,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM
            title = "telos-adb-pairing"
        }
        runCatching {
            wm.addView(box, lp)
            root = box
        }.onFailure {
            root = null
            input = null
        }
    }

    fun dismiss() {
        val view = root
        root = null
        input = null
        if (view != null) runCatching { wm.removeView(view) }
        scope.cancel()
    }

    private fun submit() {
        val code = input?.text?.toString().orEmpty().filter { it.isDigit() }.take(6)
        if (code.length != 6) {
            input?.error = context.getString(I18nR.string.telos_shizuku_pair_code)
            return
        }
        scope.launch {
            val r = TelosShizukuHost.submitPairingCode(app, code, host, port)
            Toast.makeText(
                app,
                if (r.isSuccess) app.getString(I18nR.string.telos_shizuku_pair_ok)
                else r.exceptionOrNull()?.message ?: app.getString(I18nR.string.telos_shizuku_pair_fail),
                Toast.LENGTH_LONG,
            ).show()
            if (r.isSuccess) dismiss()
        }
    }

    companion object {
        fun overlayTypeFor(context: Context, accessibility: Boolean): Int? {
            if (accessibility) {
                return if (Build.VERSION.SDK_INT >= 22) {
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                } else {
                    WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
                }
            }
            if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(context)) return null
            return if (Build.VERSION.SDK_INT >= 26) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
            }
        }
    }
}
