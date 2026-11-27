package com.hikariatelier.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BlockEditorPresentationTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    @Before fun resetPreferences() {
        context.getSharedPreferences("block_editor_presentation", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun displayPreferencesAndViewportSurviveStoreRecreation() {
        val store = BlockEditorPresentationStore(context)
        assertFalse(store.easyMode())
        assertFalse(store.easyModeHintDismissed())
        val display = BlockEditorDisplayPreferences(18.0, "comfortable")
        val first = BlockEditorViewport(-120.0, -450.0, 1.5, "bound_one")
        val second = BlockEditorViewport(-15.0, -20.0, 0.75, null)
        store.saveEasyMode(true)
        store.saveDisplayPreferences(display)
        store.saveViewport("one", first)
        store.saveViewport("two", second)
        val reloaded = BlockEditorPresentationStore(context)
        assertTrue(reloaded.easyMode())
        assertTrue(reloaded.easyModeHintDismissed())
        assertEquals(display, reloaded.displayPreferences())
        assertEquals(first, reloaded.viewport("one"))
        assertEquals(second, reloaded.viewport("two"))
        assertNull(reloaded.viewport("unknown"))
    }

    @Test fun viewportChangesAreWorkScopedAndDoNotInvalidateSourceOrHistory() {
        val session = EditorSessionViewModel().apply { initialize(listOf(Work("one", "One", "circle(1,2,3);"), Work("two", "Two", "")), "one") }
        val revision = session.currentBlockDocumentRevision("one")
        val text = session.editorValueState.value
        val historySize = session.undoStack.size
        val view = BlockEditorViewport(-20.0, -30.0, 1.25, "bound_one")
        assertTrue(session.rememberBlockEditorViewport("one", view))
        assertFalse(session.rememberBlockEditorViewport("two", view))
        assertEquals(view, session.blockEditorViewport("one"))
        assertNull(session.blockEditorViewport("two"))
        assertEquals(revision, session.currentBlockDocumentRevision("one"))
        assertEquals(text, session.editorValueState.value)
        assertEquals(historySize, session.undoStack.size)
        assertNull(session.currentBlockEditorState("one"))
        session.activateWorkEditor("two", "", false, setOf("one", "two"))
        assertEquals(view, session.blockEditorViewport("one"))
    }

    @Test fun malformedBridgePresentationValuesAreRejectedWithoutCoercion() {
        fun valid() = JSONObject().put("x", 0).put("y", 0).put("scale", 1).put("selected", JSONObject.NULL)
        assertNotNull(decodeBlockEditorViewport(valid()))
        for (bad in listOf(valid().put("x", "0"), valid().put("y", 1e10), valid().put("scale", 0.1), valid().put("scale", 3), valid().put("selected", JSONObject()), valid().put("selected", "x".repeat(257))))
            assertNull(decodeBlockEditorViewport(bad))
        assertNull(decodeBlockEditorViewport(null))
        assertNull(decodeBlockEditorDisplayPreferences(JSONObject().put("fontSize", "18").put("density", "standard")))
        assertNull(decodeBlockEditorDisplayPreferences(JSONObject().put("fontSize", 100).put("density", "standard")))
        assertNull(decodeBlockEditorDisplayPreferences(JSONObject().put("fontSize", 18).put("density", "unknown")))
    }

    @Test fun corruptPresentationPreferencesDoNotBreakDocumentLoading() {
        context.getSharedPreferences("block_editor_presentation", Context.MODE_PRIVATE).edit()
            .putString("display", "broken").putString("view:one", "broken").commit()
        val store = BlockEditorPresentationStore(context)
        assertEquals(BlockEditorDisplayPreferences(), store.displayPreferences())
        assertNull(store.viewport("one"))
    }
}
