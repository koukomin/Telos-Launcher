package de.mm20.launcher2.comms.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelosSearchTest {
    private fun yes(q: String, vararg f: String?) =
        assertTrue("'$q' should match ${f.toList()}", TelosSearch.matches(q, *f))

    private fun no(q: String, vararg f: String?) =
        assertFalse("'$q' should not match ${f.toList()}", TelosSearch.matches(q, *f))

    @Test fun blankQueryMatchesAll() { yes("", "x"); yes("   ", null) }
    @Test fun caseInsensitive() { yes("JOHN", "john smith") }
    @Test fun latinAccents() { yes("jose", "José"); yes("josé", "Jose"); yes("cafe", "Café") }
    @Test fun greekToneless() { yes("αλφα", "άλφα") }
    @Test fun greekToneTyped() { yes("άλφα", "αλφα") }
    @Test fun greekUppercase() { yes("άλφα", "ΆΛΦΑ"); yes("αλφα", "ΆΛΦΑ") }
    @Test fun finalSigma() { yes("γιαννης", "Γιάννης"); yes("γιαννησ", "Γιάννης"); yes("ΓΙΑΝΝΗΣ", "Γιάννης") }
    @Test fun diaeresis() { yes("ταξι", "ταξί"); yes("ϊ", "ΐ"); yes("υψηλα", "ΰψηλά") }
    @Test fun apostropheTonos() { yes("για´ννης", "Γιάννης"); yes("γιά΄ννης", "Γιάννης") }
    @Test fun greeklishGiannis() { yes("giannis", "Γιάννης"); yes("yiannis", "Γιάννης") }
    @Test fun greeklishKostas() { yes("kostas", "Κώστας") }
    @Test fun greeklishPrefix() { yes("gia", "Γιάννης"); yes("kos", "Κώστας") }
    @Test fun greeklishTh() { yes("thanasis", "Θανάσης"); yes("8anasis", "Θανάσης") }
    @Test fun greeklishPs() { yes("psaras", "Ψαράς") }
    @Test fun greeklishCh() { yes("christos", "Χρήστος"); yes("xristos", "Χρήστος") }
    @Test fun greeklishX() { yes("xenia", "Ξένια"); yes("alexandros", "Αλέξανδρος") }
    @Test fun alexandrosBothWays() { yes("Alexandros", "Αλέξανδρος"); yes("αλεξανδρος", "Alexandros") }
    @Test fun greeklishOu() { yes("papadopoulos", "Παπαδόπουλος"); yes("papadopulos", "Παπαδόπουλος") }
    @Test fun greeklishAiEiOi() { yes("eleni", "Ελένη"); yes("eirini", "Ειρήνη"); yes("irini", "Ειρήνη"); yes("maria", "Μαρία") }
    @Test fun greeklishW() { yes("kwstas", "Κώστας") }
    @Test fun giorgosVariants() {
        yes("giorgos", "Γιώργος"); yes("yiorgos", "Γιώργος"); yes("georgos", "Γιώργος".replace("Γι", "Γε"))
        yes("giorgos", "Γιώργος Παπαδόπουλος")
    }
    @Test fun babisVariants() {
        yes("mpampis", "Μπάμπης"); yes("babis", "Μπάμπης"); yes("Μπάμπης", "Babis"); yes("μπαμπης", "Mpampis")
    }
    @Test fun ntAndGk() { yes("ntinos", "Ντίνος"); yes("gkikas", "Γκίκας") }
    @Test fun greekQueryLatinText() {
        yes("γιαννης", "Giannis"); yes("κωστας", "Kostas"); yes("παπαδοπουλος", "Papadopoulos")
    }
    @Test fun papadopoulosBothWays() { yes("Papadopoulos", "Παπαδόπουλος"); yes("Παπαδόπουλος", "Papadopoulos") }
    @Test fun multipleTokensAnd() {
        yes("giannis papa", "Γιάννης Παπαδόπουλος"); yes("παπα γιαν", "Γιάννης Παπαδόπουλος")
        no("giannis kostas", "Γιάννης Παπαδόπουλος")
    }
    @Test fun tokensInDifferentFields() { yes("giannis 697", "Γιάννης", "+30 697 123 4567") }
    @Test fun phoneIgnoresSpacesDashes() { yes("6971234567", "697 123 4567"); yes("697-123", "697 123 4567") }
    @Test fun phoneLeadingPlusAnd00() { yes("+306971234567", "+30 697 123 4567"); yes("00306971234567", "+30 697 123 4567") }
    @Test fun phoneSubstring() { yes("1234", "+30 697 123 4567".replace(" 123 ", " 1234 ")) }
    @Test fun phoneNoMatch() { no("6989", "+30 697 123 4567") }
    @Test fun digitsDontMatchGreeklish() { no("8", "Θανάσης") }
    @Test fun nonMatchingLatin() { no("zzz", "Giannis"); no("nikos", "Kostas") }
    @Test fun nonMatchingGreek() { no("νικος", "Κώστας"); no("xyz", "Γιάννης Παπαδόπουλος") }
    @Test fun nullFields() { no("a", null, null); yes("a", null, "abc") }
    @Test fun apostropheInName() { yes("obrien", "O'Brien"); yes("o'brien", "O'Brien") }
    @Test fun johnSkippingH() { yes("jon", "John") }

    @Test fun scoreZeroOnMismatch() { assertEquals(0, TelosSearch.score("qqq", "abc")) }
    @Test fun scoreOrder() {
        val exact = TelosSearch.score("kostas", "Kostas")
        val prefix = TelosSearch.score("kost", "Kostas")
        val contains = TelosSearch.score("ost", "Kostas")
        val sub = TelosSearch.score("johnsm", "John Smith")
        assertTrue("$exact > $prefix", exact > prefix)
        assertTrue("$prefix > $contains", prefix > contains)
        assertTrue("$contains > $sub", contains > sub)
        assertTrue(sub > 0)
    }

    @Test fun filterKeepsOrderOnBlank() {
        val items = listOf("b", "a", "c")
        assertEquals(items, TelosSearch.filter(items, " ") { listOf(it) })
    }

    @Test fun filterSortsByScore() {
        val items = listOf("Ostrich", "Kostas", "Ost")
        assertEquals(listOf("Ost", "Ostrich", "Kostas"), TelosSearch.filter(items, "ost") { listOf(it) })
    }

    @Test fun filterIsStable() {
        val items = listOf("Anna 1", "Anna 2", "Anna 3")
        assertEquals(items, TelosSearch.filter(items, "anna") { listOf(it) })
    }

    @Test fun filterDropsNonMatches() {
        val items = listOf("Γιάννης", "Κώστας", "Giannis")
        assertEquals(listOf("Γιάννης", "Giannis"), TelosSearch.filter(items, "giannis") { listOf(it) })
    }
}
