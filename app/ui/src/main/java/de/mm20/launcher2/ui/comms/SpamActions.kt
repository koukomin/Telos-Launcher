package de.mm20.launcher2.ui.comms

import android.content.Context
import android.widget.Toast
import de.mm20.launcher2.comms.repository.SpamRepository
import de.mm20.launcher2.comms.repository.SystemBlockUpdateException
import de.mm20.launcher2.ui.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A partial system write is surfaced even though the personal list has already been updated. */
internal suspend fun updateBlockedWithFeedback(context: Context, spam: SpamRepository, number: String, blocked: Boolean): Boolean {
    return try {
        spam.setBlocked(number, blocked)
        true
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (error: Exception) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, if (error is SystemBlockUpdateException) R.string.caller_block_partial else R.string.hc_failed_try_again,
                Toast.LENGTH_LONG).show()
        }
        false
    }
}
