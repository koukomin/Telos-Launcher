package de.mm20.launcher2.helper

/** Pure name planning; file operations must separately check existing destinations. */
object BatchNames {
    data class Input(val name: String, val directory: Boolean = false)
    fun plan(inputs: List<Input>, pattern: String, start: Int = 1, find: String = "", replacement: String = "", regex: Boolean = false): List<String> {
        require(pattern.isNotBlank())
        val matcher = if (regex && find.isNotEmpty()) Regex(find) else null
        return inputs.mapIndexed { index, input ->
            val dot = input.name.lastIndexOf('.').takeIf { !input.directory && it > 0 } ?: -1
            val name = if (dot < 0) input.name else input.name.substring(0, dot)
            val extension = if (dot < 0) "" else input.name.substring(dot)
            val replaced = if (matcher != null) matcher.replace(name, replacement) else if (find.isNotEmpty()) name.replace(find, replacement) else name
            val numbered = Regex("\\{n(?::([1-9]))?}").replace(pattern) { m ->
                (start.toLong() + index).toString().padStart(m.groupValues[1].toIntOrNull() ?: 1, '0')
            }
            numbered.replace("{name}", replaced).replace("{ext}", extension).also {
                require(it.isNotBlank() && it != "." && it != ".." && it.none { c -> c == '/' || c == '\\' || c == '\u0000' })
            }
        }
    }
}
