package de.mm20.launcher2.comms

import android.telephony.PhoneNumberUtils

object PhoneNumbers {
    fun digits(number: String): String = number.filter { it.isDigit() }

    fun match(a: String, b: String): Boolean {
        if (a.isEmpty() || b.isEmpty()) return false
        if (PhoneNumberUtils.compare(a, b)) return true
        val da = digits(a)
        val db = digits(b)
        if (da.isEmpty() || db.isEmpty()) return false
        return da == db || (da.length >= 7 && db.length >= 7 && (da.endsWith(db) || db.endsWith(da)))
    }
}
