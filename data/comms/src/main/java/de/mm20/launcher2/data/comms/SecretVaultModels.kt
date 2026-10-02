package de.mm20.launcher2.data.comms

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "secret_contacts")
data class SecretContact(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val phoneNumber: String
)

@Entity(tableName = "secret_call_logs")
data class SecretCallLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val contactId: Long?,
    val number: String,
    val date: Long,
    val duration: Long,
    val type: Int
)

@Entity(tableName = "secret_sms")
data class SecretSms(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val address: String,
    val body: String,
    val date: Long,
    val type: Int
)
