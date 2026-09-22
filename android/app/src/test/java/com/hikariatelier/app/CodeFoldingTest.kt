package com.hikariatelier.app

import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.*
import org.junit.Test

class CodeFoldingTest {
    @Test fun findsNestedMultilineBlocksButNotSingleLineOrUnclosedBlocks() {
        val source = "function draw() {\n if (true) {\n circle(1,2,3);\n }\n}\nconst a = {};\nfunction unfinished() {"
        val folds = codeFolds(source)
        assertEquals(2, folds.size)
        assertEquals("function draw() ".length, folds.first().open)
        assertTrue(folds[1].close < folds[0].close)
    }

    @Test fun ignoresCommentsStringsRegexAndNestedTemplates() {
        val source = """
            function draw() {
                // }
                const a = "escaped \" }";
                /* { } */
                const r = /[{}]\/x/;
                if (a) /}/.test(a);
                const t = `outer ${'$'}{`nested ${'$'}{"}"}`} { }`;
                const n = 10 / 2;
            }
        """.trimIndent()
        val folds = codeFolds(source)
        assertEquals(1, folds.size)
        assertEquals(source.lastIndexOf('}'), folds.single().close)
    }

    @Test fun projectionRetainsSourceAndOffsetsAcrossNestedAndAdjacentFolds() {
        val source = "function a() {\n if (x) {\n y();\n }\n}\nfunction b() {\n z();\n}\nend();"
        val folds = codeFolds(source)
        val projection = FoldProjection(source, folds, folds.map { it.open }.toSet())
        val displayed = projection.transform(AnnotatedString(source)).text.text
        assertEquals("function a() { … }\nfunction b() { … }\nend();", displayed)
        assertEquals(2, projection.hidden.size)
        val originalOffsets = (0..source.length).map(projection::originalToTransformed)
        val displayedOffsets = (0..displayed.length).map(projection::transformedToOriginal)
        assertTrue(originalOffsets.zipWithNext().all { it.first <= it.second })
        assertTrue(displayedOffsets.zipWithNext().all { it.first <= it.second })
        assertTrue(originalOffsets.all { it in 0..displayed.length })
        assertTrue(displayedOffsets.all { it in 0..source.length })
        assertEquals(source.indexOf("end"), projection.transformedToOriginal(displayed.indexOf("end")))
        assertEquals(source, FoldProjection(source, folds, emptySet()).transform(AnnotatedString(source)).text.text)
    }

    @Test fun shortEmptyBlockAndUnicodeHaveValidMappings() {
        val source = "日本語{\n}"
        val projection = FoldProjection(source, codeFolds(source), setOf(3))
        val displayed = projection.transform(AnnotatedString(source)).text.text
        assertEquals("日本語{ … }", displayed)
        assertEquals(displayed.length, projection.originalToTransformed(source.length))
        assertEquals(source.length, projection.transformedToOriginal(displayed.length))
    }

    @Test fun editsRebaseUnaffectedFoldsAndRevealChangedBlocks() {
        val source = "function a() {\n abc();\n}\n"
        val opening = source.indexOf('{')
        val state = CodeFoldState(source, setOf(opening))
        assertEquals(setOf(opening + 2), rebasedFolds(state, "\n\n" + source))
        assertEquals(setOf(opening), rebasedFolds(state, source + "tail"))
        assertTrue(rebasedFolds(state, source.replace("abc", "def")).isEmpty())
        assertTrue(rebasedFolds(state, "").isEmpty())
    }

    @Test fun accidentalDeletionInsideFoldIsDetectedButVisibleEditsAreAllowed() {
        val source = "function a() {\n abc();\n}"
        val folds = codeFolds(source)
        assertTrue(deletesFoldedCode(source, source.replace("abc", "ab"), folds))
        assertFalse(deletesFoldedCode(source, source.replace("function", "functio"), folds))
        assertFalse(deletesFoldedCode(source, "x" + source, folds))
    }
    @Test fun projectionReusesUnchangedResultsAndPreservesUnfoldedAnnotations() {
        val source = "function a() {\n x();\n}"
        val text = AnnotatedString(source)
        val folds = codeFolds(source)
        val expanded = FoldProjection(source, folds, emptySet()).transform(text)
        assertSame(text, expanded.text)
        val folded = FoldProjection(source, folds, setOf(folds.single().open))
        assertSame(folded.transform(text), folded.transform(text))
        val styled = AnnotatedString.Builder(source).apply {
            addStyle(androidx.compose.ui.text.SpanStyle(color = androidx.compose.ui.graphics.Color.Red), 0, 8)
        }.toAnnotatedString()
        val result = folded.transform(styled)
        assertEquals(1, result.text.spanStyles.size)
        assertEquals(8, result.text.spanStyles.single().end)
    }

}
