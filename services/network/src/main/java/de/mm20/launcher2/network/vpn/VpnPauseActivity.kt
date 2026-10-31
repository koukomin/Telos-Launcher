package de.mm20.launcher2.network.vpn
import de.mm20.launcher2.i18n.R as I18nR

import android.app.Activity
import android.app.AlertDialog
import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import de.mm20.launcher2.network.NetworkEngine
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

/** Direct notification activity PendingIntent, avoiding Android's notification-trampoline ban. */
class VpnPauseActivity : Activity(), KoinComponent {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.action == ACTION_RESUME) {
            resumeVpn()
            return
        }
        val labels = listOf(getString(I18nR.string.dl_t_minutes, 5), getString(I18nR.string.dl_t_minutes, 30),
            getString(I18nR.string.dl_t_hours, 1), getString(I18nR.string.dl_t_hours, 2))
        val content = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            addView(android.widget.TextView(context).apply { setText(I18nR.string.net_pause_explanation) })
        }
        val builder = AlertDialog.Builder(this)
            .setTitle(getString(I18nR.string.au_radio_pause) + " VPN")
            .setView(android.widget.ScrollView(this).apply { addView(content) })
            .setNegativeButton(android.R.string.cancel) { _, _ -> finish() }
            .setOnCancelListener { finish() }
        if (Build.VERSION.SDK_INT >= 31 && !getSystemService(AlarmManager::class.java).canScheduleExactAlarms()) {
            builder.setNeutralButton(I18nR.string.hc_settings) { _, _ ->
                runCatching { startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))) }
                finish()
            }
        }
        val dialog = builder.create()
        labels.forEachIndexed { index, label ->
            content.addView(android.widget.Button(this).apply {
                text = label
                setOnClickListener {
                    get<NetworkEngine>().pause(VpnPausePolicy.durationsMs[index])
                    dialog.dismiss()
                    finish()
                }
            })
        }
        content.addView(android.widget.Button(this).apply {
            setText(I18nR.string.net_notif_stop)
            setOnClickListener { get<NetworkEngine>().stop(); dialog.dismiss(); finish() }
        })
        dialog.show()
    }
    private fun resumeVpn() {
        val engine = get<NetworkEngine>()
        engine.start()
        val state = engine.state.value
        if (state is de.mm20.launcher2.network.NetState.NeedsPermission) {
            runCatching { startActivityForResult(state.intent, 7106) }.onFailure { finish() }
        } else finish()
    }

    @Deprecated("Legacy Activity permission result")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 7106) {
            if (resultCode == RESULT_OK) get<NetworkEngine>().start() else get<NetworkEngine>().acknowledgeError()
            finish()
        }
    }

    companion object { const val ACTION_RESUME = "de.mm20.launcher2.network.NOTIFICATION_RESUME" }
}
