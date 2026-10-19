package de.mm20.launcher2.comms.ermis

import android.accounts.Account
import android.accounts.AccountManager
import android.app.Activity
import android.content.Context
import java.util.concurrent.TimeUnit
import javax.mail.AuthenticationFailedException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible

/** Optional Android authenticator integration. No bundled OAuth client identity is assumed. */
object ErmisGoogleAuth {
    private const val tokenType = "oauth2:https://mail.google.com/"
    suspend fun consent(activity: Activity, email: String): String = runInterruptible(Dispatchers.IO) {
        val future = AccountManager.get(activity).getAuthToken(Account(email, "com.google"), tokenType, null, activity, null, null)
        try {
            future.getResult(60, TimeUnit.SECONDS).getString(AccountManager.KEY_AUTHTOKEN)
                ?.takeIf { it.isNotBlank() } ?: throw AuthenticationFailedException()
        } finally { if (!future.isDone) future.cancel(true) }
    }
    internal fun invalidate(context: Context, token: String) {
        AccountManager.get(context).invalidateAuthToken("com.google", token)
    }
    internal fun token(context: Context, email: String): String {
        val future = AccountManager.get(context).getAuthToken(Account(email, "com.google"), tokenType, null, false, null, null)
        return try {
            future.getResult(30, TimeUnit.SECONDS).getString(AccountManager.KEY_AUTHTOKEN)
                ?.takeIf { it.isNotBlank() } ?: throw AuthenticationFailedException()
        } finally { if (!future.isDone) future.cancel(true) }
    }
}
