package com.hikariatelier.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

class JavaScriptHighlighter(
    darkTheme: Boolean,
    private val errorLines: Set<Int> = emptySet()
) : VisualTransformation {
    private val keywordStyle = SpanStyle(
        color = if (darkTheme) Color(0xFFC792EA) else Color(0xFF7B1FA2)
    )
    private val functionStyle = SpanStyle(
        color = if (darkTheme) Color(0xFF82AAFF) else Color(0xFF1565C0)
    )
    private val stringStyle = SpanStyle(
        color = if (darkTheme) Color(0xFFC3E88D) else Color(0xFF2E7D32)
    )
    private val numberStyle = SpanStyle(
        color = if (darkTheme) Color(0xFFF78C6C) else Color(0xFFD84315)
    )
    private val commentStyle = SpanStyle(
        color = if (darkTheme) Color(0xFF78909C) else Color(0xFF607D8B)
    )
    private val errorLineStyle = SpanStyle(
        background = if (darkTheme) Color(0x334F1118) else Color(0x22B3261E)
    )
    private var cachedInput: AnnotatedString? = null
    private var cachedResult = AnnotatedString("")

    override fun filter(text: AnnotatedString): TransformedText {
        val source = text.text

        if (text == cachedInput) {
            return TransformedText(cachedResult, OffsetMapping.Identity)
        }

        if (source.isEmpty()) {
            cachedInput = text
            cachedResult = text
            return TransformedText(text, OffsetMapping.Identity)
        }

        val builder = AnnotatedString.Builder(text)
        var index = 0
        // Scan iteratively: recursive regex alternatives can overflow on a long
        // string, and an unfinished token is normal while the user is typing.
        while (index < source.length) {
            val start = index
            val character = source[index]
            val next = source.getOrNull(index + 1)
            val style = when {
                character == '/' && next == '/' -> {
                    index += 2
                    while (index < source.length && source[index] != '\n' && source[index] != '\r') index++
                    commentStyle
                }
                character == '/' && next == '*' -> {
                    val closing = source.indexOf("*/", index + 2)
                    index = if (closing < 0) source.length else closing + 2
                    commentStyle
                }
                character == '\'' || character == '"' || character == '`' -> {
                    index++
                    while (index < source.length) {
                        val current = source[index]
                        if (current == '\\') {
                            val escapedLength = if (
                                source.getOrNull(index + 1) == '\r' &&
                                source.getOrNull(index + 2) == '\n'
                            ) 3 else 2
                            index = (index + escapedLength).coerceAtMost(source.length)
                        } else if (current == character) {
                            index++
                            break
                        } else if (character != '`' && (current == '\n' || current == '\r')) {
                            break
                        } else {
                            index++
                        }
                    }
                    stringStyle
                }
                isIdentifierStart(character) -> {
                    index++
                    while (index < source.length && isIdentifierPart(source[index])) index++
                    when (source.substring(start, index)) {
                        in KEYWORDS -> keywordStyle
                        in P5_FUNCTIONS -> functionStyle
                        else -> null
                    }
                }
                character in '0'..'9' || (character == '.' && next in '0'..'9') -> {
                    index = numberEnd(source, start)
                    numberStyle
                }
                else -> {
                    index++
                    null
                }
            }
            if (style != null) builder.addStyle(style, start, index)
        }

        applyErrorStyles(builder, source)

        cachedInput = text
        cachedResult = builder.toAnnotatedString()

        return TransformedText(
            cachedResult,
            OffsetMapping.Identity
        )
    }

    private fun applyErrorStyles(
        builder: AnnotatedString.Builder,
        source: String
    ) {
        if (errorLines.isEmpty()) return
        var line = 1
        var start = 0
        while (start <= source.length) {
            val end = source.indexOf('\n', start).let {
                if (it < 0) source.length else it
            }
            if (line in errorLines) {
                val styledEnd = if (end > start) end else minOf(source.length, start + 1)
                if (styledEnd > start) {
                    builder.addStyle(
                        errorLineStyle,
                        start,
                        styledEnd
                    )
                }
            }
            if (end == source.length) break
            start = end + 1
            line += 1
        }
    }

    private companion object {
        fun isIdentifierStart(character: Char): Boolean =
            character.isLetter() || character == '_' || character == '$'

        fun isIdentifierPart(character: Char): Boolean =
            isIdentifierStart(character) || character.isDigit() ||
                character == '\u200C' || character == '\u200D' ||
                Character.getType(character) == Character.NON_SPACING_MARK.toInt() ||
                Character.getType(character) == Character.COMBINING_SPACING_MARK.toInt()

        fun numberEnd(source: String, start: Int): Int {
            var index = start
            if (source[start] == '0') {
                val radix = when (source.getOrNull(start + 1)?.lowercaseChar()) {
                    'x' -> 16
                    'b' -> 2
                    'o' -> 8
                    else -> null
                }
                if (radix != null) {
                    index += 2
                    while (index < source.length &&
                        (source[index] == '_' || source[index].digitToIntOrNull(radix) != null)
                    ) index++
                    return if (source.getOrNull(index) == 'n') index + 1 else index
                }
            }
            while (index < source.length && (source[index] in '0'..'9' || source[index] == '_')) index++
            if (source.getOrNull(index) == '.') {
                index++
                while (index < source.length && (source[index] in '0'..'9' || source[index] == '_')) index++
            }
            if (source.getOrNull(index) == 'e' || source.getOrNull(index) == 'E') {
                var exponent = index + 1
                if (source.getOrNull(exponent) == '+' || source.getOrNull(exponent) == '-') exponent++
                if (source.getOrNull(exponent) in '0'..'9') {
                    index = exponent + 1
                    while (index < source.length && (source[index] in '0'..'9' || source[index] == '_')) index++
                }
            }
            return if (source.getOrNull(index) == 'n') index + 1 else index
        }

        val KEYWORDS = setOf(
            "function", "let", "var", "const", "if", "else", "for", "while",
            "return", "true", "false", "new", "this", "class", "extends", "async",
            "await", "switch", "case", "break", "continue", "try", "catch", "finally",
            "throw", "typeof", "instanceof", "null", "undefined", "do", "in",
            "of", "delete", "void", "yield", "import", "export", "default", "super"
        )

        val P5_FUNCTIONS = setOf(
            "setup", "draw", "createCanvas", "resizeCanvas", "background", "fill",
            "noStroke", "circle", "rect", "line", "ellipse", "triangle", "stroke",
            "strokeWeight", "noFill", "translate", "rotate", "push", "pop", "sin",
            "cos", "tan", "random", "noise", "map", "dist", "floor", "ceil", "min",
            "max", "abs", "colorMode", "blendMode", "strokeCap", "touchStarted",
            "touchMoved", "touchEnded", "mousePressed", "mouseDragged", "mouseReleased"
        )
    }
}
