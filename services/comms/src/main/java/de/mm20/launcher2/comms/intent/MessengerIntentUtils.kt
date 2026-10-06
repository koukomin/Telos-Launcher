package de.mm20.launcher2.comms.intent

import android.content.Intent
import android.net.Uri

object MessengerIntentUtils {

    fun whatsApp(phoneNumber: String): Intent {
        val digits = digitsOnly(phoneNumber)
        return Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=$digits"))
    }

    fun telegram(phoneNumber: String): Intent {
        val digits = digitsOnly(phoneNumber)
        return Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/+$digits"))
    }

    fun signal(phoneNumber: String): Intent {
        val withPlus = internationalWithPlus(phoneNumber)
        return Intent(Intent.ACTION_VIEW, Uri.parse("sgnl://signal.me/#p/$withPlus")).apply {
            setPackage("org.thoughtcrime.securesms")
        }
    }

    fun viber(phoneNumber: String): Intent {
        val digits = digitsOnly(phoneNumber)
        return Intent(Intent.ACTION_VIEW, Uri.parse("viber://chat?number=$digits"))
    }

    fun sms(phoneNumber: String, body: String? = null): Intent {
        return Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phoneNumber")).apply {
            if (!body.isNullOrBlank()) putExtra("sms_body", body)
        }
    }

    fun email(address: String): Intent {
        return Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${Uri.encode(address)}"))
    }

    private fun digitsOnly(phoneNumber: String): String = phoneNumber.filter { it.isDigit() }

    private fun internationalWithPlus(phoneNumber: String): String = "+${digitsOnly(phoneNumber)}"
}
