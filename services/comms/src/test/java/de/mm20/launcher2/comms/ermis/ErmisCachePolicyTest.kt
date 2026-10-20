package de.mm20.launcher2.comms.ermis

import org.junit.Assert.*
import org.junit.Test

class ErmisCachePolicyTest {
    @Test fun readingManyLargeEmailsCannotGrowResidentBodiesWithoutBound() {
        val source = (1..100).map { ErmisMessage("account", it.toLong(), from="sender@example.org", subject="Mail", date=0, body="x".repeat(100_000), html="<b>format</b>") }
        val selected = source.last().copy(body="x".repeat(900_000))
        val result = ErmisCachePolicy.bound(source.dropLast(1) + selected, selected)
        assertTrue(result.sumOf { it.body.length } <= ErmisCachePolicy.MAX_BODY_CHARS)
        assertEquals(900_000, result.last().body.length)
        assertTrue(result.dropLast(1).all { it.body.length <= 32_000 && it.html.isEmpty() })
        assertEquals(100, result.size)
        assertEquals(source.map { it.uid }, result.map { it.uid })
    }
    @Test fun selectionNeverConfusesEqualUidsInAnotherFolderOrAccount() {
        val selected = ErmisMessage("first",1,from="a@example.org",subject="a",date=0,body="x".repeat(50_000),html="<b>a</b>")
        val other = selected.copy(folder="Sent")
        val second = selected.copy(accountId="second")
        val result = ErmisCachePolicy.bound(listOf(selected,other,second),selected)
        assertEquals(50_000,result[0].body.length)
        assertEquals(32_000,result[1].body.length)
        assertTrue(result.drop(1).all { it.html.isEmpty() })
    }
}
