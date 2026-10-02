package de.mm20.launcher2.comms.intent

import android.content.Intent
import android.net.Uri

/**
 * Builds [Intent]s that jump straight to a chat with a given phone number in a specific
 * third-party messaging app, via each app's own documented deep-link scheme. Callers are
 * responsible for `startActivity`/catching [android.content.ActivityNotFoundException] - these
 * functions only build the `Intent`, matching every other call site in this codebase that
 * constructs an `Intent` without needing a `Context`.
 */
object SocialIntentHelper {

    /**
     * WhatsApp's official "click to chat" link. `phone` must be the full number in international
     * format with country code, digits only (no `+`, spaces or punctuation) - this is WhatsApp's
     * documented requirement for the `phone` query parameter.
     */
    fun whatsApp(phoneNumber: String): Intent {
        val digits = digitsOnly(phoneNumber)
        return Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=$digits"))
    }

    /**
     * Telegram's phone-number deep link (`https://t.me/+<phone>`), which opens a chat with
     * whichever Telegram account that number belongs to - not `tg://resolve?domain=`, which
     * resolves a `@username`, not a phone number, so it doesn't fit this use case.
     */
    fun telegram(phoneNumber: String): Intent {
        val digits = digitsOnly(phoneNumber)
        return Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/+$digits"))
    }

    /**
     * Signal's official `signal.me` deep link. Unlike WhatsApp/Telegram, Signal's documented
     * format keeps the leading `+` (international format, e.g. `+15551234567`). Explicitly
     * targets Signal's package so this never silently falls through to a browser if Signal isn't
     * installed - better to fail with "app not found" than to open signal.me as a web page that
     * can't actually do anything.
     */
    fun signal(phoneNumber: String): Intent {
        val withPlus = internationalWithPlus(phoneNumber)
        return Intent(Intent.ACTION_VIEW, Uri.parse("sgnl://signal.me/#p/$withPlus")).apply {
            setPackage(SIGNAL_PACKAGE)
        }
    }

    /**
     * Viber's `viber://chat` deep link, which opens a 1:1 chat with the given number if it's on
     * Viber.
     */
    fun viber(phoneNumber: String): Intent {
        val digits = digitsOnly(phoneNumber)
        return Intent(Intent.ACTION_VIEW, Uri.parse("viber://chat?number=$digits"))
    }

    private fun digitsOnly(phoneNumber: String): String = phoneNumber.filter { it.isDigit() }

    private fun internationalWithPlus(phoneNumber: String): String = "+${digitsOnly(phoneNumber)}"

    private const val SIGNAL_PACKAGE = "org.thoughtcrime.securesms"
}
