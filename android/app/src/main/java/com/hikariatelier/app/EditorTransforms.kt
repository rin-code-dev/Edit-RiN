package com.hikariatelier.app

private const val FORMAT_INDENT = "  "

/**
 * Reindent ordinary code without rewriting literal contents. This is deliberately
 * conservative rather than a JavaScript parser: templates, block comments and
 * slash expressions are left unchanged because reflowing them can change a work.
 */
internal fun formatJavaScript(source: String): String {
    data class Line(val text: String, val leadingClosers: Int, val balance: Int)

    val lines = mutableListOf<Line>()
    val delimiters = ArrayDeque<Char>()
    for (rawLine in source.lines()) {
        val trimmed = rawLine.trim()
        var balance = 0
        var leadingClosers = 0
        var onlyClosersSoFar = true
        var quote: Char? = null
        var index = 0
        while (index < trimmed.length) {
            val character = trimmed[index]
            if (quote != null) {
                when (character) {
                    '\\' -> {
                        // A continued string must retain its original indentation.
                        if (index + 1 == trimmed.length) return source
                        index += 2
                        continue
                    }
                    quote -> quote = null
                }
            } else {
                when (character) {
                    '\'', '"' -> {
                        quote = character
                        onlyClosersSoFar = false
                    }
                    '`' -> return source
                    '/' -> {
                        if (trimmed.getOrNull(index + 1) == '/') break
                        // Distinguishing a regexp literal from division requires
                        // parsing the preceding expression. Preserve both safely.
                        return source
                    }
                    '{', '[', '(' -> {
                        delimiters.addLast(character)
                        balance++
                        onlyClosersSoFar = false
                    }
                    '}', ']', ')' -> {
                        val expected = when (character) {
                            '}' -> '{'
                            ']' -> '['
                            else -> '('
                        }
                        if (delimiters.removeLastOrNull() != expected) return source
                        balance--
                        if (onlyClosersSoFar) leadingClosers++
                    }
                    else -> if (!character.isWhitespace()) onlyClosersSoFar = false
                }
            }
            index++
        }
        if (quote != null) return source
        lines += Line(trimmed, leadingClosers, balance)
    }
    // Avoid rewriting incomplete code while the user is still editing it.
    if (delimiters.isNotEmpty()) return source

    var depth = 0
    var previousBlank = false
    val result = ArrayList<String>(lines.size)
    for (line in lines) {
        if (line.text.isEmpty()) {
            if (!previousBlank) result += ""
            previousBlank = true
        } else {
            previousBlank = false
            result += FORMAT_INDENT.repeat((depth - line.leadingClosers).coerceAtLeast(0)) + line.text
            depth = (depth + line.balance).coerceAtLeast(0)
        }
    }
    val newline = if (source.contains("\r\n")) "\r\n" else "\n"
    return result.joinToString(newline)
}
