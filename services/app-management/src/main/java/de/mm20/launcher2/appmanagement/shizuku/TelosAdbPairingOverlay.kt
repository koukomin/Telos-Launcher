package de.mm20.launcher2.appmanagement.shizuku

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.inputmethod.InputMethodManager
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.ImageButton
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
    private var pairButton: Button? = null
    private var submitting = false
    private var params: WindowManager.LayoutParams? = null

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
        val field = object : EditText(context) {
            override fun onKeyPreIme(keyCode: Int, event: KeyEvent): Boolean {
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    collapse()
                    return true
                }
                return super.onKeyPreIme(keyCode, event)
            }
        }.apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
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
        pairButton = pair
        field.setOnEditorActionListener { _, action, event ->
            if (action == EditorInfo.IME_ACTION_DONE ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP)) {
                submit()
                true
            } else false
        }
        val pill = Button(context).apply {
            text = context.getString(I18nR.string.telos_shizuku_pair_code)
            setOnClickListener {
                field.visibility = View.VISIBLE
                pair.visibility = View.VISIBLE
                params?.let { it.flags = it.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv() }
                root?.let { wm.updateViewLayout(it, params) }
                field.requestFocus()
                showKeyboardWhenReady(field)
            }
        }
        field.visibility = View.GONE
        pair.visibility = View.GONE
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            val backgroundColor = android.util.TypedValue().also {
                context.theme.resolveAttribute(android.R.attr.colorBackground, it, true)
            }.data
            background = GradientDrawable().apply { setColor(backgroundColor); cornerRadius = 28 * density }
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(pill, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(ImageButton(context).apply {
                    setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                    contentDescription = context.getString(I18nR.string.telos_shizuku_pair_stop)
                    minimumWidth = (48 * density).toInt()
                    minimumHeight = (48 * density).toInt()
                    setOnClickListener {
                        context.startService(TelosAdbPairingService.stopIntent(context))
                        dismiss()
                    }
                })
            })
            addView(field)
            addView(pair)
        }
        val flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        val lp = WindowManager.LayoutParams(
            (320 * density).toInt().coerceAtMost(context.resources.displayMetrics.widthPixels - 2 * pad),
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            flags,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = (72 * density).toInt()
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            title = "telos-adb-pairing"
        }
        params = lp
        runCatching {
            wm.addView(box, lp)
            root = box
        }.onFailure {
            root = null
            input = null
        }
    }

    fun dismiss() {
        hideKeyboard()
        val view = root
        root = null
        input = null
        if (view != null) runCatching { wm.removeView(view) }
        scope.cancel()
    }

    private fun showKeyboardWhenReady(field: EditText, attempts: Int = 10) {
        // Updating NOT_FOCUSABLE does not transfer window focus synchronously.
        field.postDelayed({
            if (input !== field || field.visibility != View.VISIBLE) return@postDelayed
            if (field.hasWindowFocus()) {
                context.getSystemService(InputMethodManager::class.java)?.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT)
            } else if (attempts > 0) showKeyboardWhenReady(field, attempts - 1)
        }, 100)
    }

    private fun hideKeyboard() {
        input?.let { field ->
            context.getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(field.windowToken, 0)
            field.clearFocus()
        }
    }

    private fun collapse() {
        hideKeyboard()
        input?.visibility = View.GONE
        pairButton?.visibility = View.GONE
        root?.let { view ->
            params?.let { lp ->
                lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                wm.updateViewLayout(view, lp)
            }
        }
    }

    private fun submit() {
        if (submitting) return
        val code = input?.text?.toString().orEmpty().filter { it.isDigit() }.take(6)
        if (code.length != 6) {
            input?.error = context.getString(I18nR.string.telos_shizuku_pair_code)
            return
        }
        submitting = true
        collapse()
        scope.launch {
            val r = TelosShizukuHost.submitPairingCode(app, code, host, port)
            Toast.makeText(
                app,
                if (r.isSuccess) app.getString(I18nR.string.telos_shizuku_pair_ok)
                else r.exceptionOrNull()?.message ?: app.getString(I18nR.string.telos_shizuku_pair_fail),
                Toast.LENGTH_LONG,
            ).show()
            submitting = false
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
