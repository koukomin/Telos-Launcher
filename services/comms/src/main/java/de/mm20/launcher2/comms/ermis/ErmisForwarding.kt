package de.mm20.launcher2.comms.ermis

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import kotlinx.serialization.Serializable

@Serializable
data class ErmisForwardSource(val accountId: String, val folder: String, val uid: Long, val uidValidity: Long)

internal object ErmisForwardPolicy {
    private val accountId = Regex("[A-Za-z0-9_-]{1,80}")
    fun validate(source: ErmisForwardSource) {
        require(accountId.matches(source.accountId))
        ErmisMailboxPolicy.folder(source.folder)
        require(source.uid in 1..4_294_967_295L && source.uidValidity in 1..4_294_967_295L)
    }
    fun requireSameMailbox(source: ErmisForwardSource, currentValidity: Long) {
        validate(source)
        require(source.uidValidity == currentValidity) { "Forwarded source mailbox changed" }
    }
}

/** Hard cap applies while RFC822 bytes are written, before SMTP or MIME attachment allocation. */
internal class ErmisBoundedOutputStream(private val limit: Int) : OutputStream() {
    private val output = ByteArrayOutputStream()
    init { require(limit in 1..20_000_000) }
    override fun write(value: Int) {
        require(output.size() < limit) { "Forwarded message exceeds attachment size limit" }
        output.write(value)
    }
    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        require(offset >= 0 && length >= 0 && offset <= bytes.size - length)
        require(length <= limit - output.size()) { "Forwarded message exceeds attachment size limit" }
        output.write(bytes, offset, length)
    }
    fun toByteArray(): ByteArray = output.toByteArray()
}
