package com.hikariatelier.app

import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.*
import org.junit.Test

class BlockEditingTest {
    private fun session(state: String? = null) = EditorSessionViewModel().apply {
        initialize(listOf(Work("one", "One", "circle(1, 2, 3);", blockEditorState = state),
            Work("two", "Two", "second")), "one")
    }
    private fun EditorSessionViewModel.edit(source: String, state: String?): Boolean =
        applyBlockEdit(BlockEditTransaction("one", editorValueState.value.text,
            listOf(SourcePatch(0, editorValueState.value.text.length, source)), state))
    private fun EditorSessionViewModel.undoMain() {
        editorValueState.value = checkNotNull(undo(editorValueState.value))
    }
    private fun EditorSessionViewModel.redoMain() {
        editorValueState.value = checkNotNull(redo(editorValueState.value))
    }

    @Test fun openFieldRecoveryDoesNotCommitCodeOrHistoryAndRejectsObsoleteDocuments() {
        val session = session("original")
        val source = session.editorValueState.value.text
        val revision = session.currentBlockDocumentRevision("one")
        val draft = BlockEditTransaction("one", source, listOf(SourcePatch(7, 8, "123")), "input", revision)
        assertTrue(session.stageBlockInputDraft(draft))
        assertEquals("circle(123, 2, 3);" to "input", session.currentBlockInputDraft("one"))
        assertEquals(source, session.editorValueState.value.text)
        assertEquals("original", session.currentBlockEditorState("one"))
        assertTrue(session.undoStack.isEmpty())
        assertTrue(session.redoStack.isEmpty())
        session.notifyBlockInputChanged()
        assertTrue(session.blockEditorHasPendingInput)
        assertEquals("circle(123, 2, 3);" to "input", session.currentBlockInputDraft("one"))
        assertTrue(session.stageBlockInputDraft(draft))
        assertTrue(session.edit("circle(9, 2, 3);", "changed"))
        assertNull(session.currentBlockInputDraft("one"))
        assertFalse(session.stageBlockInputDraft(draft))
        val currentDraft = draft.copy(expectedSource = session.editorValueState.value.text,
            expectedRevision = session.currentBlockDocumentRevision("one"))
        assertTrue(session.stageBlockInputDraft(currentDraft))
        session.activateWorkEditor("two", "second", false, setOf("one", "two"))
        assertNull(session.currentBlockInputDraft("one"))
        assertFalse(session.stageBlockInputDraft(currentDraft))
    }

    @Test fun abandonedRedoEntriesReleaseTheirCodeAndBlockMetadataOnEveryEditingPath() {
        val historyField = EditorSessionViewModel::class.java.getDeclaredField("mainHistoryBlockStates").apply { isAccessible = true }
        for (path in listOf("code", "blocks", "parameter")) {
            val source = "let speed = 2.5; // @slider(0.1, 10.0, 0.1)"
            val session = EditorSessionViewModel().apply {
                initialize(listOf(Work("one", "One", source, blockEditorState = "original")), "one")
            }
            repeat(20) { index ->
                val before = session.editorValueState.value
                when (path) {
                    "blocks" -> assertTrue(session.edit(before.text + "\n// $index", "state-$index"))
                    "parameter" -> {
                        val parameter = workParameters(mapOf("sketch.js" to before.text)).single() as WorkParameter.Number
                        session.applyLiveParameterToEditor(parameter, "3.5")
                        session.commitLiveParameterChange()
                    }
                    else -> TextFieldValue(before.text + "\n// $index").let { next ->
                        session.applyChange(before, next)
                        session.editorValueState.value = next
                    }
                }
                assertTrue(session.redoStack.isEmpty())
                val retained = historyField.get(session) as Map<*, *>
                assertEquals("$path retained abandoned entries", session.undoStack.size, retained.size)
                session.undoMain()
                assertEquals(source, session.editorValueState.value.text)
                assertEquals("original", session.currentBlockEditorState("one"))
            }
        }
    }

    @Test fun sourcePatchesPreserveUntouchedTextAndRejectOverlapAndSplitSurrogates() {
        val source = "// comment\ncircle(1, 2, 3); // preserved"
        val offset = source.indexOf("1, 2, 3")
        assertEquals("// comment\ncircle(4, 2, 9); // preserved",
            applySourcePatches(source, listOf(SourcePatch(offset + 6, offset + 7, "9"), SourcePatch(offset, offset + 1, "4"))))
        assertNull(applySourcePatches(source, listOf(SourcePatch(0, 4, "a"), SourcePatch(2, 3, "b"))))
        assertNull(applySourcePatches(source, listOf(SourcePatch(-1, 0, "a"))))
        assertNull(applySourcePatches(source, listOf(SourcePatch(1, source.length + 1, "a"))))
        assertNull(applySourcePatches(source, listOf(SourcePatch(1, 1, "a"), SourcePatch(1, 1, "b"))))
        assertNull(applySourcePatches("😀", listOf(SourcePatch(1, 1, "x"))))
    }

    @Test fun sourceAndBlockStateUndoAndRedoTogetherAcrossCodeEdits() {
        val session = session("original")
        val original = session.editorValueState.value.text
        assertTrue(session.edit("circle(4, 2, 3);", "moved"))
        val blockSource = session.editorValueState.value
        val typed = TextFieldValue("// custom code\n${blockSource.text}")
        session.applyChange(blockSource, typed)
        session.editorValueState.value = typed
        session.undoMain()
        assertEquals(blockSource.text, session.editorValueState.value.text)
        assertEquals("moved", session.currentBlockEditorState("one"))
        session.undoMain()
        assertEquals(original, session.editorValueState.value.text)
        assertEquals("original", session.currentBlockEditorState("one"))
        assertFalse(session.blockEditorDrafts.containsKey("one"))
        session.redoMain()
        session.redoMain()
        assertEquals(typed.text, session.editorValueState.value.text)
        assertEquals("moved", session.currentBlockEditorState("one"))
    }

    @Test fun layoutOnlyActionsHaveDistinctHistoryWithoutChangingRuntimeSource() {
        val session = session("original")
        val text = session.editorValueState.value
        assertTrue(session.applyBlockEdit(BlockEditTransaction("one", text.text, emptyList(), "layout")))
        assertSame(text, session.editorValueState.value)
        assertEquals(1, session.undoStack.size)
        assertEquals("layout", session.currentBlockEditorState("one"))
        session.undoMain()
        assertEquals("original", session.currentBlockEditorState("one"))
        session.redoMain()
        assertEquals("layout", session.currentBlockEditorState("one"))
        assertTrue(session.applyBlockEdit(BlockEditTransaction("one", text.text, emptyList(), null)))
        assertTrue(session.blockEditorDrafts.containsKey("one"))
        assertNull(session.currentBlockEditorState("one"))
        session.undoMain()
        assertEquals("layout", session.currentBlockEditorState("one"))
    }

    @Test fun rejectedTransactionsDoNotChangeSourceMetadataOrHistory() {
        val session = session("original")
        val text = session.editorValueState.value.text
        for (transaction in listOf(
            BlockEditTransaction("other", text, emptyList(), "bad"),
            BlockEditTransaction("one", "obsolete", emptyList(), "bad"),
            BlockEditTransaction("one", text, listOf(SourcePatch(2, 1, "x")), "bad")
        )) assertFalse(session.applyBlockEdit(transaction))
        session.editorInputLocked = true
        assertFalse(session.applyBlockEdit(BlockEditTransaction("one", text, emptyList(), "bad")))
        assertEquals(text, session.editorValueState.value.text)
        assertEquals("original", session.currentBlockEditorState("one"))
        assertTrue(session.undoStack.isEmpty())
        assertTrue(session.redoStack.isEmpty())
    }

    @Test fun liveParametersKeepTheBlockStateAtTheirHistoryBoundary() {
        val source = "let speed = 2.5; // @slider(0.1, 10.0, 0.1)"
        val session = EditorSessionViewModel().apply {
            initialize(listOf(Work("one", "One", source, blockEditorState = "old")), "one")
        }
        assertTrue(session.applyBlockEdit(BlockEditTransaction("one", source, emptyList(), "layout")))
        val parameter = workParameters(mapOf("sketch.js" to source)).single() as WorkParameter.Number
        session.applyLiveParameterToEditor(parameter, "3.0")
        session.applyLiveParameterToEditor(parameter, "4.2")
        session.commitLiveParameterChange()
        session.undoMain()
        assertEquals(source, session.editorValueState.value.text)
        assertEquals("layout", session.currentBlockEditorState("one"))
        session.undoMain()
        assertEquals("old", session.currentBlockEditorState("one"))
        session.redoMain()
        session.redoMain()
        assertTrue(session.editorValueState.value.text.contains("4.2"))
        assertEquals("layout", session.currentBlockEditorState("one"))
    }

    @Test fun savedNavigationRetainsCoordinatedHistoryAndStoreReplacementClearsModeAndDrafts() {
        val session = session("original")
        assertTrue(session.edit("changed", "layout"))
        // Simulate a successful save before navigating.
        session.worksState.value.first().apply { code = "changed"; blockEditorState = "layout" }
        session.blockEditorDrafts.clear()
        session.lastSavedTextState.value = "changed"
        session.blockModeState("one").value = true
        session.activateWorkEditor("two", "second", true, setOf("one", "two"))
        session.activateWorkEditor("one", "changed", true, setOf("one", "two"))
        session.undoMain()
        assertEquals("original", session.currentBlockEditorState("one"))
        assertEquals("circle(1, 2, 3);", session.editorValueState.value.text)
        session.clearAuxiliaryEditors()
        assertFalse(session.blockModeState("one").value)
        assertTrue(session.blockEditorDrafts.isEmpty())
    }
}
