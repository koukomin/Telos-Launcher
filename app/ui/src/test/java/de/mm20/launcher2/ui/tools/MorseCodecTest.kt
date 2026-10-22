package de.mm20.launcher2.ui.tools

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class MorseCodecTest {
    @Test fun greekAccentsFinalSigmaAndRadioAlphabet() {
        assertEquals("... --- ...", MorseCodec.encode("σός").code)
        assertEquals("-.-. ---- --.- .--", MorseCodec.encode("θχψώ").code)
        assertEquals(MorseCodec.encode("ά").code, MorseCodec.encode("α\u0301").code)
    }
    @Test fun encodingDoesNotDependOnDeviceLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"))
            assertEquals("..", MorseCodec.encode("i").code)
        } finally { Locale.setDefault(previous) }
    }
    @Test fun scriptsUsedByAllSupportedLocalesProduceSignals() {
        listOf("Hello", "Καλημέρα", "Привет", "Привіт", "Прывітанне", "Здравей", "مرحبا", "سلام", "שלום", "नमस्ते", "নমস্কার", "你好", "こんにちは", "안녕하세요", "สวัสดี", "Xin chào", "İyi günler", "Český", "Żółć", "Għodwa", "Dia dhuit", "Azərbaycan").forEach {
            val encoded = MorseCodec.encode(it)
            assertTrue("Missing code: $it", encoded.code.isNotBlank())
            assertFalse("Unsupported letter: $it", encoded.unsupported)
        }
    }
    @Test fun romanizationIsVisibleAndUnknownSymbolsAreReported() {
        val chinese = MorseCodec.encode("你好")
        assertTrue(chinese.romanized)
        assertTrue(chinese.readable.isNotBlank())
        assertTrue(MorseCodec.encode("🧊").unsupported)
        assertTrue(MorseCodec.encode("🧊").code.isBlank())
    }
    @Test fun standardTimingIncludesOnlyOneGapBetweenLettersAndWords() {
        assertEquals(listOf(MorseCodec.Pulse(true,1), MorseCodec.Pulse(false,3), MorseCodec.Pulse(true,3), MorseCodec.Pulse(false,7), MorseCodec.Pulse(true,1)), MorseCodec.pulses(MorseCodec.encode("ET E").code))
        assertEquals(50, MorseCodec.pulses(MorseCodec.encode("PARIS").code).sumOf { it.units } + 7)
    }
    @Test fun whitespaceIsCollapsedWithoutLosingWordBoundaries() {
        assertEquals(". / -", MorseCodec.encode("  E\n\tT  ").code)
        assertEquals("-..-.", MorseCodec.encode("/").code)
    }
}
