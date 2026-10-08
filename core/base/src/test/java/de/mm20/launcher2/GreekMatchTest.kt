package de.mm20.launcher2

import de.mm20.launcher2.search.GreekFold
import de.mm20.launcher2.search.ResultScore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GreekMatchTest {
    @Test fun matchesGreeklish() {
        assertTrue(GreekFold.matches("Γιώργος Παπαδόπουλος", GreekFold.fold("giorgos")))
        assertTrue(GreekFold.matches("Γιώργος Παπαδόπουλος", GreekFold.fold("papad")))
        assertTrue(GreekFold.matches("Καλημέρα", GreekFold.fold("ΚΑΛΗΜΕΡΑ")))
        assertFalse(GreekFold.matches("Καλημέρα", GreekFold.fold("nikos")))
        assertFalse(GreekFold.matches(null, GreekFold.fold("a")))
    }

    @Test fun needsFold() {
        assertTrue(GreekFold.needsFold("kali"))
        assertFalse(GreekFold.needsFold("123"))
    }

    @Test fun resultScoreGreek() {
        val s = ResultScore.from("kalim", primaryFields = listOf("Καλημέρα"))
        assertTrue(s.isPrefix)
        assertTrue(s.isSubstring)
        val plain = ResultScore.from("abc", primaryFields = listOf("abcd"))
        assertTrue(plain.isPrefix)
    }
}
