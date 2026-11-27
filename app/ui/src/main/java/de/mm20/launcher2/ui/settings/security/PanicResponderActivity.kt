package de.mm20.launcher2.ui.settings.security

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import de.mm20.launcher2.applock.AppLockManager
import de.mm20.launcher2.applock.TelosSecretVault
import de.mm20.launcher2.i18n.R
import de.mm20.launcher2.ui.base.BaseActivity
import de.mm20.launcher2.ui.files.vault.VaultSessions
import org.koin.android.ext.android.inject

internal fun lockPrivateAccess(appLock: AppLockManager) {
    TelosSecretVault.lockVault()
    VaultSessions.lockAll()
    appLock.relockAll()
}

internal fun panicPreferences(context: Context) =
    context.getSharedPreferences("panic_responder", Context.MODE_PRIVATE)

internal const val PANIC_CONNECTED = "ripple_connected"

/** Ripple identifies itself through Android's activity caller identity. */
class PanicResponderActivity : BaseActivity() {
    private val appLock: AppLockManager by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (callingPackage != "info.guardianproject.ripple") {
            finish()
            return
        }
        val prefs = panicPreferences(this)
        when (intent.action) {
            "info.guardianproject.panic.action.CONNECT" -> {
                AlertDialog.Builder(this)
                    .setTitle(R.string.security_hub_ripple_connect)
                    .setMessage(R.string.security_hub_ripple_connect_info)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        prefs.edit().putBoolean(PANIC_CONNECTED, true).apply()
                        setResult(RESULT_OK)
                        finish()
                    }
                    .setNegativeButton(android.R.string.cancel) { _, _ -> finish() }
                    .setOnCancelListener { finish() }
                    .show()
            }
            "info.guardianproject.panic.action.DISCONNECT" -> {
                prefs.edit().remove(PANIC_CONNECTED).apply()
                finish()
            }
            "info.guardianproject.panic.action.TRIGGER" -> {
                if (prefs.getBoolean(PANIC_CONNECTED, false)) lockPrivateAccess(appLock)
                finish()
            }
            else -> finish()
        }
    }
}
