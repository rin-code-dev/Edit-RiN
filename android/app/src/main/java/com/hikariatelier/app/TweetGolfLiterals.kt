package com.hikariatelier.app

/** Keep authored literal text opaque throughout all golf passes, including whitespace removal. */
internal class TweetGolfLiterals(private val source: String) {
    private var prefix = "__RIN_GOLF_LITERAL_"
    private val literals = linkedMapOf<String, String>()

    init { while (source.contains(prefix)) prefix += "_" }

    fun protect(): String {
        val out = StringBuilder()
        var i = 0
        var previous: Char? = null
        var word = ""
        while (i < source.length) {
            val c = source[i]
            val next = source.getOrNull(i + 1)
            if (c == '/' && next == '/') {
                while (i < source.length && source[i] != '\n') i++
                out.append('\n')
                continue
            }
            if (c == '/' && next == '*') {
                val end = source.indexOf("*/", i + 2)
                val comment = source.substring(i, if (end < 0) source.length else end + 2)
                out.append(if ('\n' in comment) '\n' else ' ')
                i += comment.length
                continue
            }
            val regexStart = previous == null || previous in "([{,;:?=+-*%&|^!~<>" ||
                word in setOf("return", "throw", "case", "typeof", "void", "yield", "await")
            val end = when {
                c == '\'' || c == '"' || c == '`' -> quotedEnd(i, c)
                c == '/' && regexStart -> javaScriptRegexLiteralEnd(source, i)
                else -> null
            }
            if (end != null) {
                var n = literals.size
                var letters = ""
                do { letters += ('A'.code + n % 26).toChar(); n = n / 26 - 1 } while (n >= 0)
                val marker = "$prefix${letters}__"
                literals[marker] = source.substring(i, end)
                out.append(marker)
                i = end
                previous = 'v'; word = "literal"
            } else if (c.isLetterOrDigit() || c == '_' || c == '$') {
                val start = i++
                while (i < source.length && (source[i].isLetterOrDigit() || source[i] in "_$")) i++
                word = source.substring(start, i)
                out.append(word); previous = word.last()
            } else {
                out.append(c); i++
                if (!c.isWhitespace()) { previous = c; word = c.toString() }
            }
        }
        return out.toString()
    }

    private fun quotedEnd(start: Int, quote: Char): Int {
        var i = start + 1
        while (i < source.length) {
            when {
                source[i] == '\\' -> i = (i + 2).coerceAtMost(source.length)
                source[i] == quote -> return i + 1
                quote == '`' && source[i] == '$' && source.getOrNull(i + 1) == '{' -> {
                    i += 2
                    var depth = 1
                    while (i < source.length && depth > 0) {
                        when (source[i]) {
                            '\'', '"', '`' -> i = quotedEnd(i, source[i])
                            '{' -> { depth++; i++ }
                            '}' -> { depth--; i++ }
                            else -> i++
                        }
                    }
                }
                else -> i++
            }
        }
        return source.length
    }

    // Interpolations stay byte-for-byte intact, so their free identifiers must stay available.
    fun templateIdentifiers(): Set<String> = literals.values.filter { it.startsWith("`") }
        .flatMap { Regex("""[A-Za-z_$][\w$]*""").findAll(it).map { match -> match.value }.toList() }.toSet()

    fun restore(code: String): String {
        var result = code
        literals.forEach { (marker, literal) -> result = result.replace(marker, literal) }
        return result
    }
}
