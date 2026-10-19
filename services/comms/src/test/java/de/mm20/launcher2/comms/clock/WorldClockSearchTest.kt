package de.mm20.launcher2.comms.clock

import org.junit.Assert.*
import org.junit.Test

class WorldClockSearchTest {
    @Test fun greekAccentsAndGreeklishDigitsMatch() {
        assertEquals(WorldClockSearch.normalize("Αθήνα"), WorldClockSearch.normalize("a8ina"))
        assertEquals(WorldClockSearch.normalize("Ελλάδα"), WorldClockSearch.normalize("ellada"))
    }
    @Test fun searchRequiresEveryTokenAcrossCityAndCountryAliases() {
        val city = WorldClockCity("Europe/Athens", "Αθήνα", "Ελλάδα", WorldClockSearch.normalize("Αθήνα Ελλάδα Athens Greece"))
        assertEquals(listOf(city), WorldClockSearch.search(listOf(city), "a8ina ellada"))
        assertTrue(WorldClockSearch.search(listOf(city), "Athens Italy").isEmpty())
        assertTrue(WorldClockSearch.search(listOf(city), "   ").isEmpty())
    }
    @Test fun nonLatinCityAndCountryAliasesRemainSearchable() {
        val city = WorldClockCity("Asia/Tokyo", "Tokyo", "Japan", WorldClockSearch.normalize("Tokyo Japan 東京 日本"))
        assertEquals(listOf(city), WorldClockSearch.search(listOf(city), "東京 日本"))
        assertTrue(WorldClockSearch.search(listOf(city), "大阪").isEmpty())
    }
}
