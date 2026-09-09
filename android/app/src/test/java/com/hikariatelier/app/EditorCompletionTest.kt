package com.hikariatelier.app

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.*
import org.junit.Test

class EditorCompletionTest {
    @Test fun prefixIsLimitedToIdentifierAtCursor() {
        assertEquals("cre", completionPrefix(TextFieldValue("let x = cre + other", TextRange(11))))
        assertEquals("", completionPrefix(TextFieldValue("create", TextRange(0, 6))))
        assertEquals("", completionPrefix(TextFieldValue("create ", TextRange(7))))
        assertEquals("", completionPrefix(TextFieldValue("create", TextRange(0))))
    }
    @Test fun completionWorksAtEndOfLargeDocument() {
        val code = "// long document\n".repeat(100000) + "cre"
        val value = TextFieldValue(code, TextRange(code.length))
        assertEquals("cre", completionPrefix(value))
        assertEquals(listOf("createCanvas"), editorCompletions(value))
    }
    @Test fun suggestionsIgnoreCaseButExcludeExactMatchAndShortPrefix() {
        assertEquals(listOf("createCanvas"), editorCompletions(TextFieldValue("CRE", TextRange(3))))
        assertTrue(editorCompletions(TextFieldValue("createCanvas", TextRange(12))).isEmpty())
        assertTrue(editorCompletions(TextFieldValue("c", TextRange(1))).isEmpty())
    }
}
