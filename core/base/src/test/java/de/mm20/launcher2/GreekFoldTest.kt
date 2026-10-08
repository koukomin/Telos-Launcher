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
}
