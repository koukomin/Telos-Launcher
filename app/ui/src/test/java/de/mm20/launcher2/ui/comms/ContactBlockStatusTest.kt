package de.mm20.launcher2.ui.comms

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ContactBlockStatusTest {
    @Test fun blockedAndUnblockedRemainDistinct() = runBlocking {
        assertEquals(ContactBlockStatus.Blocked, readContactBlockStatus({ true }, { error("unexpected") }))
        assertEquals(ContactBlockStatus.Unblocked, readContactBlockStatus({ false }, { error("unexpected") }))
    }
    @Test fun keystoreOrDatabaseFailureDoesNotPretendTheNumberIsUnblocked() = runBlocking {
        var reported = false
        assertEquals(ContactBlockStatus.Unavailable, readContactBlockStatus({ throw java.security.GeneralSecurityException() }, { reported = true }))
        assertTrue(reported)
    }
    @Test fun nativeLibraryFailureIsRecoverableForContactDetails() = runBlocking {
        assertEquals(ContactBlockStatus.Unavailable, readContactBlockStatus({ throw UnsatisfiedLinkError("native SQLCipher unavailable") }, {}))
    }
    @Test fun cancellationIsNotConsumedOrReportedAsFailure() = runBlocking {
        try {
            readContactBlockStatus({ throw CancellationException() }, { error("cancelled lookup reported") })
            fail()
        } catch (_: CancellationException) { }
    }
    @Test fun retryCanRecoverWithoutRestartingOrErasingData() = runBlocking {
        assertEquals(ContactBlockStatus.Unavailable, readContactBlockStatus({ error("temporarily unavailable") }, {}))
        assertEquals(ContactBlockStatus.Blocked, readContactBlockStatus({ true }, {}))
    }
}
