package de.mm20.launcher2.appmanagement.shizuku

import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Bundle
import android.app.Activity
import android.app.AlertDialog
import de.mm20.launcher2.i18n.R as I18nR
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuApiConstants

/**
 * Confirmation dialog the Shizuku server launches when a client calls
 * [Shizuku.requestPermission]. Without this activity the grant never returns.
 */
class TelosShizukuRequestPermissionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uid = intent.getIntExtra("uid", -1)
        val pid = intent.getIntExtra("pid", -1)
        val requestCode = intent.getIntExtra("requestCode", -1)
        val ai = applicationInfoExtra()
        if (uid < 0 || requestCode < 0 || ai == null) {
            finish()
            return
        }
        val label = runCatching { ai.loadLabel(packageManager).toString() }.getOrDefault(ai.packageName)
        AlertDialog.Builder(this)
            .setTitle(I18nR.string.telos_shizuku_perm_title)
            .setMessage(getString(I18nR.string.telos_shizuku_perm_body, label))
            .setPositiveButton(I18nR.string.security_hub_allow) { _, _ ->
                reply(uid, pid, requestCode, allowed = true, onetime = false)
            }
            .setNeutralButton(I18nR.string.telos_shizuku_perm_once) { _, _ ->
                reply(uid, pid, requestCode, allowed = true, onetime = true)
            }
            .setNegativeButton(I18nR.string.security_hub_deny) { _, _ ->
                reply(uid, pid, requestCode, allowed = false, onetime = false)
            }
            .setOnCancelListener {
                reply(uid, pid, requestCode, allowed = false, onetime = true)
            }
            .show()
    }

    private fun applicationInfoExtra(): ApplicationInfo? {
        return if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra("applicationInfo", ApplicationInfo::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("applicationInfo")
        }
    }

    @Suppress("RestrictedApi")
    private fun reply(uid: Int, pid: Int, requestCode: Int, allowed: Boolean, onetime: Boolean) {
        val data = Bundle().apply {
            putBoolean(ShizukuApiConstants.REQUEST_PERMISSION_REPLY_ALLOWED, allowed)
            putBoolean(ShizukuApiConstants.REQUEST_PERMISSION_REPLY_IS_ONETIME, onetime)
        }
        runCatching {
            Shizuku.dispatchPermissionConfirmationResult(uid, pid, requestCode, data)
        }
        finish()
    }
}
