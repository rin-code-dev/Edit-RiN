package com.hikariatelier.app

import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BlockNativeSafetyTest {
    private fun session(sample: Boolean = false) = EditorSessionViewModel().apply {
        initialize(listOf(Work("one", "One", "circle(1, 2, 3);", blockEditorState = "A", isSample = sample),
            Work("two", "Two", "circle(1, 2, 3);")), "one")
    }
    private fun EditorSessionViewModel.metadata(state: String?, revision: String) =
        applyBlockEdit(BlockEditTransaction("one", editorValueState.value.text, emptyList(), state, revision))

    @Test fun sameSourceMetadataEditsRejectOldRevisionWithoutChangingHistory() {
        val session = session()
        val initial = session.currentBlockDocumentRevision("one")
        assertTrue(session.metadata("B", initial))
        val next = session.currentBlockDocumentRevision("one")
        assertNotEquals(initial, next)
        assertTrue(next.substringAfterLast(':').toLong() > initial.substringAfterLast(':').toLong())
        val undoCount = session.undoStack.size
        assertFalse(session.metadata("obsolete", initial))
        assertEquals("B", session.currentBlockEditorState("one"))
        assertEquals(undoCount, session.undoStack.size)
        assertEquals(next, session.currentBlockDocumentRevision("one"))
    }

    @Test fun directTextAndMetadataAbaCannotResurrectOldRevisions() {
        val session = session()
        val source = session.editorValueState.value
        val beforeText = session.currentBlockDocumentRevision("one")
        session.editorValueState.value = TextFieldValue("changed")
        session.editorValueState.value = source
        val afterText = session.currentBlockDocumentRevision("one")
        assertNotEquals(beforeText, afterText)
        assertFalse(session.metadata("obsolete", beforeText))
        assertTrue(session.metadata("B", afterText))
        assertTrue(session.metadata("A", session.currentBlockDocumentRevision("one")))
        assertEquals("A", session.currentBlockEditorState("one"))
        assertNotEquals(afterText, session.currentBlockDocumentRevision("one"))
        assertFalse(session.metadata("obsolete", afterText))
    }

    @Test fun undoRedoAndSameSourceWorkActivationAlwaysAdvanceAuthority() {
        val session = session()
        val source = session.editorValueState.value.text
        val initial = session.currentBlockDocumentRevision("one")
        assertTrue(session.applyBlockEdit(BlockEditTransaction("one", source,
            listOf(SourcePatch(7, 8, "9")), "B", initial)))
        val edited = session.currentBlockDocumentRevision("one")
        session.editorValueState.value = checkNotNull(session.undo(session.editorValueState.value))
        assertEquals(source, session.editorValueState.value.text)
        val undone = session.currentBlockDocumentRevision("one")
        assertNotEquals(initial, undone)
        session.editorValueState.value = checkNotNull(session.redo(session.editorValueState.value))
        assertNotEquals(edited, session.currentBlockDocumentRevision("one"))
        session.editorValueState.value = checkNotNull(session.undo(session.editorValueState.value))
        assertEquals(source, session.editorValueState.value.text)
        val beforeNavigation = session.currentBlockDocumentRevision("one")
        val activation = session.blockEditorActivationGeneration
        session.activateWorkEditor("two", source, false, setOf("one", "two"))
        session.activateWorkEditor("one", session.worksState.value.first().code, false, setOf("one", "two"))
        assertNotEquals(beforeNavigation, session.currentBlockDocumentRevision("one"))
        assertFalse(session.canApplyBlockAction("one", activation, requireEditable = false))
        assertFalse(session.metadata("obsolete", beforeNavigation))
    }

    @Test fun runtimeAndDraftEnvironmentChangesInvalidateOtherwiseIdenticalSource() {
        val session = session()
        val first = session.currentBlockDocumentRevision("one")
        val work = session.worksState.value.first()
        work.p5Version = P5_VERSION_LEGACY
        val runtime = session.currentBlockDocumentRevision("one")
        assertNotEquals(first, runtime)
        assertFalse(session.metadata("obsolete", first))
        val beforeAsset = session.currentBlockDocumentRevision("one")
        work.assets["image.png"] = ProjectAsset("a".repeat(64), 4, "image/png")
        assertNotEquals(beforeAsset, session.currentBlockDocumentRevision("one"))
        assertFalse(session.metadata("obsolete", beforeAsset))
        work.files[PROJECT_CONFIG_FILE] = "{}"
        val beforeDraft = session.currentBlockDocumentRevision("one")
        session.fileDrafts["one/$PROJECT_CONFIG_FILE"] = """{"executionMode":"module","moduleEntry":"sketch.js"}"""
        assertNotEquals(beforeDraft, session.currentBlockDocumentRevision("one"))
        assertFalse(session.metadata("obsolete", beforeDraft))
    }

    @Test fun productionBridgeRequiresStrictRevisionCorrelationAndPatchTypes() {
        fun message() = JSONObject().put("expectedSource", "source").put("expectedRevision", "r1")
            .put("requestId", "request-1").put("metadata", JSONObject.NULL)
            .put("patches", JSONArray().put(JSONObject().put("start", 0).put("end", 1).put("text", "s")))
        val valid = checkNotNull(decodeBlockEditTransaction("one", message()))
        assertEquals("r1", valid.expectedRevision)
        assertNull(valid.blockEditorState)
        for (bad in listOf(
            message().apply { remove("expectedRevision") }, message().put("expectedRevision", 4),
            message().put("expectedRevision", ""), message().apply { remove("requestId") },
            message().put("requestId", " "), message().put("expectedSource", 4),
            message().put("metadata", JSONObject()),
            message().apply { getJSONArray("patches").getJSONObject(0).put("start", .5) },
            message().apply { getJSONArray("patches").getJSONObject(0).put("text", 9) }
        )) assertNull(decodeBlockEditTransaction("one", bad))
        val ack = blockEditAckMessage("session", false, "one", "current", "layout", "r2", "request-1", "r1")
        assertFalse(ack.getBoolean("accepted"))
        assertEquals("request-1", ack.getString("requestId"))
        assertEquals("r1", ack.getString("expectedRevision"))
        assertEquals("r2", ack.getString("revision"))
    }

    @Test fun postFlushGuardRejectsWorkAbaAndLocksButAllowsReadonlyNavigation() = runBlocking {
        for (lockOnly in listOf(false, true)) {
            val session = session()
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Boolean>()
            session.blockEditorFlusher = { entered.complete(Unit); release.await() }
            val owner = session.activeWorkIdState.value
            val activation = session.blockEditorActivationGeneration
            val action = async {
                val flushed = session.flushBlockEditor()
                flushed to (flushed && session.canApplyBlockAction(owner, activation))
            }
            entered.await()
            if (lockOnly) session.editorInputLocked = true else {
                val source = session.editorValueState.value.text
                session.activateWorkEditor("two", source, true, setOf("one", "two"))
                session.activateWorkEditor("one", source, true, setOf("one", "two"))
            }
            release.complete(true)
            val (flushed, allowed) = action.await()
            assertFalse(allowed)
            if (!lockOnly) assertFalse(flushed)
        }
        val readonly = session(sample = true)
        assertFalse(readonly.canApplyBlockAction("one", readonly.blockEditorActivationGeneration))
        assertTrue(readonly.canApplyBlockAction("one", readonly.blockEditorActivationGeneration, requireEditable = false))
    }

    @Test fun effectiveFilesAndParserModeMatchPreviewIncludingEmptyAndOtherWorkDrafts() {
        val files = mapOf(PROJECT_CONFIG_FILE to "{}", "helper.js" to "saved")
        val moduleConfig = """{"executionMode":"module","moduleEntry":"sketch.js"}"""
        val drafts = mapOf("one/$PROJECT_CONFIG_FILE" to moduleConfig, "one/helper.js" to "", "two/helper.js" to "other", "one/unknown.js" to "ignored")
        val work = Work("one", "One", "const value = 1;", files = files.toMutableMap())
        val effective = effectiveProjectFiles(work.id, files, drafts)
        val input = capturePreviewRun(work, work.code, files, work.assets, drafts)
        assertEquals(effective, input.files)
        assertEquals("", effective["helper.js"])
        assertFalse(effective.containsKey("unknown.js"))
        assertEquals("module", blockEditorSourceType(work.id, work.code, files, drafts))
        assertEquals("script", blockEditorSourceType(work.id, work.code, files, emptyMap()))
        assertEquals("sketch.js", resolveProjectRun(input.files, readProjectDocumentConfig(input.files), input.source).modulePath)
    }
}
