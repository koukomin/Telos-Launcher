package de.mm20.launcher2.comms.sms

import android.content.Context

/**
 * Text for notifications and stored messages. The strings live in the app's resources (core:i18n),
 * which this module does not depend on, so they are looked up by name; [fallback] is used when a
 * name is not found.
 */
internal object SmsText {
    fun get(context: Context, name: String, fallback: String): String = runCatching {
        val id = context.resources.getIdentifier(name, "string", context.packageName)
        if (id != 0) context.getString(id) else fallback
    }.getOrDefault(fallback)
}
