package de.mm20.launcher2.comms.intent

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import de.mm20.launcher2.ktx.tryStartActivity

enum class MessengerChannel(val id: String) {
    Phone("phone"),
    Sms("sms"),
    Signal("signal"),
    WhatsApp("whatsapp"),
    Telegram("telegram"),
    Viber("viber");

    companion object {
        fun fromId(id: String?): MessengerChannel = entries.find { it.id == id } ?: Phone
    }
}

object MessengerApps {

    val signalPkgs = listOf(
        "org.thoughtcrime.securesms",
        "im.molly.app",
        "org.thoughtcrime.securesms.backup",
    )
    val whatsAppPkgs = listOf("com.whatsapp", "com.whatsapp.w4b")
    val viberPkgs = listOf("com.viber.voip")
    val telegramPkgs = listOf(
        "org.telegram.messenger",
        "org.telegram.messenger.web",
        "org.telegram.messenger.beta",
        "org.telegram.plus",
        "org.thunderdog.challegram",
        "nekogram.messenger",
        "tw.nekomimi.nekogram",
        "xyz.nextalone.nagram",
        "org.aka.messenger",
        "org.telegram.messenger.huawei",
        "org.telegram.messenger.premium",
        "com.exteragram.messenger",
        "org.forkgram.messenger",
        "it.belloworld.mercurygram",
        "it.belloworld.mercurygram.beta",
    )

    fun installed(context: Context, pkgs: List<String>): String? {
        val pm = context.packageManager
        for (p in pkgs) {
            if (runCatching { pm.getPackageInfo(p, 0) }.isSuccess) return p
        }
        return null
    }

    fun installedPackage(context: Context, channel: MessengerChannel): String? = when (channel) {
        MessengerChannel.Signal -> installed(context, signalPkgs)
        MessengerChannel.WhatsApp -> installed(context, whatsAppPkgs)
        MessengerChannel.Telegram -> installed(context, telegramPkgs)
        MessengerChannel.Viber -> installed(context, viberPkgs)
        else -> null
    }

    fun chatIntent(channel: MessengerChannel, number: String, body: String? = null, pkg: String?): Intent {
        val digits = number.filter { it.isDigit() }
        val plus = "+$digits"
        val intent = when (channel) {
            MessengerChannel.WhatsApp -> Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits")).apply {
                if (!body.isNullOrBlank()) data = Uri.parse("https://wa.me/$digits?text=${Uri.encode(body)}")
            }
            MessengerChannel.Telegram -> Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?phone=$digits")).apply {
                if (!body.isNullOrBlank()) putExtra(Intent.EXTRA_TEXT, body)
            }
            MessengerChannel.Signal -> Intent(Intent.ACTION_VIEW, Uri.parse("sgnl://signal.me/#p/$plus"))
            MessengerChannel.Viber -> Intent(Intent.ACTION_VIEW, Uri.parse("viber://chat?number=$digits"))
            MessengerChannel.Sms, MessengerChannel.Phone -> MessengerIntentUtils.sms(number, body)
        }
        if (!pkg.isNullOrBlank()) intent.setPackage(pkg)
        return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun callIntent(channel: MessengerChannel, number: String, pkg: String?): Intent {
        val digits = number.filter { it.isDigit() }
        val plus = "+$digits"
        val intent = when (channel) {
            MessengerChannel.WhatsApp -> Intent(Intent.ACTION_VIEW, Uri.parse("whatsapp://call?phone=$digits"))
            MessengerChannel.Telegram -> Intent(Intent.ACTION_VIEW, Uri.parse("tg://call?phone=$digits"))
            MessengerChannel.Signal -> Intent(Intent.ACTION_VIEW, Uri.parse("sgnl://signal.me/#p/$plus"))
            MessengerChannel.Viber -> Intent(Intent.ACTION_VIEW, Uri.parse("viber://call?number=$digits"))
            else -> Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null))
        }
        if (!pkg.isNullOrBlank()) intent.setPackage(pkg)
        return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun openChat(context: Context, channel: MessengerChannel, number: String, body: String? = null): Boolean {
        if (channel == MessengerChannel.Sms || channel == MessengerChannel.Phone) {
            return context.tryStartActivity(MessengerIntentUtils.sms(number, body).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        val pkg = installedPackage(context, channel) ?: return false
        val ok = context.tryStartActivity(chatIntent(channel, number, body, pkg))
        if (ok) return true
        return context.tryStartActivity(chatIntent(channel, number, body, null))
    }

    fun openCall(context: Context, channel: MessengerChannel, number: String): Boolean {
        if (channel == MessengerChannel.Phone || channel == MessengerChannel.Sms) return false
        val pkg = installedPackage(context, channel) ?: return false
        if (context.tryStartActivity(callIntent(channel, number, pkg))) return true
        if (context.tryStartActivity(callIntent(channel, number, null))) return true
        return openChat(context, channel, number)
    }

    fun resolvePref(prefs: Map<String, String>, number: String): MessengerChannel {
        val digits = number.filter { it.isDigit() }
        if (digits.isEmpty()) return MessengerChannel.Phone
        val exact = prefs[digits] ?: prefs.entries.firstOrNull { numberMatch(it.key, digits) }?.value
        return MessengerChannel.fromId(exact)
    }

    private fun numberMatch(a: String, bDigits: String): Boolean {
        val ad = a.filter { it.isDigit() }
        if (ad.isEmpty() || bDigits.isEmpty()) return false
        return ad == bDigits || ad.endsWith(bDigits) || bDigits.endsWith(ad)
    }
}
