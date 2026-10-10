package com.hikariatelier.app

import androidx.compose.ui.text.input.TextFieldValue

internal data class EditorTextChange(val start: Int, val oldEnd: Int, val newEnd: Int) {
    val delta: Int get() = newEnd - oldEnd
}

/** Use the IME's selection to locate ordinary edits; validate before trusting that hint. */
internal fun editorTextChange(before: TextFieldValue, after: TextFieldValue): EditorTextChange {
    val old = before.text
    val next = after.text
    val delta = next.length - old.length
    val start = minOf(before.selection.min, after.selection.min).coerceIn(0, minOf(old.length, next.length))
    val oldEnd = maxOf(before.selection.max, start - minOf(delta, 0)).coerceAtMost(old.length)
    val newEnd = oldEnd + delta
    if (newEnd in start..next.length &&
        old.regionMatches(0, next, 0, start) &&
        old.regionMatches(oldEnd, next, newEnd, old.length - oldEnd)) {
        return EditorTextChange(start, oldEnd, newEnd)
    }
    // Formatting, undo, external replacement and unusual IME edits may not follow the caret.
    var prefix = 0
    val common = minOf(old.length, next.length)
    while (prefix < common && old[prefix] == next[prefix]) prefix++
    var suffix = 0
    while (suffix < common - prefix && old[old.lastIndex - suffix] == next[next.lastIndex - suffix]) suffix++
    return EditorTextChange(prefix, old.length - suffix, next.length - suffix)
}

internal fun shiftedFoldRegions(folds: List<CodeFold>, change: EditorTextChange): List<CodeFold> =
    folds.mapNotNull { fold ->
        when {
            fold.close < change.start -> fold
            fold.open >= change.oldEnd -> CodeFold(fold.open + change.delta, fold.close + change.delta)
            else -> null // An edited block stays expanded until the background parser verifies it.
        }
    }

internal data class EditorFoldSnapshot(
    val regions: List<CodeFold>?,
    val previousSource: String?,
    val change: EditorTextChange?
)

/** Advance from the immediately preceding edit, rather than the last full parse. */
internal class EditorFoldAnalysis {
    private var previous: TextFieldValue? = null
    private var snapshot = EditorFoldSnapshot(null, null, null)

    fun update(value: TextFieldValue, completed: Pair<String, List<CodeFold>>? = null): EditorFoldSnapshot {
        val old = previous
        if (old != null && old.text != value.text) {
            val regions = snapshot.regions
            val change = if (regions.isNullOrEmpty()) null else editorTextChange(old, value)
            snapshot = EditorFoldSnapshot(if (change == null) regions else shiftedFoldRegions(regions!!, change), old.text, change)
        }
        if (completed?.first == value.text && snapshot.regions !== completed.second) {
            snapshot = snapshot.copy(regions = completed.second)
        }
        previous = value
        return snapshot
    }
}
