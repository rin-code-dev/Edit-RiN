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
        assertEquals("\$brush", completionPrefix(TextFieldValue("\$brush", TextRange(6))))
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

    @Test fun projectCompletionUsesCurrentAndOtherFilesBeforeBundledNames() {
        val symbols = projectSymbols(mapOf(
            "helper.js" to "function createParticles() {}\nconst createPalette = 1",
            "sketch.js" to "let createShape = 1\nclass createBrush {}"
        ))
        val value = TextFieldValue("cre", TextRange(3))
        assertEquals(listOf(
            CompletionCandidate("createShape", "sketch.js"),
            CompletionCandidate("createBrush", "sketch.js"),
            CompletionCandidate("createParticles", "helper.js"),
            CompletionCandidate("createPalette", "helper.js"),
            CompletionCandidate("createCanvas")
        ), projectCompletions(value, symbols, "sketch.js"))
    }

    @Test fun projectCompletionIgnoresCommentsStringsAndDuplicateNames() {
        val symbols = projectSymbols(mapOf("sketch.js" to """
            // function pretendComment() {}
            /* const pretendBlock = 1 */
            const text = "class pretendString {}";
            const template = `let pretendTemplate = 1`;
            const pattern = /function pretendRegex[\/] = 1/;
            function realName() {}
            function realName() {}
        """.trimIndent()))
        assertEquals(listOf("text", "template", "pattern", "realName"), symbols.map { it.name })
        assertEquals(listOf(CompletionCandidate("realName", "sketch.js")),
            projectCompletions(TextFieldValue("rea", TextRange(3)), symbols, "sketch.js"))
    }

    @Test fun projectNameOverridesDuplicateBundledName() {
        val symbols = projectSymbols(mapOf("sketch.js" to "function createCanvas() {}"))
        assertEquals(listOf(CompletionCandidate("createCanvas", "sketch.js")),
            projectCompletions(TextFieldValue("cre", TextRange(3)), symbols, "sketch.js"))
    }
}
