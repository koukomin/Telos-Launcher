package de.mm20.launcher2.ktx

/** Conservative identity key, not a formatter or a suffix match. Greek national numbers have 10 digits. */
object PhoneNumberKey {
    fun of(raw: String): String {
        val value = raw.trim()
        if (value.isEmpty() || value.any { !(it in '0'..'9' || it in "+ -().\u00a0") }) return ""
        val compact = value.filterNot { it in " -().\u00a0" }
        if (compact.count { it == '+' } > 1 || ('+' in compact && !compact.startsWith('+'))) return ""
        val digits = value.filter { it in '0'..'9' }
        if (digits.isEmpty()) return ""
        val international = when {
            compact.startsWith('+') -> digits
            digits.startsWith("00") -> digits.drop(2)
            else -> null
        }
        if (international != null) return "+$international"
        return if (digits.length == 10 && (digits.startsWith('2') || digits.startsWith('6'))) "+30$digits" else digits
    }

    fun matches(a: String, b: String): Boolean {
        val key = of(a)
        return key.isNotEmpty() && key == of(b)
    }
}
