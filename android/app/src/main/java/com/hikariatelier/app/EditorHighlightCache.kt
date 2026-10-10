package com.hikariatelier.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle

/** UI-thread cache. Keep untouched syntax spans while a fresh background parse is pending. */
internal class EditorHighlightCache {
    private var displayed: AnnotatedString? = null

    fun highlight(input: AnnotatedString, completed: AnnotatedString? = null): AnnotatedString {
        val previous = displayed
        val next = when {
            completed != null && completed.text == input.text -> completed
            previous == null -> input
            previous.text == input.text -> previous
            else -> rebaseEditorHighlight(previous, input)
        }
        displayed = next
        return next
    }
}

private fun rebaseEditorHighlight(before: AnnotatedString, after: AnnotatedString): AnnotatedString {
    var prefix = 0
    val commonLength = minOf(before.length, after.length)
    while (prefix < commonLength && before[prefix] == after[prefix]) prefix++
    var suffix = 0
    while (suffix < commonLength - prefix &&
        before[before.length - suffix - 1] == after[after.length - suffix - 1]) suffix++
    return AnnotatedString.Builder().apply {
        if (prefix > 0) append(before.subSequence(0, prefix))
        if (after.length - suffix > prefix) append(after.subSequence(prefix, after.length - suffix))
        if (suffix > 0) append(before.subSequence(before.length - suffix, before.length))
    }.toAnnotatedString()
}

/** Diagnostics are transient and must never be carried over by the syntax cache. */
internal fun withEditorErrorLines(text: AnnotatedString, dark: Boolean, errors: Set<Int>): AnnotatedString {
    if (errors.isEmpty()) return text
    val style = SpanStyle(background = if (dark) Color(0x334F1118) else Color(0x22B3261E))
    return AnnotatedString.Builder(text).apply {
        var line = 1
        var start = 0
        val lastError = errors.maxOrNull() ?: 0
        while (start < text.length && line <= lastError) {
            val end = text.text.indexOf('\n', start).let { if (it < 0) text.length else it }
            if (line in errors) addStyle(style, start, maxOf(start + 1, end).coerceAtMost(text.length))
            start = end + 1
            line++
        }
    }.toAnnotatedString()
}

/** One entry per transformation configuration; focus/layout changes reuse the displayed text. */
internal class CachedEditorTransformation(
    private val transform: (AnnotatedString) -> androidx.compose.ui.text.input.TransformedText
) : androidx.compose.ui.text.input.VisualTransformation {
    private var input: AnnotatedString? = null
    private var output: androidx.compose.ui.text.input.TransformedText? = null

    override fun filter(text: AnnotatedString): androidx.compose.ui.text.input.TransformedText {
        if (text == input) return output!!
        return transform(text).also {
            input = text
            output = it
        }
    }
}
