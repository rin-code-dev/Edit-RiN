package com.hikariatelier.app

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.*
import org.junit.Test

class EditorSessionViewModelTest {
    private fun seed(session: EditorSessionViewModel, key: String) {
        session.fileDrafts[key] = "draft"
        session.fileEditorValues[key] = mutableStateOf(TextFieldValue("draft"))
        session.fileUndoStacks[key] = mutableStateListOf(TextFieldValue("before"))
        session.fileRedoStacks[key] = mutableStateListOf(TextFieldValue("after"))
    }

    @Test fun reloadingWorkDiscardsItsStaleTabsWithoutTouchingOtherWorks() {
        val session = EditorSessionViewModel()
        seed(session, "one/helper.js")
        seed(session, "one-more/helper.js")
        val generation = session.auxiliaryEditorGeneration
        session.clearAuxiliaryEditors("one")
        assertFalse(session.fileDrafts.containsKey("one/helper.js"))
        assertFalse(session.fileEditorValues.containsKey("one/helper.js"))
        assertFalse(session.fileUndoStacks.containsKey("one/helper.js"))
        assertFalse(session.fileRedoStacks.containsKey("one/helper.js"))
        assertEquals("draft", session.fileDrafts["one-more/helper.js"])
        assertTrue(session.fileUndoStacks.containsKey("one-more/helper.js"))
        assertTrue(session.auxiliaryEditorGeneration > generation)
        val restored = session.fileEditorValues.getOrPut("one/helper.js") {
            mutableStateOf(TextFieldValue("restored from backup"))
        }
        assertEquals("restored from backup", restored.value.text)
    }

    @Test fun replacingStoreAlsoReleasesCachedTabsWithoutDrafts() {
        val session = EditorSessionViewModel()
        seed(session, "work/helper.js")
        session.fileDrafts.clear()
        session.clearAuxiliaryEditors()
        assertTrue(session.fileDrafts.isEmpty())
        assertTrue(session.fileEditorValues.isEmpty())
        assertTrue(session.fileUndoStacks.isEmpty())
        assertTrue(session.fileRedoStacks.isEmpty())
    }
    @Test fun replacingOneFileKeepsSiblingDraftsAndUndoHistory() {
        val session = EditorSessionViewModel()
        seed(session, "one/helper.js")
        seed(session, "one/keep.js")
        seed(session, "two/helper.js")
        session.clearAuxiliaryEditor("one", "helper.js")
        assertFalse(session.fileDrafts.containsKey("one/helper.js"))
        assertFalse(session.fileUndoStacks.containsKey("one/helper.js"))
        assertEquals("draft", session.fileDrafts["one/keep.js"])
        assertTrue(session.fileUndoStacks.containsKey("one/keep.js"))
        assertEquals("draft", session.fileDrafts["two/helper.js"])
    }

}
