package com.hikariatelier.app

private const val FORMAT_INDENT = "  "

internal sealed interface FormatResult {
    data class Success(val code: String) : FormatResult
    object UnbalancedBrackets : FormatResult
    object UnfinishedString : FormatResult
}

private val REGEX_PREFIX_KEYWORDS = setOf(
    "return", "case", "throw", "yield", "await",
    "typeof", "void", "delete", "instanceof", "in", "do"
)

/**
 * Validate delimiters and reindent ordinary code, including division expressions.
 * This is not a JavaScript parser: preserve the whole source when it contains
 * templates, block comments, regex literals or continued strings. Their exact
 * whitespace and surrounding expression context must not change.
 */
internal fun formatJavaScriptDetailed(source: String): FormatResult {
    data class Line(val text: String, val leadingClosers: Int, val balance: Int)
    val lines = mutableListOf<Line>()
    val delimiters = ArrayDeque<Char>()
    var quote: Char? = null
    var blockComment = false
    var templateText = false
    var openTemplates = 0
    var preserveSource = false
    var lastChar: Char? = null
    var lastToken = ""

    for (rawLine in source.lines()) {
        var index = 0
        var balance = 0
        var leadingClosers = 0
        var onlyClosers = true
        var continuedString = false
        while (index < rawLine.length) {
            val char = rawLine[index]
            if (blockComment) {
                val end = rawLine.indexOf("*/", index)
                if (end < 0) break
                blockComment = false
                index = end + 2
                continue
            }
            if (quote != null) {
                if (char == '\\') {
                    if (index + 1 == rawLine.length) continuedString = true
                    index += 2
                    continue
                }
                if (char == quote) {
                    quote = null
                    lastChar = char
                    lastToken = "literal"
                }
                index++
                continue
            }
            if (templateText) {
                when {
                    char == '\\' -> index += 2
                    char == '`' -> {
                        openTemplates--
                        templateText = false
                        lastChar = '`'
                        lastToken = "literal"
                        index++
                    }
                    char == '$' && rawLine.getOrNull(index + 1) == '{' -> {
                        // A distinct delimiter returns to template text after interpolation.
                        delimiters.addLast('$')
                        templateText = false
                        lastChar = '{'
                        lastToken = "{"
                        index += 2
                    }
                    else -> index++
                }
                continue
            }
            if (char.isWhitespace()) { index++; continue }
            if (char == '/' && rawLine.getOrNull(index + 1) == '/') break
            if (char == '/' && rawLine.getOrNull(index + 1) == '*') {
                preserveSource = true
                blockComment = true
                index += 2
                continue
            }
            if (char == '\'' || char == '"') {
                quote = char
                onlyClosers = false
                index++
                continue
            }
            if (char == '`') {
                preserveSource = true
                openTemplates++
                templateText = true
                onlyClosers = false
                index++
                continue
            }
            if (char == '/') {
                if (isRegexStart(lastChar, lastToken)) {
                    val end = javaScriptRegexLiteralEnd(rawLine, index)
                        ?: return FormatResult.UnbalancedBrackets
                    preserveSource = true
                    index = end
                    lastChar = 'v'
                    lastToken = "literal"
                } else {
                    lastChar = '/'
                    lastToken = "/"
                    index++
                }
                onlyClosers = false
                continue
            }
            if (char == '{' || char == '[' || char == '(') {
                delimiters.addLast(char)
                balance++
                onlyClosers = false
            } else if (char == '}' || char == ']' || char == ')') {
                val expected = when (char) { '}' -> '{'; ']' -> '['; else -> '(' }
                val opener = delimiters.removeLastOrNull()
                if (char == '}' && opener == '$') {
                    templateText = true
                } else {
                    if (opener != expected) return FormatResult.UnbalancedBrackets
                    balance--
                    if (onlyClosers) leadingClosers++
                }
            } else if (char.isLetterOrDigit() || char == '_' || char == '$') {
                val start = index++
                while (index < rawLine.length && (rawLine[index].isLetterOrDigit() || rawLine[index] == '_' || rawLine[index] == '$')) index++
                lastToken = rawLine.substring(start, index)
                lastChar = rawLine[index - 1]
                onlyClosers = false
                continue
            } else {
                onlyClosers = false
                if ((char == '+' || char == '-') && rawLine.getOrNull(index + 1) == char) {
                    // Postfix operators can be followed by division, not a regex literal.
                    lastChar = 'v'
                    lastToken = "postfix"
                    index += 2
                    continue
                }
            }
            lastChar = char
            lastToken = char.toString()
            index++
        }
        if (quote != null) {
            if (!continuedString) return FormatResult.UnfinishedString
            preserveSource = true
        }
        lines += Line(rawLine.trim(), leadingClosers, balance)
    }
    if (quote != null || openTemplates != 0) return FormatResult.UnfinishedString
    if (blockComment || delimiters.isNotEmpty()) return FormatResult.UnbalancedBrackets
    if (preserveSource) return FormatResult.Success(source)

    var depth = 0
    val result = ArrayList<String>(lines.size)
    for (line in lines) {
        if (line.text.isEmpty()) {
            result += ""
        } else {
            result += FORMAT_INDENT.repeat((depth - line.leadingClosers).coerceAtLeast(0)) + line.text
            depth = (depth + line.balance).coerceAtLeast(0)
        }
    }
    val newline = if (source.contains("\r\n")) "\r\n" else "\n"
    return FormatResult.Success(result.joinToString(newline))
}

internal fun formatJavaScript(source: String): String = when (val result = formatJavaScriptDetailed(source)) {
    is FormatResult.Success -> result.code
    else -> source
}

private fun isRegexStart(lastChar: Char?, lastToken: String): Boolean {
    if (lastChar == null || lastToken in REGEX_PREFIX_KEYWORDS) return true
    return lastChar in "([{,;:?=+-*%&|^!~<>"
}

/** Line count is retained; move the cursor with the changed leading indentation. */
internal fun formattedJavaScriptOffset(source: String, formatted: String, offset: Int): Int {
    val cursor = offset.coerceIn(0, source.length)
    val line = source.take(cursor).count { it == '\n' }
    val sourceStart = if (cursor == 0) 0 else source.lastIndexOf('\n', cursor - 1) + 1
    val oldLine = source.substring(sourceStart, source.indexOf('\n', sourceStart).takeIf { it >= 0 } ?: source.length).trimEnd('\r')
    var newStart = 0
    repeat(line) {
        val newline = formatted.indexOf('\n', newStart)
        if (newline < 0) return formatted.length
        newStart = newline + 1
    }
    val newLine = formatted.substring(newStart, formatted.indexOf('\n', newStart).takeIf { it >= 0 } ?: formatted.length).trimEnd('\r')
    val oldIndent = oldLine.takeWhile { it == ' ' || it == '\t' }.length
    val newIndent = newLine.takeWhile { it == ' ' || it == '\t' }.length
    return newStart + (cursor - sourceStart + newIndent - oldIndent).coerceIn(0, newLine.length)
}

/**
 * Minifies JavaScript code for #つぶやきProcessing.
 * 1. Safely removes single-line (//) and multi-line (/* * /) comments.
 * 2. Compresses indentation, line breaks, and unnecessary spaces around symbols.
 * 3. Preserves string literals ("...", '...', `...`) and regexes verbatim.
 * 4. Inserts semicolons between statements across lines when required to keep code functional.
 */
internal fun minifyJavaScriptForTweet(source: String): String {
    val out = StringBuilder(source.length)
    var i = 0
    val len = source.length
    var lastChar: Char? = null
    var lastToken = ""

    fun isWordChar(c: Char) = c.isLetterOrDigit() || c == '_' || c == '$'

    while (i < len) {
        val c = source[i]
        val next = source.getOrNull(i + 1)

        // 1. Line comment
        if (c == '/' && next == '/') {
            i += 2
            while (i < len && source[i] != '\n' && source[i] != '\r') {
                i++
            }
            continue
        }

        // 2. Block comment
        if (c == '/' && next == '*') {
            i += 2
            val end = source.indexOf("*/", i)
            if (end >= 0) {
                i = end + 2
            } else {
                i = len
            }
            continue
        }

        // 3. String literals ('...' or "...")
        if (c == '\'' || c == '"') {
            val quote = c
            val start = i
            i++
            while (i < len) {
                val sc = source[i]
                if (sc == '\\') {
                    i += 2
                } else if (sc == quote) {
                    i++
                    break
                } else {
                    i++
                }
            }
            val strLiteral = source.substring(start, i.coerceAtMost(len))
            out.append(strLiteral)
            lastChar = strLiteral.lastOrNull()
            lastToken = "literal"
            continue
        }

        // 4. Template literals (`...`)
        if (c == '`') {
            val start = i
            i++
            while (i < len) {
                val tc = source[i]
                if (tc == '\\') {
                    i += 2
                } else if (tc == '`') {
                    i++
                    break
                } else {
                    i++
                }
            }
            val tplLiteral = source.substring(start, i.coerceAtMost(len))
            out.append(tplLiteral)
            lastChar = tplLiteral.lastOrNull()
            lastToken = "literal"
            continue
        }

        // 5. Regex literal
        if (c == '/' && isRegexStart(lastChar, lastToken)) {
            val end = javaScriptRegexLiteralEnd(source, i)
            if (end != null) {
                val regexLiteral = source.substring(i, end)
                out.append(regexLiteral)
                i = end
                lastChar = 'v'
                lastToken = "literal"
                continue
            }
        }

        // 6. Whitespace and Newlines
        if (c.isWhitespace()) {
            var seenNewline = false
            while (i < len && source[i].isWhitespace()) {
                if (source[i] == '\n' || source[i] == '\r') seenNewline = true
                i++
            }
            if (i >= len) break

            val nextChar = source[i]

            if (seenNewline) {
                val prev = lastChar
                if (prev != null && prev != ';' && prev != '{' && prev != '}' && prev != '(' && prev != '[' &&
                    prev != ',' && prev != ':' && prev != '?' && prev != '=' && prev != '+' && prev != '-' &&
                    prev != '*' && prev != '/' && prev != '&' && prev != '|' && prev != '^' && prev != '!' &&
                    nextChar != ';' && nextChar != '}' && nextChar != ')' && nextChar != ']' && nextChar != ',' &&
                    nextChar != '.' && nextChar != ':' && nextChar != '?'
                ) {
                    out.append(';')
                    lastChar = ';'
                    lastToken = ";"
                } else if (prev != null && isWordChar(prev) && isWordChar(nextChar)) {
                    out.append(' ')
                    lastChar = ' '
                }
            } else {
                val prev = lastChar
                if (prev != null && isWordChar(prev) && isWordChar(nextChar)) {
                    out.append(' ')
                    lastChar = ' '
                } else if ((prev == '+' && nextChar == '+') || (prev == '-' && nextChar == '-')) {
                    out.append(' ')
                    lastChar = ' '
                }
            }
            continue
        }

        // 7. Words (identifiers, keywords, numbers)
        if (isWordChar(c)) {
            val start = i
            while (i < len && isWordChar(source[i])) {
                i++
            }
            val word = source.substring(start, i)
            if (lastChar != null && isWordChar(lastChar)) {
                out.append(' ')
            }
            out.append(word)
            lastChar = word.last()
            lastToken = word
            continue
        }

        // 8. Other operators and punctuation
        if ((lastChar == '+' && c == '+') || (lastChar == '-' && c == '-')) {
            out.append(' ')
        }
        out.append(c)
        lastChar = c
        lastToken = c.toString()
        i++
    }

    return out.toString().trim()
}
