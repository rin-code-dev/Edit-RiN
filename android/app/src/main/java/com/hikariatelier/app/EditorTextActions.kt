package com.hikariatelier.app

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

internal const val EDITOR_INDENT = "  "

internal fun applyAutomaticIndent(
    previous: TextFieldValue,
    changed: TextFieldValue
): TextFieldValue {
    if (
        !previous.selection.collapsed ||
        changed.text.length != previous.text.length + 1 ||
        changed.selection.start != previous.selection.start + 1
    ) return changed

    val insertedAt = previous.selection.start
    if (
        insertedAt !in changed.text.indices ||
        changed.text[insertedAt] != '\n' ||
        changed.text.removeRange(insertedAt, insertedAt + 1) != previous.text
    ) return changed

    val before = previous.text.substring(0, insertedAt)
    val currentLine = before.substringAfterLast('\n')
    val baseIndent = currentLine.takeWhile { it == ' ' || it == '\t' }
    val opener = before.lastOrNull()
    val closer = previous.text.getOrNull(insertedAt)
    val matchingPair =
        (opener == '{' && closer == '}') ||
            (opener == '[' && closer == ']') ||
            (opener == '(' && closer == ')')
    val deeperIndent = if (opener == '{' || opener == '[' || opener == '(') {
        EDITOR_INDENT
    } else {
        ""
    }
    val insertion = if (matchingPair) {
        "\n$baseIndent$deeperIndent\n$baseIndent"
    } else {
        "\n$baseIndent$deeperIndent"
    }
    val newText = previous.text.replaceRange(insertedAt, insertedAt, insertion)
    val cursor = insertedAt + 1 + baseIndent.length + deeperIndent.length
    return TextFieldValue(newText, TextRange(cursor))
}

internal fun insertAtSelection(
    value: TextFieldValue,
    insertion: String,
    closing: String = ""
): TextFieldValue {
    val start = value.selection.min
    val end = value.selection.max
    val selected = if (closing.isNotEmpty()) value.text.substring(start, end) else ""
    val replacement = insertion + selected + closing
    val cursor = if (start == end && closing.isNotEmpty()) {
        start + insertion.length
    } else {
        start + replacement.length
    }
    return TextFieldValue(
        value.text.replaceRange(start, end, replacement),
        TextRange(cursor)
    )
}

internal fun moveCursorVertically(
    value: TextFieldValue,
    direction: Int
): TextFieldValue {
    val cursor = if (direction < 0) value.selection.min else value.selection.max
    val currentStart = if (cursor == 0) {
        0
    } else {
        value.text.lastIndexOf('\n', cursor - 1) + 1
    }
    val column = cursor - currentStart
    val targetStart: Int
    val targetEnd: Int
    if (direction < 0) {
        if (currentStart == 0) return value.copy(selection = TextRange(0))
        targetEnd = currentStart - 1
        targetStart = if (targetEnd == 0) {
            0
        } else {
            value.text.lastIndexOf('\n', targetEnd - 1) + 1
        }
    } else {
        val currentEnd = value.text.indexOf('\n', cursor).let {
            if (it < 0) value.text.length else it
        }
        if (currentEnd == value.text.length) {
            return value.copy(selection = TextRange(value.text.length))
        }
        targetStart = currentEnd + 1
        targetEnd = value.text.indexOf('\n', targetStart).let {
            if (it < 0) value.text.length else it
        }
    }
    return value.copy(
        selection = TextRange((targetStart + column).coerceAtMost(targetEnd))
    )
}

internal fun changeLineIndent(
    value: TextFieldValue,
    addIndent: Boolean
): TextFieldValue {
    if (addIndent && value.selection.collapsed) {
        return insertAtSelection(value, EDITOR_INDENT)
    }
    val firstLineStart = if (value.selection.min == 0) {
        0
    } else {
        value.text.lastIndexOf('\n', value.selection.min - 1) + 1
    }
    val starts = mutableListOf(firstLineStart)
    var newline = value.text.indexOf('\n', firstLineStart)
    while (newline >= 0 && newline + 1 < value.selection.max) {
        starts += newline + 1
        newline = value.text.indexOf('\n', newline + 1)
    }
    val builder = StringBuilder(value.text)
    var newStart = value.selection.min
    var newEnd = value.selection.max
    starts.asReversed().forEach { start ->
        if (addIndent) {
            builder.insert(start, EDITOR_INDENT)
            if (start <= newStart) newStart += EDITOR_INDENT.length
            if (start <= newEnd) newEnd += EDITOR_INDENT.length
        } else {
            val removeCount = when {
                builder.substring(start).startsWith("\t") -> 1
                builder.substring(start).startsWith(EDITOR_INDENT) -> EDITOR_INDENT.length
                builder.getOrNull(start) == ' ' -> 1
                else -> 0
            }
            if (removeCount > 0) {
                builder.delete(start, start + removeCount)
                if (start < newStart) newStart = (newStart - removeCount).coerceAtLeast(start)
                if (start < newEnd) newEnd = (newEnd - removeCount).coerceAtLeast(start)
            }
        }
    }
    return TextFieldValue(
        builder.toString(),
        TextRange(newStart, newEnd)
    )
}
