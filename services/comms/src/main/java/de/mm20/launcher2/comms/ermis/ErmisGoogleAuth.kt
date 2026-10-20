package de.mm20.launcher2.comms.ermis

import android.accounts.Account
import android.accounts.AccountManager
import android.accounts.AccountManagerFuture
import android.accounts.AuthenticatorException
import android.os.Bundle
import android.app.Activity
import android.content.Context
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible

/** Optional Android authenticator integration. No bundled OAuth client identity is assumed. */
object ErmisGoogleAuth {
    private fun Bundle.mailToken(): String = getString(AccountManager.KEY_AUTHTOKEN)
        ?.takeIf { it.isNotBlank() } ?: throw ErmisGoogleAccessNotGrantedException()
    private const val tokenType = "oauth2:https://mail.google.com/"
    private fun request(context: Context, email: String, activity: Activity?): AccountManagerFuture<Bundle> = try {
        val manager = AccountManager.get(context)
        val account = Account(email, "com.google")
        if (activity != null) manager.getAuthToken(account, tokenType, null, activity, null, null)
        else manager.getAuthToken(account, tokenType, null, false, null, null)
    } catch (_: SecurityException) { throw ErmisGoogleAccessNotGrantedException() }

    suspend fun consent(activity: Activity, email: String): String = runInterruptible(Dispatchers.IO) {
        val future = request(activity, email, activity)
        try {
            future.getResult(60, TimeUnit.SECONDS).mailToken()
        } catch (_: AuthenticatorException) { throw ErmisGoogleAccessNotGrantedException()
        } catch (_: SecurityException) { throw ErmisGoogleAccessNotGrantedException()
        } finally { if (!future.isDone) future.cancel(true) }
    }
    internal fun invalidate(context: Context, token: String) {
        AccountManager.get(context).invalidateAuthToken("com.google", token)
    }
    internal fun token(context: Context, email: String): String {
        val future = request(context, email, null)
        return try {
            future.getResult(30, TimeUnit.SECONDS).mailToken()
        } catch (_: AuthenticatorException) { throw ErmisGoogleAccessNotGrantedException()
        } catch (_: SecurityException) { throw ErmisGoogleAccessNotGrantedException()
        } finally { if (!future.isDone) future.cancel(true) }
    }
}
