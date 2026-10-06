package com.hikariatelier.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class EditorHighlightCacheTest {
    private fun parsed(source: String) = JavaScriptHighlighter(false).filter(AnnotatedString(source)).text
    private fun tokens(text: AnnotatedString) = text.spanStyles.map { text.text.substring(it.start, it.end) }

    @Test fun pendingParseKeepsSyntaxOnBothSidesOfAnInsertion() {
        val source = "const a = 1;\nlet b = 2;"
        val cache = EditorHighlightCache()
        cache.highlight(AnnotatedString(source), parsed(source))
        val next = source.replace("a =", "alpha =")
        val result = cache.highlight(AnnotatedString(next))
        assertEquals(next, result.text)
        assertEquals(listOf("const", "1", "let", "2"), tokens(result))
    }

    @Test fun successiveDistantEditsPreserveSyntaxBetweenThemAndIgnoreOldCompletions() {
        val source = "// title\nconst center = 2;\n// end"
        val completed = parsed(source)
        val cache = EditorHighlightCache()
        cache.highlight(AnnotatedString(source), completed)
        val first = source.replace("title", "new title")
        cache.highlight(AnnotatedString(first), completed)
        val second = first.replace("end", "new end")
        val result = cache.highlight(AnnotatedString(second), completed)
        assertEquals(second, result.text)
        assertTrue("const" in tokens(result))
        assertTrue("2" in tokens(result))
        val refreshed = parsed(second)
        assertSame(refreshed, cache.highlight(AnnotatedString(second), refreshed))
    }

    @Test fun randomInsertionsDeletionsAndReplacementsNeverChangeTextOrProduceInvalidSpans() {
        val random = Random(83)
        var source = "const title = '日本語😀';\nlet count = 123;\n".repeat(100)
        val cache = EditorHighlightCache()
        cache.highlight(AnnotatedString(source), parsed(source))
        repeat(200) { step ->
            val start = random.nextInt(source.length + 1)
            val end = random.nextInt(start, source.length + 1)
            source = source.replaceRange(start, end, listOf("", "x", "\n", "let x = 1;")[step % 4])
            val result = cache.highlight(AnnotatedString(source))
            assertEquals(source, result.text)
            assertTrue(result.spanStyles.all { it.start >= 0 && it.start <= it.end && it.end <= source.length })
        }
        assertEquals(AnnotatedString(""), cache.highlight(AnnotatedString("")))
    }

    @Test fun errorBackgroundDoesNotLeakIntoTheSyntaxCacheOrAnotherDocument() {
        val source = "const a = 1;\nlet b = 2;"
        val cache = EditorHighlightCache()
        val syntax = cache.highlight(AnnotatedString(source), parsed(source))
        val diagnostic = withEditorErrorLines(syntax, false, setOf(2))
        assertEquals(1, diagnostic.spanStyles.count { it.item.background != Color.Unspecified })
        val next = cache.highlight(AnnotatedString(source + " "))
        assertTrue(next.spanStyles.all { it.item.background == Color.Unspecified })
        assertTrue(tokens(next).containsAll(listOf("const", "let")))
        assertSame(next, withEditorErrorLines(next, false, emptySet()))
        assertTrue(EditorHighlightCache().highlight(AnnotatedString(source)).spanStyles.isEmpty())
    }
}
