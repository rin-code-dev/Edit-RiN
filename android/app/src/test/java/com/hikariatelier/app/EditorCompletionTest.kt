package com.hikariatelier.app

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.*
import org.junit.Test

class EditorCompletionTest {
    @Test fun modulesContributeSymbolsButMarkupStylesAndDataDoNot() {
        val sources = linkedMapOf(
            "scripts/helper.mjs" to "export function createModule() {}",
            "scripts/legacy.cjs" to "const createLegacy = 1",
            "index.html" to "function createMarkup() {}",
            "style.css" to "const createStyle = 1",
            "data.json" to "const createData = 1"
        )
        assertEquals(listOf("createModule", "createLegacy"), ProjectCompletionCache().symbols(sources).map { it.name })
        val value = TextFieldValue("cre", TextRange(3))
        assertTrue(projectCompletions(value, projectSymbols(sources), "index.html").isEmpty())
        assertEquals(CompletionCandidate("createModule", "scripts/helper.mjs"),
            projectCompletions(value, projectSymbols(sources), "scripts/helper.mjs").first())
    }
    @Test fun cacheReparsesOnlyChangedFilesAndRemovesDeletedFiles() {
        val parsed = mutableListOf<String>()
        val cache = ProjectCompletionCache { file, source -> parsed.add(file); projectSymbols(mapOf(file to source)) }
        val original = linkedMapOf("sketch.js" to "const main = 1", "helper.js" to "function helper() {}", "effect.frag" to "void main() {}")
        assertEquals(setOf("main", "helper"), cache.symbols(original).map { it.name }.toSet())
        assertEquals(listOf("sketch.js", "helper.js"), parsed)
        parsed.clear()
        cache.symbols(original.toMap())
        assertTrue(parsed.isEmpty())
        val changed = original + ("sketch.js" to "const changed = 1")
        assertEquals(setOf("changed", "helper"), cache.symbols(changed).map { it.name }.toSet())
        assertEquals(listOf("sketch.js"), parsed)
        assertEquals(listOf("changed"), cache.symbols(changed - "helper.js").map { it.name })
        parsed.clear()
        cache.symbols(changed)
        assertEquals(listOf("helper.js"), parsed)
    }
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
        assertEquals(listOf(CompletionCandidate("createCanvas")), projectCompletions(value, emptyList(), "sketch.js"))
    }
    @Test fun suggestionsIgnoreCaseButExcludeExactMatchAndShortPrefix() {
        assertEquals(listOf(CompletionCandidate("createCanvas")),
            projectCompletions(TextFieldValue("CRE", TextRange(3)), emptyList(), "sketch.js"))
        assertTrue(projectCompletions(TextFieldValue("createCanvas", TextRange(12)), emptyList(), "sketch.js").isEmpty())
        assertTrue(projectCompletions(TextFieldValue("c", TextRange(1)), emptyList(), "sketch.js").isEmpty())
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
