package com.hikariatelier.app

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

class EditorSelectionTest {
    @Test fun ordinarySymbolReplacesSelection() {
        val result = insertAtSelection(TextFieldValue("abcde", TextRange(1, 4)), ";")
        assertEquals("a;e", result.text)
        assertEquals(TextRange(2), result.selection)
    }

    @Test fun bracketPairsWrapReversedSelection() {
        val result = insertAtSelection(TextFieldValue("abcde", TextRange(4, 1)), "(", ")")
        assertEquals("a(bcd)e", result.text)
    }

    @Test fun indentDoesNotAffectLineAfterSelectionEnd() {
        val result = changeLineIndent(TextFieldValue("one\ntwo\nthree", TextRange(0, 4)), true)
        assertEquals("  one\ntwo\nthree", result.text)
    }

    @Test fun activeWatchLinesEmptyWhenUnfocusedOrNoLines() {
        assertEquals(emptySet<Int>(), resolveActiveWatchLines(TextRange(0), 10, listOf(0), isFocused = false))
        assertEquals(emptySet<Int>(), resolveActiveWatchLines(TextRange(0), 10, emptyList(), isFocused = true))
    }

    @Test fun activeWatchLinesSingleCursorLine() {
        val lineStarts = listOf(0, 10, 20) // line 1: 0..9, line 2: 10..19, line 3: 20..30
        assertEquals(setOf(1), resolveActiveWatchLines(TextRange(5), 30, lineStarts, isFocused = true))
        assertEquals(setOf(2), resolveActiveWatchLines(TextRange(15), 30, lineStarts, isFocused = true))
        assertEquals(setOf(3), resolveActiveWatchLines(TextRange(25), 30, lineStarts, isFocused = true))
    }

    @Test fun activeWatchLinesMultilineSelection() {
        val lineStarts = listOf(0, 10, 20, 30)
        assertEquals(setOf(1, 2), resolveActiveWatchLines(TextRange(5, 15), 40, lineStarts, isFocused = true))
        assertEquals(setOf(1, 2, 3), resolveActiveWatchLines(TextRange(25, 2), 40, lineStarts, isFocused = true))
        assertEquals(setOf(1, 2, 3, 4), resolveActiveWatchLines(TextRange(0, 35), 40, lineStarts, isFocused = true))
    }
}
