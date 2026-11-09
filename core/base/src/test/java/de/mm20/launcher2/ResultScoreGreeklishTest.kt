package de.mm20.launcher2

import de.mm20.launcher2.search.ResultScore
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultScoreGreeklishTest {
    @Test
    fun greeklishFindsGreekLabels() {
        assertTrue(ResultScore.from("tilefono", listOf("Τηλέφωνο")).score >= ResultScore.MATCH_THRESHOLD)
        assertTrue(ResultScore.from("anazitisi", listOf("Αναζήτηση")).score >= ResultScore.MATCH_THRESHOLD)
        assertTrue(ResultScore.from("kalhmera", listOf("Καλημέρα")).score >= ResultScore.MATCH_THRESHOLD)
        assertTrue(ResultScore.from("emfanisi", listOf("Εμφάνιση")).score >= ResultScore.MATCH_THRESHOLD)
    }

    @Test
    fun englishStillMatches() {
        assertTrue(ResultScore.from("wifi", listOf("Wi-Fi")).score >= ResultScore.MATCH_THRESHOLD)
        assertTrue(ResultScore.from("Phone", listOf("Phone")).score >= ResultScore.MATCH_THRESHOLD)
    }

    @Test
    fun greeklishToGreekHelpsNetworkQueries() {
        assertTrue(de.mm20.launcher2.search.GreekFold.hasGreek(de.mm20.launcher2.search.GreekFold.greeklishToGreek("athina")))
    }
}
