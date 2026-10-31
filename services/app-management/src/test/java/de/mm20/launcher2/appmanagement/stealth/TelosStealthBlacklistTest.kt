package de.mm20.launcher2.appmanagement.stealth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelosStealthBlacklistTest {
    @Test
    fun seededGreekBankIsListed() {
        assertTrue("gr.nbg.mobilebanking" in TelosStealthBlacklist.SEEDED)
        assertTrue("com.revolut.revolut" in TelosStealthBlacklist.SEEDED)
        assertTrue("com.nianticlabs.pokemongo" in TelosStealthBlacklist.SEEDED)
    }

    @Test
    fun financePackageNameMatches() {
        assertTrue(
            TelosStealthBlacklist.matchesHeuristic(
                "com.example.bank",
                "Example Bank",
                0,
            ),
        )
    }

    @Test
    fun labelHeuristicsMatch() {
        assertTrue(TelosStealthBlacklist.matchesHeuristic("com.foo", "Eurobank Pay", 0))
        assertTrue(TelosStealthBlacklist.matchesHeuristic("com.bar.wallet", "Bar", 0))
        assertFalse(TelosStealthBlacklist.matchesHeuristic("com.android.calculator2", "Calculator", 0))
    }
}
