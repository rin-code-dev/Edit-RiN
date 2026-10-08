package com.hikariatelier.app

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.*
import org.junit.Test

class EditorInteractionsTest {
    @Test fun readOnlySampleCanFoldAndExpandWithoutEditingItsCode() {
        val source = "function draw() {\n  background(0);\n  circle(10, 10, 20);\n}\n"
        val regions = codeFolds(source)
        val fold = regions.single()
        val current = TextFieldValue(source, TextRange(source.indexOf("circle")))
        var selected = current
        val collapsed = toggleEditorFold(source, current, emptySet(), regions, fold)!!
        dispatchEditorValueChange(current, collapsed.value, true, { selected = it },
            { fail("Folding must not change source or history") })
        assertEquals(source, selected.text)
        assertEquals(TextRange(fold.open), selected.selection)
        assertFalse(selected.selection.start > fold.open + 1 && selected.selection.start < fold.close)
        val projection = FoldProjection(source, regions, collapsed.state.collapsed)
        assertFalse(projection.transform(AnnotatedString(source)).text.text.contains("circle"))
        val expanded = toggleEditorFold(source, selected, collapsed.state.collapsed, regions, fold)!!
        assertEquals(source, FoldProjection(source, regions, expanded.state.collapsed)
            .transform(AnnotatedString(source)).text.text)
        assertEquals(source, expanded.value.text)
    }

    @Test fun togglingOneFoldKeepsOtherFoldsAndRejectsStaleInput() {
        val source = "function a() {\n  x();\n}\nfunction b() {\n  y();\n}\n"
        val regions = codeFolds(source)
        val value = TextFieldValue(source, composition = TextRange(0, 3))
        val toggle = toggleEditorFold(source, value, setOf(regions.last().open), regions, regions.first())!!
        assertEquals(regions.map { it.open }.toSet(), toggle.state.collapsed)
        assertNull(toggle.value.composition)
        assertNull(toggleEditorFold(source, TextFieldValue("new source"), emptySet(), regions, regions.first()))
        assertNull(toggleEditorFold(source, value, emptySet(), regions, CodeFold(0, 1)))
    }

    @Test fun searchTargetsTheMatchWithinALongWrappedLine() {
        val source = "x".repeat(2000) + "needle" + "y".repeat(2000)
        val match = searchProject(mapOf("sketch.js" to source), "needle", true).matches.single()
        val target = EditorNavigationTarget(match.file, match.line, TextRange(match.start, match.end))
        assertEquals(2000, target.sourceOffset(source))
        assertEquals(0, EditorNavigationTarget("sketch.js", 1).sourceOffset(source))
    }

    @Test fun sourceOffsetsRemainCorrectAfterFoldingAndForReversedSelections() {
        val source = "function a() {\n  x();\n}\n" + "x".repeat(500) + "needle"
        val start = source.indexOf("needle")
        val target = EditorNavigationTarget("helper.js", 4, TextRange(start + 6, start))
        val offset = target.sourceOffset(source)!!
        val folds = codeFolds(source)
        val projection = FoldProjection(source, folds, folds.map { it.open }.toSet())
        val displayed = projection.transform(AnnotatedString(source)).text.text
        assertEquals(displayed.indexOf("needle"), projection.originalToTransformed(offset))
        assertEquals(sourceLineOffset(source, 4),
            EditorNavigationTarget("helper.js", 4, TextRange(source.length + 10)).sourceOffset(source))
        assertNull(EditorNavigationTarget("helper.js", 100).sourceOffset(source))
    }

    @Test fun foldArrowAndErrorNumberHaveIndependentActions() {
        assertEquals(EditorGutterAction.FOLD, editorGutterAction(true, true, true))
        assertEquals(EditorGutterAction.ERROR, editorGutterAction(true, true, false))
        assertEquals(EditorGutterAction.FOLD, editorGutterAction(true, false, false))
        assertEquals(EditorGutterAction.ERROR, editorGutterAction(false, true, false))
        assertNull(editorGutterAction(false, false, false))
    }

    @Test fun readOnlySelectionCanChangeWithoutWritingTextOrHistory() {
        val current = TextFieldValue("sample code")
        var selection: TextFieldValue? = null
        var editCalls = 0
        dispatchEditorValueChange(current, current.copy(selection = TextRange(6, 0)), true,
            { selection = it }, { editCalls++ })
        assertEquals("sample code", selection!!.text)
        assertEquals(TextRange(6, 0), selection!!.selection)
        dispatchEditorValueChange(current, TextFieldValue("replacement"), true,
            { fail("Text replacement reached the selection callback") }, { editCalls++ })
        assertEquals(0, editCalls)
    }

    @Test fun editableTextAndImeCompositionUseTheirRespectiveCallbacks() {
        val current = TextFieldValue("日本語")
        val composing = current.copy(selection = TextRange(3), composition = TextRange(0, 3))
        var selection: TextFieldValue? = null
        var edited: TextFieldValue? = null
        dispatchEditorValueChange(current, composing, false, { selection = it }, { edited = it })
        assertEquals(composing, selection)
        assertNull(edited)
        val next = TextFieldValue("日本語入力", TextRange(5), TextRange(0, 5))
        dispatchEditorValueChange(composing, next, false, { fail("Edit treated as selection") }, { edited = it })
        assertEquals(next, edited)
    }
}
