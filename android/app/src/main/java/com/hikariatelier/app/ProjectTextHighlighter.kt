package com.hikariatelier.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

internal enum class ProjectTextSyntax { JAVASCRIPT, HTML, CSS, JSON, SHADER, PLAIN }

internal fun projectTextSyntax(file: String): ProjectTextSyntax = when (file.substringAfterLast('.', "").lowercase()) {
    "js", "mjs", "cjs" -> ProjectTextSyntax.JAVASCRIPT
    "html", "htm", "svg", "xml" -> ProjectTextSyntax.HTML
    "css" -> ProjectTextSyntax.CSS
    "json" -> ProjectTextSyntax.JSON
    "frag", "vert", "glsl" -> ProjectTextSyntax.SHADER
    else -> ProjectTextSyntax.PLAIN
}

internal fun projectFileHighlighter(file: String, dark: Boolean, errors: Set<Int>): VisualTransformation =
    when (val syntax = projectTextSyntax(file)) {
        ProjectTextSyntax.JAVASCRIPT, ProjectTextSyntax.SHADER -> JavaScriptHighlighter(dark, errors)
        else -> ProjectTextHighlighter(syntax, dark, errors)
    }

/** Highlights text without rewriting it or applying JavaScript tokens to markup and styles. */
internal class ProjectTextHighlighter(
    private val syntax: ProjectTextSyntax,
    dark: Boolean,
    private val errorLines: Set<Int> = emptySet()
) : VisualTransformation {
    private val keyword = SpanStyle(color = if (dark) Color(0xFFC792EA) else Color(0xFF7B1FA2))
    private val string = SpanStyle(color = if (dark) Color(0xFFC3E88D) else Color(0xFF2E7D32))
    private val number = SpanStyle(color = if (dark) Color(0xFFF78C6C) else Color(0xFFD84315))
    private val comment = SpanStyle(color = if (dark) Color(0xFF78909C) else Color(0xFF607D8B))
    private val error = SpanStyle(background = if (dark) Color(0x334F1118) else Color(0x22B3261E))

    override fun filter(text: AnnotatedString): TransformedText {
        val source = text.text
        val result = AnnotatedString.Builder(text)
        var index = 0
        var inTag = false
        while (index < source.length && syntax != ProjectTextSyntax.PLAIN) {
            val start = index
            val character = source[index]
            val style = when {
                syntax == ProjectTextSyntax.HTML && source.startsWith("<!--", index) -> {
                    index = source.indexOf("-->", index + 4).let { if (it < 0) source.length else it + 3 }
                    comment
                }
                syntax == ProjectTextSyntax.CSS && source.startsWith("/*", index) -> {
                    index = source.indexOf("*/", index + 2).let { if (it < 0) source.length else it + 2 }
                    comment
                }
                syntax == ProjectTextSyntax.HTML && character == '<' -> {
                    index++
                    if (source.getOrNull(index) in listOf('/', '!', '?')) index++
                    while (index < source.length && (source[index].isLetterOrDigit() || source[index] in "_:-")) index++
                    inTag = true
                    keyword
                }
                syntax == ProjectTextSyntax.HTML && character == '>' -> {
                    index++
                    inTag = false
                    keyword
                }
                (syntax != ProjectTextSyntax.HTML || inTag) && (character == '"' || character == '\'') -> {
                    index++
                    while (index < source.length) {
                        val current = source[index++]
                        if (current == '\\' && syntax != ProjectTextSyntax.HTML) index = (index + 1).coerceAtMost(source.length)
                        else if (current == character) break
                    }
                    string
                }
                syntax == ProjectTextSyntax.CSS && character == '#' -> {
                    index++
                    while (index < source.length && (source[index].isLetterOrDigit() || source[index] in "_-")) index++
                    number
                }
                syntax != ProjectTextSyntax.HTML && (character.isDigit() || character == '-' && source.getOrNull(index + 1)?.isDigit() == true) -> {
                    index++
                    while (index < source.length && (source[index].isLetterOrDigit() || source[index] in ".%+-")) index++
                    number
                }
                character.isLetter() || character in "_@-" -> {
                    index++
                    while (index < source.length && (source[index].isLetterOrDigit() || source[index] in "_-" ||
                        syntax == ProjectTextSyntax.HTML && source[index] == ':')) index++
                    val word = source.substring(start, index)
                    var afterWord = index
                    while (source.getOrNull(afterWord)?.isWhitespace() == true) afterWord++
                    when {
                        syntax == ProjectTextSyntax.HTML && inTag -> keyword
                        syntax == ProjectTextSyntax.JSON && word in setOf("true", "false", "null") -> keyword
                        syntax == ProjectTextSyntax.CSS && (word.startsWith('@') || source.getOrNull(afterWord) == ':') -> keyword
                        else -> null
                    }
                }
                else -> { index++; null }
            }
            if (style != null) result.addStyle(style, start, index)
        }
        var line = 1
        var start = 0
        while (errorLines.isNotEmpty() && start < source.length) {
            val end = source.indexOf('\n', start).let { if (it < 0) source.length else it }
            if (line in errorLines) result.addStyle(error, start, maxOf(start + 1, end).coerceAtMost(source.length))
            start = end + 1
            line++
        }
        return TransformedText(result.toAnnotatedString(), OffsetMapping.Identity)
    }
}
