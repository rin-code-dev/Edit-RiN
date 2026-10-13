package com.hikariatelier.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle

/** UI-thread cache. Keep untouched syntax spans while a fresh background parse is pending. */
internal class EditorHighlightCache {
    private var displayed: AnnotatedString? = null
    private var blocks = emptyList<AnnotatedString>()
    internal var reusedBlocks = 0
        private set

    fun highlight(input: AnnotatedString, completed: AnnotatedString? = null,
                  shared: EditorDocumentChange? = null): AnnotatedString {
        val previous = displayed
        if (completed != null && completed.text == input.text) {
            if (completed !== previous) blocks = highlightBlocks(completed)
            displayed = completed
            return completed
        }
        if (previous == null) {
            blocks = highlightBlocks(input); displayed = input
            return input
        }
        if (previous.text == input.text) return previous
        val change = shared?.takeIf { it.before == previous.text && it.after == input.text }?.range
            ?: editorTextChange(androidx.compose.ui.text.input.TextFieldValue(previous.text),
                androidx.compose.ui.text.input.TextFieldValue(input.text))
        reusedBlocks = 0
        val next = mutableListOf<AnnotatedString>()
        fun retain(start: Int, end: Int) {
            var offset = 0
            for (block in blocks) {
                val limit = offset + block.length
                if (offset >= end) break
                if (limit > start) {
                    if (offset >= start && limit <= end) { next.add(block); reusedBlocks++ }
                    else next.add(block.subSequence(maxOf(0, start - offset), minOf(block.length, end - offset)))
                }
                offset = limit
            }
        }
        retain(0, change.start)
        if (change.newEnd > change.start) next.addAll(highlightBlocks(input.subSequence(change.start, change.newEnd)))
        retain(change.oldEnd, previous.length)
        // Coalesce adjacent tiny edit fragments so prolonged typing cannot grow one block per keystroke.
        blocks = buildList {
            var small = AnnotatedString.Builder()
            fun flush() { if (small.length > 0) { add(small.toAnnotatedString()); small = AnnotatedString.Builder() } }
            for (block in next) {
                if (block.length >= 1024) { flush(); add(block) }
                else { small.append(block); if (small.length >= 1024) flush() }
            }
            flush()
        }
        return AnnotatedString.Builder().apply { blocks.forEach { append(it) } }.toAnnotatedString().also { displayed = it }
    }
}

/** Partition once; subsequent edits reuse unaffected text/style blocks rather than slicing all spans. */
private fun highlightBlocks(text: AnnotatedString): List<AnnotatedString> {
    if (text.length <= 2048) return listOf(text)
    val count = (text.length + 2047) / 2048
    val styles = List(count) { mutableListOf<AnnotatedString.Range<SpanStyle>>() }
    for (span in text.spanStyles) {
        if (span.start >= span.end) continue
        for (block in span.start / 2048..(span.end - 1) / 2048) {
            val start = block * 2048
            styles[block].add(AnnotatedString.Range(span.item,
                maxOf(span.start, start) - start, minOf(span.end, start + 2048) - start))
        }
    }
    return List(count) { index ->
        val start = index * 2048
        AnnotatedString(text.text.substring(start, minOf(start + 2048, text.length)), styles[index])
    }
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
