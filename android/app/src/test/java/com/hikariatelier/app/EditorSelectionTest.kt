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
}
