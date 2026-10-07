package de.mm20.launcher2.ui.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotesImportTest {
    @Test
    fun markdownFrontMatter() {
        val n = NotesImport.parseMarkdown("file", NotesImport.toMarkdown(Note(title = "Hello", body = "- [ ] a\n", pinned = true, labels = listOf("x", "y"))))
        assertEquals("Hello", n.title); assertEquals("- [ ] a\n", n.body); assertTrue(n.pinned); assertEquals(listOf("x", "y"), n.labels)
    }

    @Test
    fun plainMarkdownUsesFileName() {
        assertEquals("todo", NotesImport.readFile("todo.md", "buy milk")[0].title)
    }

    @Test
    fun enex() {
        val x = "<en-export><note><title>T</title><content><![CDATA[<en-note><div>one</div><div>two &amp; three</div></en-note>]]></content><tag>w</tag></note></en-export>"
        val n = NotesImport.readFile("a.enex", x)[0]
        assertEquals("T", n.title); assertEquals("one\ntwo & three", n.body); assertEquals(listOf("w"), n.labels)
    }
}
