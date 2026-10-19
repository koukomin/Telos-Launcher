package de.mm20.launcher2.comms.ermis
import org.junit.Assert.*
import org.junit.Test
class ErmisValidationTest {
    private val valid=ErmisAccount("account-1","Test","test@example.com","imap.example.com",993,"smtp.example.com")
    @Test fun acceptsTlsServerConfiguration() { ErmisValidation.validate(valid) }
    @Test fun neverSendsGoogleTokenToCustomHost() { assertThrows(IllegalArgumentException::class.java) { ErmisValidation.validate(valid.copy(googleAccount=valid.email)) } }
    @Test fun rejectsInjectedHost() { assertThrows(IllegalArgumentException::class.java) { ErmisValidation.validate(valid.copy(imapHost="host\r\nother")) } }
    @Test fun rejectsOutOfRangePorts() { assertThrows(IllegalArgumentException::class.java) { ErmisValidation.validate(valid.copy(smtpPort=65536)) } }
    @Test fun rejectsPathLikeAccountIdentifiers() { assertThrows(IllegalArgumentException::class.java) { ErmisValidation.validate(valid.copy(id="../secret")) } }
}
