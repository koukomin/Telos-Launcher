package de.mm20.launcher2.comms.recording

import de.mm20.launcher2.ktx.PhoneNumberKey

/** Older filenames dropped '+'. Restore only a complete matching international number, never a suffix. */
fun recordingNumberMatches(contactNumber: String, recordedNumber: String): Boolean {
    if (PhoneNumberKey.matches(contactNumber, recordedNumber)) return true
    val key = PhoneNumberKey.of(contactNumber)
    return key.startsWith('+') && recordedNumber.length >= 10 &&
        recordedNumber.all { it in '0'..'9' } && key.drop(1) == recordedNumber
}
