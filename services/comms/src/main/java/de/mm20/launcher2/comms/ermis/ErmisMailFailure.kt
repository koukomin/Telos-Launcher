package de.mm20.launcher2.comms.ermis

import java.util.Collections
import java.util.IdentityHashMap
import javax.mail.AuthenticationFailedException
import javax.mail.MessagingException
import javax.net.ssl.SSLException

internal class ErmisConnectionException(val endpoint: String, cause: Exception) : MessagingException(null, cause)
class ErmisGoogleAccessNotGrantedException : AuthenticationFailedException()
internal enum class ErmisFailureKind { GOOGLE_ACCESS, AUTHENTICATION, TLS, OTHER }

/** Classify wrapped JavaMail failures without displaying untrusted server responses or credentials. */
internal object ErmisMailFailure {
    fun causes(failure: Throwable): List<Throwable> {
        val seen = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
        val pending = ArrayDeque<Throwable>()
        val result = mutableListOf<Throwable>()
        pending.add(failure)
        while (pending.isNotEmpty() && result.size < 16) {
            val current = pending.removeFirst()
            if (!seen.add(current)) continue
            result += current
            current.cause?.let { pending.add(it) }
            if (current is MessagingException) current.nextException?.let { pending.add(it) }
        }
        return result
    }
    fun kind(causes: List<Throwable>): ErmisFailureKind = when {
        causes.any { it is ErmisGoogleAccessNotGrantedException } -> ErmisFailureKind.GOOGLE_ACCESS
        causes.any { it is AuthenticationFailedException } -> ErmisFailureKind.AUTHENTICATION
        causes.any { it is SSLException } -> ErmisFailureKind.TLS
        else -> ErmisFailureKind.OTHER
    }
}
