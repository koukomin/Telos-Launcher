package de.mm20.launcher2.comms

import android.telephony.PhoneNumberUtils

object PhoneNumbers {
    fun digits(number: String): String = number.filter { it.isDigit() }

    fun match(a: String, b: String): Boolean {
        if (a.isEmpty() || b.isEmpty()) return false
        val ka = de.mm20.launcher2.ktx.PhoneNumberKey.of(a)
        val kb = de.mm20.launcher2.ktx.PhoneNumberKey.of(b)
        if (ka.isNotEmpty() && ka == kb) return true
        // Never collapse two different international numbers or match a short number by its suffix.
        if (ka.startsWith('+') && kb.startsWith('+')) return false
        if (PhoneNumberUtils.compare(a, b)) return true
        val da = digits(a)
        val db = digits(b)
        if (da.isEmpty() || db.isEmpty()) return false
        return da == db
    }
}
