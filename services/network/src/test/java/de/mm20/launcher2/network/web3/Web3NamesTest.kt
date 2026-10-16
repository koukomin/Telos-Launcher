package de.mm20.launcher2.network.web3

import org.junit.Assert.*
import org.junit.Test

class Web3NamesTest {
    @Test fun namesCannotInjectPathsQueriesOrUnsupportedSuffixes() {
        assertEquals("alice.eth", Web3Names.normalize(" ALICE.eth. "))
        assertEquals("alice.crypto", Web3Names.normalize("alice.crypto"))
        listOf("alice.eth/?x=1", "alice.eth@evil.org", "alice.com", "alice.eth/../x", ".eth", "álîce.eth", "alice..eth", "-alice.eth").forEach {
            assertNull(it, Web3Names.normalize(it))
        }
    }
    @Test fun websiteRecordsCannotOpenScriptsOrSendEmbeddedCredentials() {
        listOf("javascript:alert(1)", "http://example.org", "https://alice:secret@example.org", "file:///data/x").forEach {
            assertNull(it, Web3Names.httpsUrl(it))
        }
        assertEquals("https://example.org/path", Web3Names.httpsUrl("https://example.org/path"))
        assertNull(Web3Names.ipfsGateway("../../etc/passwd"))
        assertNull(Web3Names.ipfsGateway("bad?query"))
    }
}
