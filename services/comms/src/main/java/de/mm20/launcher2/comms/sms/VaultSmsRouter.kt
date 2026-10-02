package de.mm20.launcher2.comms.sms

interface VaultSmsRouter {
    suspend fun isHiddenContact(phoneNumber: String): Boolean
    suspend fun saveSecretSms(address: String, body: String, date: Long, type: Int)
}
