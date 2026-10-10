package com.hikariatelier.app

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class EditorFoldAnalysisTest {
    @Test fun rapidEditsShiftUntouchedCollapsedBlocksWithoutParsingOrAcceptingStaleResults() {
        val original = "// title\nfunction a() {\n run();\n}\nfunction b() {\n other();\n}\n"
        val analysis = EditorFoldAnalysis()
        val folds = codeFolds(original)
        var value = TextFieldValue(original, TextRange(3))
        analysis.update(value, original to folds)
        repeat(100) { step ->
            val position = value.selection.start
            value = TextFieldValue(value.text.replaceRange(position, position, "x"), TextRange(position + 1))
            val result = analysis.update(value, original to folds)
            assertEquals(folds.map { CodeFold(it.open + step + 1, it.close + step + 1) }, result.regions)
        }
        assertEquals(codeFolds(value.text), analysis.update(value, value.text to codeFolds(value.text)).regions)
    }

    @Test fun caretHintsAndFallbackAlwaysDescribeAnExactReplacement() {
        val random = Random(132)
        var value = TextFieldValue("function a() {\n 日本語😀();\n}\n".repeat(500))
        repeat(500) {
            val start = random.nextInt(value.text.length + 1)
            val end = random.nextInt(start, value.text.length + 1)
            val insertion = listOf("x", "\n", "", "{}", "日本語")[it % 5]
            val nextText = value.text.replaceRange(start, end, insertion)
            val before = value.copy(selection = TextRange(start, end))
            val next = TextFieldValue(nextText, TextRange(start + insertion.length))
            for (input in listOf(before, value.copy(selection = TextRange.Zero))) {
                val change = editorTextChange(input, next)
                assertEquals(next.text, input.text.replaceRange(change.start, change.oldEnd,
                    next.text.substring(change.start, change.newEnd)))
            }
            value = next
        }
    }

    @Test fun editInsideOneBlockOpensItButPreservesAndShiftsTheOtherBlock() {
        val source = "function a() {\n run();\n}\nfunction b() {\n other();\n}"
        val folds = codeFolds(source)
        val position = source.indexOf("run") + 1
        val analysis = EditorFoldAnalysis()
        analysis.update(TextFieldValue(source, TextRange(position)), source to folds)
        val next = TextFieldValue(source.replaceRange(position, position, "long"), TextRange(position + 4))
        assertEquals(listOf(CodeFold(folds.last().open + 4, folds.last().close + 4)), analysis.update(next).regions)
        // A selection-only update must not discard or rebuild the region list.
        val first = analysis.update(next)
        assertSame(first.regions, analysis.update(next.copy(selection = TextRange.Zero)).regions)
    }
}
