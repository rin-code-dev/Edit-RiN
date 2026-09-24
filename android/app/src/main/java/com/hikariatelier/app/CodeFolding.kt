package com.hikariatelier.app

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText

internal data class CodeFold(val open: Int, val close: Int)
internal data class CodeFoldState(val source: String, val collapsed: Set<Int>)

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

    private fun quoted(quote: Char) {
        index++
        while (index < source.length) {
            val c = source[index++]
            if (c == '\\') index = (index + 1).coerceAtMost(source.length)
            else if (c == quote || c == '\n' || c == '\r') return
        }
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

    private fun regex() {
        index++
        var characterClass = false
        while (index < source.length) {
            when (source[index++]) {
                '\\' -> index = (index + 1).coerceAtMost(source.length)
                '[' -> characterClass = true
                ']' -> characterClass = false
                '/' -> if (!characterClass) return
                '\n', '\r' -> return
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
                c == '/' && next == '/' -> {
                    index += 2
                    while (index < source.length && source[index] != '\n') index++
                }
                c == '/' && next == '*' -> {
                    val end = source.indexOf("*/", index + 2)
                    index = if (end < 0) source.length else end + 2
                }
                c == '\'' || c == '"' -> { quoted(c); expectsExpression = false }
                c == '`' -> { template(depth); expectsExpression = false }
                c == '/' && expectsExpression -> { regex(); expectsExpression = false }
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
    return codeFolds(state.source).filter { it.open in state.collapsed }.mapNotNull { fold ->
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
    private var cachedInput: AnnotatedString? = null
    private var cachedResult: TransformedText? = null
    fun transform(text: AnnotatedString): TransformedText {
        if (text.text != source || hidden.isEmpty()) return TransformedText(text, OffsetMapping.Identity)
        if (text == cachedInput) return cachedResult!!
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
            cachedResult = it
        }
    }
    override fun originalToTransformed(offset: Int): Int {
        var removed = 0
        for (fold in hidden) {
            val start = fold.open + 1
            if (offset <= start) break
            if (offset < fold.close) return start - removed
            removed += fold.close - start - marker.length
        }
        return offset - removed
    }
    override fun transformedToOriginal(offset: Int): Int {
        var removed = 0
        for (fold in hidden) {
            val start = fold.open + 1 - removed
            if (offset <= start) break
            if (offset < start + marker.length) return fold.open + 1
            removed += fold.close - fold.open - 1 - marker.length
        }
        return offset + removed
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
