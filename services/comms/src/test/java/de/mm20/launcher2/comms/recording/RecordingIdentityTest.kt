package de.mm20.launcher2.comms.recording

import org.junit.Assert.*
import org.junit.Test

class RecordingIdentityTest {
    @Test fun greekPrefixesAndLegacyFilenamesResolveTheSameContact() {
        for (saved in listOf("+30 210 1234567", "00302101234567", "2101234567")) {
            for (recorded in listOf("302101234567", "+302101234567", "00302101234567", "2101234567")) {
                assertTrue("$saved / $recorded", recordingNumberMatches(saved, recorded))
            }
        }
    }
    @Test fun neverMatchesSuffixesOrAnotherCountry() {
        assertFalse(recordingNumberMatches("+442101234567", "2101234567"))
        assertFalse(recordingNumberMatches("+302101234567", "1234567"))
        assertFalse(recordingNumberMatches("+302101234567", "302101234568"))
        assertFalse(recordingNumberMatches("", "Unknown"))
    }
}
