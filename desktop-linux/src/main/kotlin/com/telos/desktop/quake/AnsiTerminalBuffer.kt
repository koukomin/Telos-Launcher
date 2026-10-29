package com.telos.desktop.quake

import androidx.compose.ui.graphics.Color

data class Cell(var ch: Char = ' ', var fg: Color = Color(0xFFEDE7F6), var bg: Color = Color.Transparent, var bold: Boolean = false)

class AnsiTerminalBuffer(val cols: Int = 120, val rows: Int = 32, val scrollback: Int = 10000) {
    val lines = ArrayList<String>(scrollback)
    private val raw = StringBuilder()
    var cursorRow = 0
    var cursorCol = 0
    var fg = Color(0xFFEDE7F6)
    var bg = Color.Transparent
    var bold = false
    var search = ""

    fun feed(bytes: ByteArray) {
        raw.append(String(bytes, Charsets.UTF_8))
        parse(raw.toString())
        raw.clear()
    }

    fun text(): String = lines.joinToString("\n")

    fun visible(): List<String> = if (lines.size <= rows) lines else lines.takeLast(rows)

    private fun parse(s: String) {
        var i = 0
        val current = StringBuilder(lines.lastOrNull() ?: "")
        if (lines.isNotEmpty()) lines.removeAt(lines.lastIndex)
        fun flushLine() {
            lines += current.toString()
            current.clear()
            if (lines.size > scrollback) lines.removeAt(0)
        }
        while (i < s.length) {
            val c = s[i]
            if (c == '\u001b' && i + 1 < s.length && s[i + 1] == '[') {
                val end = (i + 2 until s.length).firstOrNull { s[it] in '@'..'~' } ?: (s.length - 1)
                val body = s.substring(i + 2, end)
                val cmd = s.getOrNull(end) ?: 'm'
                handleCsi(body, cmd, current)
                i = end + 1
                continue
            }
            when (c) {
                '\n' -> flushLine()
                '\r' -> {}
                '\b' -> if (current.isNotEmpty()) current.deleteCharAt(current.lastIndex)
                '\u0007' -> {}
                else -> current.append(c)
            }
            i++
        }
        lines += current.toString()
        if (lines.size > scrollback) lines.removeAt(0)
    }

    private fun handleCsi(body: String, cmd: Char, current: StringBuilder) {
        when (cmd) {
            'm' -> {
                val codes = body.split(';').mapNotNull { it.toIntOrNull() }.ifEmpty { listOf(0) }
                for (code in codes) applySgr(code)
            }
            'H', 'f' -> {
                val p = body.split(';')
                cursorRow = ((p.getOrNull(0)?.toIntOrNull() ?: 1) - 1).coerceAtLeast(0)
                cursorCol = ((p.getOrNull(1)?.toIntOrNull() ?: 1) - 1).coerceAtLeast(0)
            }
            'J' -> if (body == "2" || body == "3") {
                lines.clear()
                current.clear()
            }
            'K' -> current.clear()
            'A' -> cursorRow = (cursorRow - (body.toIntOrNull() ?: 1)).coerceAtLeast(0)
            'B' -> cursorRow += body.toIntOrNull() ?: 1
            'C' -> cursorCol += body.toIntOrNull() ?: 1
            'D' -> cursorCol = (cursorCol - (body.toIntOrNull() ?: 1)).coerceAtLeast(0)
        }
    }

    private fun applySgr(code: Int) {
        when (code) {
            0 -> { fg = Color(0xFFEDE7F6); bg = Color.Transparent; bold = false }
            1 -> bold = true
            22 -> bold = false
            30 -> fg = Color(0xFF1A1A1A)
            31 -> fg = Color(0xFFFF5555)
            32 -> fg = Color(0xFF50FA7B)
            33 -> fg = Color(0xFFF1FA8C)
            34 -> fg = Color(0xFFBD93F9)
            35 -> fg = Color(0xFFFF007F)
            36 -> fg = Color(0xFF00F0FF)
            37 -> fg = Color(0xFFF8F8F2)
            39 -> fg = Color(0xFFEDE7F6)
            40 -> bg = Color.Black
            41 -> bg = Color(0xFFAA0000)
            42 -> bg = Color(0xFF00AA00)
            90 -> fg = Color(0xFF6272A4)
            in 90..97 -> fg = Color(0xFFCCCCCC)
        }
    }
}
