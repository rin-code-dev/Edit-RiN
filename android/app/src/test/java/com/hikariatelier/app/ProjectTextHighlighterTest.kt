package com.hikariatelier.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectTextHighlighterTest {
    @Test fun markupAndStylesKeepSourceAnnotationsAndEveryOffset() {
        listOf(
            "index.html" to "<!-- draw() -->\n<div class=\"panel\">Hello</div>",
            "styles/main.css" to "/* colors */\n.panel { color: #33aaff; width: 50%; }",
            "data/settings.json" to "{\"draw\": true, \"size\": 400}",
            "notes.txt" to "function draw() { plain text }"
        ).forEach { (file, source) ->
            val annotated = AnnotatedString.Builder(source).apply {
                addStringAnnotation("selected", "value", 0, 1)
                addStyle(SpanStyle(background = Color.Red), 0, 1)
            }.toAnnotatedString()
            val transformed = projectFileHighlighter(file, false, emptySet()).filter(annotated)
            assertEquals(source, transformed.text.text)
            assertEquals(annotated.getStringAnnotations(0, 1), transformed.text.getStringAnnotations(0, 1))
            assertTrue(transformed.text.spanStyles.contains(annotated.spanStyles.single()))
            (0..source.length).forEach { offset ->
                assertEquals(offset, transformed.offsetMapping.originalToTransformed(offset))
                assertEquals(offset, transformed.offsetMapping.transformedToOriginal(offset))
            }
        }
    }

    @Test fun longUnfinishedMarkupAndCssCommentsDoNotRecurse() {
        listOf("index.html" to "<!-- ", "style.css" to "/* ").forEach { (file, prefix) ->
            val source = prefix + "unfinished\n".repeat(25_000)
            val transformed = projectFileHighlighter(file, true, emptySet()).filter(AnnotatedString(source))
            assertEquals(source, transformed.text.text)
            assertEquals(1, transformed.text.spanStyles.size)
            assertEquals(source.length, transformed.text.spanStyles.single().end)
        }
    }

    @Test fun plainTextDoesNotReceiveJavaScriptSyntaxStyles() {
        val source = "const value = 2; draw();"
        val transformed = projectFileHighlighter("README", true, emptySet()).filter(AnnotatedString(source))
        assertEquals(AnnotatedString(source), transformed.text)
    }
}
