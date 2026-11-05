package de.mm20.launcher2.appmanagement.shizuku

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.app.AlertDialog
import de.mm20.launcher2.i18n.R as I18nR
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Heads-up dialog over Settings when the wireless pairing code is on screen,
 * so the user can enter (or confirm) the 6 digits without hunting a notification.
 */
class TelosAdbPairingPromptActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
        )
        val prefill = intent.getStringExtra(EXTRA_CODE).orEmpty().filter { it.isDigit() }.take(6)
        val host = intent.getStringExtra(EXTRA_HOST)
        val port = intent.getIntExtra(EXTRA_PORT, -1).takeIf { it > 0 }
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            filters = arrayOf(InputFilter.LengthFilter(6))
            hint = getString(I18nR.string.telos_shizuku_pair_code)
            setText(prefill)
            setSelection(text.length)
        }
        val pad = (16 * resources.displayMetrics.density).toInt()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, 0)
            addView(
                TextView(this@TelosAdbPairingPromptActivity).apply {
                    text = getString(I18nR.string.telos_shizuku_pair_prompt_body)
                },
            )
            addView(input)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(I18nR.string.telos_shizuku_pair_notif_found))
            .setView(box)
            .setPositiveButton(getString(I18nR.string.telos_shizuku_pair_submit), null)
            .setNegativeButton(android.R.string.cancel) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val code = input.text.toString().filter { it.isDigit() }.take(6)
                if (code.length != 6) {
                    input.error = getString(I18nR.string.telos_shizuku_pair_code)
                    return@setOnClickListener
                }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                scope.launch {
                    val r = TelosShizukuHost.submitPairingCode(this@TelosAdbPairingPromptActivity, code, host, port)
                    Toast.makeText(
                        this@TelosAdbPairingPromptActivity,
                        if (r.isSuccess) getString(I18nR.string.telos_shizuku_pair_ok)
                        else r.exceptionOrNull()?.message ?: getString(I18nR.string.telos_shizuku_pair_fail),
                        Toast.LENGTH_LONG,
                    ).show()
                    finish()
                }
            }
        }
        dialog.show()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_CODE = "code"
        const val EXTRA_HOST = "host"
        const val EXTRA_PORT = "port"

        fun launch(context: Context, code: String, host: String?, port: Int?) {
            val i = Intent(context, TelosAdbPairingPromptActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_CODE, code)
                .putExtra(EXTRA_HOST, host)
                .putExtra(EXTRA_PORT, port ?: -1)
            runCatching { context.startActivity(i) }
        }
    }
}
