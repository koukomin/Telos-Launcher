package de.mm20.launcher2

import de.mm20.launcher2.search.GreekFold
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GreekFoldTest {
    private fun same(vararg words: String) {
        val folds = words.map { GreekFold.fold(it) }
        assertTrue("$words -> $folds", folds.distinct().size == 1)
    }

    @Test fun accentsAndCase() = same("καλημέρα", "Καλημερα", "ΚΑΛΗΜΕΡΑ", "kalimera", "Kalimera", "kalhmera")
    @Test fun finalSigma() = same("Ουρανός", "ουρανοσ", "ouranos", "uranos")
    @Test fun digraphs() = same("Μπάλα", "mpala", "bala")
    @Test fun digraphsNt() = same("Ντομάτα", "ntomata", "domata")
    @Test fun thetaChiPsi() = same("Θέλω", "thelw", "thelo", "8elo") ; @Test fun chi() = same("Χάρτης", "xartis", "chartis")
    @Test fun psi() = same("Ψωμί", "psomi", "psomh")
    @Test fun phiAndEf() = same("Αυτοκίνητο", "aftokinito", "avtokinito")
    @Test fun doubleLetters() = same("Ελλάδα", "ellada", "elada")
    @Test fun doubleBeta() = same("Σάββατο", "savvato", "savato")
    @Test fun dialytika() = same("Ταΐζω", "taizo", "taizw")
    @Test fun substringKeepsWorking() {
        assertTrue(GreekFold.fold("Καλημέρα κόσμε").contains(GreekFold.fold("kosm")))
        assertTrue(GreekFold.fold("Μουσική").startsWith(GreekFold.fold("mous")))
    }

    @Test fun emptyAndAscii() {
        assertEquals("", GreekFold.fold(""))
        assertEquals(GreekFold.stripAccents("Hello World"), "hello world")
        assertEquals("σ", GreekFold.stripAccents("Σ"))
        assertEquals("οσ", GreekFold.stripAccents("ΟΣ"))
    }
    @Test fun accentedLatinAndGreekMarks() {
        assertEquals("cafe", GreekFold.stripAccents("Café"))
        assertEquals("ι", GreekFold.stripAccents("ΐ"))
    }
    @Test fun matchesHelper() {
        val q = GreekFold.fold("kalim")
        assertTrue(GreekFold.matches("Καλημέρα", q))
        assertTrue(!GreekFold.matches(null, q))
        assertTrue(!GreekFold.matches("", q))
        assertTrue(!GreekFold.matches("abc", ""))
    }
    @Test fun needsFoldAndHasGreek() {
        assertTrue(GreekFold.needsFold("ab1"))
        assertTrue(!GreekFold.needsFold("123 45"))
        assertTrue(GreekFold.hasGreek("aβ"))
        assertTrue(!GreekFold.hasGreek("abc"))
    }
    @Test fun prepareFinishEqualsFold() {
        for (w in listOf("Καλημέρα", "plain text", "Σάββατο", "thelw")) {
            assertEquals(GreekFold.fold(w), GreekFold.finish(GreekFold.prepare(w)))
        }
    }
}
