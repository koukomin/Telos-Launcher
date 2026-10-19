package de.mm20.launcher2.comms.callerid

import org.junit.Assert.*
import org.junit.Test

class CallerLookupParserTest {
    private fun page(number: String = "2106702385", risk: String = "80", ratings: String = "92") = """
        <h1>Αριθμός τηλεφώνου: $number</h1><h3>Community business name</h3>
        <p>Αυτός ο τίτλος γράφτηκε από τον χρήστη</p><h2>Στατιστικά</h2>
        <p>Βαθμός του κινδύνου: $risk % Αριθμός αξιολογήσεων: $ratings×</p>
        <h2>Προσθήκη ενός σχολίου</h2><p>Unrelated comments</p>
    """.trimIndent()
    @Test fun acceptsGreekFormatsOnly() {
        assertEquals("2106702385", CallerLookupParser.greekNumber("+30 210 6702385"))
        assertEquals("2106702385", CallerLookupParser.greekNumber("00302106702385"))
        assertNull(CallerLookupParser.greekNumber("+442106702385"))
        assertNull(CallerLookupParser.greekNumber("2106702385;123"))
        assertNull(CallerLookupParser.greekNumber("112"))
    }
    @Test fun exactPageAggregateProducesAttributedSuggestion() {
        val result = CallerLookupParser.parse("2106702385", page(), "https://example.test")!!
        assertTrue(result.likelySpam)
        assertEquals("Community business name", result.suggestedName)
    }
    @Test fun mismatchedPageOrChallengeNeverProducesIdentity() {
        assertNull(CallerLookupParser.parse("2106702385", page("2100000000"), "https://example.test"))
        assertNull(CallerLookupParser.parse("2106702385", "<h1>Verify you are human</h1>", "https://example.test"))
    }
    @Test fun lowEvidenceAndCommentsNeverFlagSpam() {
        assertFalse(CallerLookupParser.parse("2106702385", page(ratings = "1"), "https://example.test")!!.likelySpam)
        assertFalse(CallerLookupParser.parse("2106702385", page(risk = "30"), "https://example.test")!!.likelySpam)
        assertFalse(CallerLookupParser.parse("2106702385", "<h1>Αριθμός τηλεφώνου: 2106702385</h1><h2>Προσθήκη ενός σχολίου</h2><p>Βαθμός του κινδύνου: 99 % Αριθμός αξιολογήσεων: 50×</p>", "https://example.test")!!.likelySpam)
    }
}
