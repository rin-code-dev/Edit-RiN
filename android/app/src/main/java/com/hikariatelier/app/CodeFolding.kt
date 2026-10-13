package com.hikariatelier.app

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText

internal data class CodeFold(val open: Int, val close: Int)
internal data class CodeFoldState(
    val source: String,
    val collapsed: Set<Int>,
    val regions: List<CodeFold>? = null
)

/** Shared lexical scanner helpers used across code folding and literal path analysis. */
internal fun skipJsLineComment(source: String, start: Int): Int =
    source.indexOf('\n', start + 2).let { if (it < 0) source.length else it }

internal fun skipJsBlockComment(source: String, start: Int, closing: String = "*/"): Int =
    source.indexOf(closing, start + 2).let { if (it < 0) source.length else it + closing.length }

internal fun skipJsQuotedLiteral(source: String, start: Int, stopAtNewline: Boolean = false): Int {
    val quote = source[start]
    var index = start + 1
    while (index < source.length) {
        when (val c = source[index]) {
            '\\' -> index = (index + 2).coerceAtMost(source.length)
            quote -> return index + 1
            '\n', '\r' -> if (stopAtNewline) return index + 1 else index++
            else -> index++
        }
    }
    return source.length
}

internal fun skipJsRegexLiteral(source: String, start: Int): Int {
    var index = start + 1
    var characterClass = false
    while (index < source.length) {
        when (source[index]) {
            '\\' -> index = (index + 2).coerceAtMost(source.length)
            '[' -> { characterClass = true; index++ }
            ']' -> { characterClass = false; index++ }
            '/' -> if (!characterClass) return index + 1 else index++
            '\n', '\r' -> return index
            else -> index++
        }
    }
    return index
}

/** Braces in comments, strings, templates and regular expressions are not block delimiters. */
internal fun codeFolds(source: String): List<CodeFold> = FoldScanner(source).scan()

/** Keep only folds whose delimiters are untouched while the next scan is pending. */
internal fun rebaseCodeFoldRegions(before: String, folds: List<CodeFold>, after: String): List<CodeFold> {
    if (before == after) return folds
    var prefix = 0
    while (prefix < minOf(before.length, after.length) && before[prefix] == after[prefix]) prefix++
    var suffix = 0
    while (suffix < minOf(before.length, after.length) - prefix &&
        before[before.lastIndex - suffix] == after[after.lastIndex - suffix]) suffix++
    val oldEnd = before.length - suffix
    val delta = after.length - before.length
    return folds.mapNotNull { fold ->
        when {
            fold.close < prefix -> fold
            fold.open >= oldEnd -> CodeFold(fold.open + delta, fold.close + delta)
            else -> null
        }
    }
}

private val FOLD_CONTROL_WORDS = setOf("if", "while", "for", "with", "switch", "catch")
private val FOLD_EXPRESSION_WORDS = setOf("return", "throw", "case", "delete", "void", "typeof", "yield", "await", "in", "of", "else", "do")

private class FoldScanner(private val source: String) {
    private var index = 0
    private var lastLineBreak = -1
    private var nextLineBreak = source.indexOf('\n')
    private val folds = mutableListOf<CodeFold>()
    fun scan(): List<CodeFold> {
        code(false, 0)
        return folds.sortedBy { it.open }
    }

    private fun template(depth: Int) {
        index++
        while (index < source.length) {
            val c = source[index++]
            when {
                c == '\\' -> index = (index + 1).coerceAtMost(source.length)
                c == '`' -> return
                c == '$' && source.getOrNull(index) == '{' -> {
                    index++
                    code(true, depth + 1)
                }
            }
        }
    }

    private fun code(interpolation: Boolean, depth: Int) {
        if (depth > 64) { index = source.length; return }
        val braces = mutableListOf<Int>()
        val parens = mutableListOf<Boolean>()
        var expectsExpression = true
        var controlParen = false
        while (index < source.length) {
            while (nextLineBreak >= 0 && nextLineBreak < index) {
                lastLineBreak = nextLineBreak
                nextLineBreak = source.indexOf('\n', nextLineBreak + 1)
            }
            val c = source[index]
            val next = source.getOrNull(index + 1)
            when {
                c.isWhitespace() -> index++
                c == '/' && next == '/' -> index = skipJsLineComment(source, index)
                c == '/' && next == '*' -> index = skipJsBlockComment(source, index)
                c == '\'' || c == '"' -> { index = skipJsQuotedLiteral(source, index, stopAtNewline = true); expectsExpression = false }
                c == '`' -> { template(depth); expectsExpression = false }
                c == '/' && expectsExpression -> { index = skipJsRegexLiteral(source, index); expectsExpression = false }
                c.isLetter() || c == '_' || c == '$' -> {
                    val start = index++
                    while (index < source.length && (source[index].isLetterOrDigit() || source[index] in "_$")) index++
                    val word = source.substring(start, index)
                    controlParen = word in FOLD_CONTROL_WORDS
                    expectsExpression = word in FOLD_EXPRESSION_WORDS
                }
                c == '(' -> { parens.add(controlParen); controlParen = false; index++; expectsExpression = true }
                c == ')' -> { expectsExpression = parens.removeLastOrNull() ?: false; index++ }
                c == '{' -> { braces.add(index++); expectsExpression = true }
                c == '}' -> {
                    val open = braces.removeLastOrNull()
                    if (open == null && interpolation) { index++; return }
                    if (open != null && !interpolation && lastLineBreak > open) {
                        folds.add(CodeFold(open, index))
                    }
                    index++; expectsExpression = true
                }
                c == ']' || c.isDigit() -> { index++; expectsExpression = false }
                (c == '+' || c == '-') && next == c -> { index += 2 }
                else -> { index++; expectsExpression = c != '.' }
            }
        }
    }
}

/** Preserve unaffected folds across an edit; open blocks whose contents were edited. */
internal fun rebasedFolds(state: CodeFoldState?, source: String): Set<Int> {
    if (state == null || state.collapsed.isEmpty()) return emptySet()
    if (state.source == source) return state.collapsed
    var prefix = 0
    while (prefix < minOf(state.source.length, source.length) && state.source[prefix] == source[prefix]) prefix++
    var suffix = 0
    while (suffix < minOf(state.source.length, source.length) - prefix &&
        state.source[state.source.lastIndex - suffix] == source[source.lastIndex - suffix]) suffix++
    val oldEnd = state.source.length - suffix
    val delta = source.length - state.source.length
    return (state.regions ?: codeFolds(state.source)).filter { it.open in state.collapsed }.mapNotNull { fold ->
        when {
            fold.close < prefix -> fold.open
            fold.open >= oldEnd -> fold.open + delta
            else -> null
        }
    }.toSet()
}

internal class FoldProjection(val source: String, folds: List<CodeFold>, collapsed: Set<Int>) : OffsetMapping {
    val hidden: List<CodeFold> = buildList {
        for (fold in folds) {
            if (fold.open in collapsed && (isEmpty() || fold.open > last().close)) add(fold)
        }
    }
    private val marker = " … "
    private val removedThrough = IntArray(hidden.size)
    private val displayStarts = IntArray(hidden.size)
    init {
        var removed = 0
        hidden.forEachIndexed { index, fold ->
            displayStarts[index] = fold.open + 1 - removed
            removed += fold.close - fold.open - 1 - marker.length
            removedThrough[index] = removed
        }
    }
    fun isHidden(offset: Int): Boolean {
        val index = hidden.binarySearch { it.open.compareTo(offset) }
        val preceding = if (index >= 0) index - 1 else -index - 2
        return preceding >= 0 && offset < hidden[preceding].close
    }
    private var cachedInput: AnnotatedString? = null
    private var cachedInputHash: Int = 0
    private var cachedResult: TransformedText? = null
    fun transform(text: AnnotatedString): TransformedText {
        if (text.text != source || hidden.isEmpty()) return TransformedText(text, OffsetMapping.Identity)
        if (text === cachedInput) return cachedResult!!
        val inputHash = text.hashCode()
        if (inputHash == cachedInputHash && text == cachedInput) return cachedResult!!
        val output = AnnotatedString.Builder()
        var from = 0
        for (fold in hidden) {
            output.append(text.subSequence(from, fold.open + 1))
            output.append(marker)
            from = fold.close
        }
        output.append(text.subSequence(from, text.length))
        return TransformedText(output.toAnnotatedString(), this).also {
            cachedInput = text
            cachedInputHash = inputHash
            cachedResult = it
        }
    }
    override fun originalToTransformed(offset: Int): Int {
        val found = hidden.binarySearch { (it.open + 1).compareTo(offset) }
        val index = if (found >= 0) found else -found - 2
        if (index < 0) return offset
        if (offset == hidden[index].open + 1 || offset < hidden[index].close) return displayStarts[index]
        return offset - removedThrough[index]
    }
    override fun transformedToOriginal(offset: Int): Int {
        val found = displayStarts.binarySearch(offset)
        val index = if (found >= 0) found else -found - 2
        if (index < 0) return offset
        if (offset < displayStarts[index] + marker.length) return hidden[index].open + 1
        return offset + removedThrough[index]
    }
}

/** A collapsed caret must not backspace through code that the user cannot see. */
internal fun deletesFoldedCode(before: String, after: String, hidden: List<CodeFold>): Boolean {
    if (hidden.isEmpty() || before == after) return false
    var prefix = 0
    while (prefix < minOf(before.length, after.length) && before[prefix] == after[prefix]) prefix++
    var suffix = 0
    while (suffix < minOf(before.length, after.length) - prefix &&
        before[before.lastIndex - suffix] == after[after.lastIndex - suffix]) suffix++
    val removedEnd = before.length - suffix
    return hidden.any { prefix < it.close && removedEnd > it.open + 1 && removedEnd > prefix }
}
