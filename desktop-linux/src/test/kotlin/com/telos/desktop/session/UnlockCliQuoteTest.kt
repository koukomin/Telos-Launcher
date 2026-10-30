package com.telos.desktop.session

import org.junit.Assert.assertEquals
import org.junit.Test

class UnlockCliQuoteTest {
    @Test
    fun quotesSpacesAndApostrophes() {
        assertEquals("'/home/user/Telos Vault'", UnlockCli.shQuote("/home/user/Telos Vault"))
        assertEquals("'/home/o'\\''neil'", UnlockCli.shQuote("/home/o'neil"))
    }
}
