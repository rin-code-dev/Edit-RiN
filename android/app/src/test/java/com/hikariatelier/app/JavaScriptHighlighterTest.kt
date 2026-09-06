package com.hikariatelier.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JavaScriptHighlighterTest {
    @Test
    fun longEscapedStringsKeepTextAndAllOffsetsWithoutRecursion() {
        val source = "const text = \"" + "a\\\"b".repeat(50_000) + "\";"
        val result = JavaScriptHighlighter(false).filter(AnnotatedString(source))
        assertEquals(source, result.text.text)
        listOf(0, 1, source.length / 2, source.length).forEach { offset ->
            assertEquals(offset, result.offsetMapping.originalToTransformed(offset))
            assertEquals(offset, result.offsetMapping.transformedToOriginal(offset))
        }
        assertEquals(2, result.text.spanStyles.size)
    }

    @Test
    fun incompleteCommentsAndTemplatesStayOneToken() {
        listOf("/* unfinished\nconst fake = 1;", "`template\nconst fake = 1;")
            .forEach { source ->
                val spans = JavaScriptHighlighter(false).filter(AnnotatedString(source)).text.spanStyles
                assertEquals(1, spans.size)
                assertEquals(0, spans.single().start)
                assertEquals(source.length, spans.single().end)
            }
    }

    @Test
    fun cacheDoesNotDiscardChangedInputAnnotations() {
        val highlighter = JavaScriptHighlighter(false)
        highlighter.filter(AnnotatedString("sample"))
        val annotated = AnnotatedString.Builder("sample").apply {
            addStyle(SpanStyle(background = Color.Red), 0, 3)
            addStringAnnotation("tag", "value", 1, 4)
        }.toAnnotatedString()
        val result = highlighter.filter(annotated).text
        assertEquals(annotated, result)
    }

    @Test
    fun identifiersAreNotPartiallyHighlightedAndNumericFormsRemainWhole() {
        val source = "\$const draw_more 0xff 1.2e-3 .5 0b10 12n"
        val result = JavaScriptHighlighter(false).filter(AnnotatedString(source)).text
        val tokens = result.spanStyles.map { source.substring(it.start, it.end) }
        assertEquals(listOf("0xff", "1.2e-3", ".5", "0b10", "12n"), tokens)
    }

    @Test
    fun errorsStyleOnlyTheirOriginalLogicalLine() {
        val source = "const a = 1;\n\nconst b = 2;"
        val result = JavaScriptHighlighter(false, setOf(2, 3)).filter(AnnotatedString(source)).text
        val errorSpans = result.spanStyles.filter { it.item.background != Color.Unspecified }
        assertEquals(2, errorSpans.size)
        assertTrue(errorSpans.all { it.start >= source.indexOf('\n') + 1 })
        assertEquals(source, result.text)
    }
}
