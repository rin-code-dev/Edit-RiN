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

    @Test fun changeTrackerReusesDocumentChangeBetweenSyntaxAndFolds() {
        val tracker = EditorChangeTracker()
        val initial = TextFieldValue("function hello() {\n  return 1;\n}\n")
        assertNull(tracker.update(initial))
        val edited = TextFieldValue("function hello() {\n  return 42;\n}\n", TextRange(29))
        val change = tracker.update(edited)
        assertNotNull(change)
        assertEquals(initial.text, change!!.before)
        assertEquals(edited.text, change.after)
        assertEquals(28, change.range.start)
        assertEquals(29, change.range.oldEnd)
        assertEquals(30, change.range.newEnd)
        assertEquals(1, change.range.delta)
    }

    @Test fun editorGutterRowFormatsLineNumbersAndFoldArrows() {
        val starts = listOf(0, 10, 25)
        val fold = CodeFold(10, 20)
        val folds = mapOf(1 to fold)

        // First line: starts at 0, no fold, line 1
        val row0 = editorGutterRow(0, 0, starts, folds, emptySet(), numbers = true, digits = 2)
        assertNotNull(row0)
        assertEquals(1, row0!!.logicalLine)
        assertEquals("  1", row0.label)
        assertNull(row0.fold)

        // Continuation offset inside line 1: binarySearch returns negative -> null
        val rowCont = editorGutterRow(1, 5, starts, folds, emptySet(), numbers = true, digits = 2)
        assertNull(rowCont)

        // Second line: fold open, expanded -> "▾ 2"
        val row1 = editorGutterRow(2, 10, starts, folds, emptySet(), numbers = true, digits = 2)
        assertNotNull(row1)
        assertEquals(2, row1!!.logicalLine)
        assertEquals("▾ 2", row1.label)
        assertEquals(fold, row1.fold)

        // Second line: collapsed -> "▸ 2"
        val row1Collapsed = editorGutterRow(2, 10, starts, folds, setOf(10), numbers = true, digits = 2)
        assertNotNull(row1Collapsed)
        assertEquals("▸ 2", row1Collapsed!!.label)
    }
}
