package com.hikariatelier.app

import android.net.Uri
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class WorkWorkflowTest {
    private open class Store : WorkPersistence {
        var calls = 0
        var mode: WorkSaveResult = WorkSaveResult.Saved
        var value = WorkStore(listOf(Work("a", "A", "saved", updatedAt = 123), Work("b", "B", "other")), "a")
        var callback: (() -> Unit)? = null
        override fun loadLocal() = value
        override fun loadFolder(folderUri: Uri) = value
        override fun save(folderUri: Uri?, works: List<Work>, activeId: String): Boolean {
            calls++; callback?.invoke()
            if (mode == WorkSaveResult.Saved) value = WorkStore(works.map(::snapshotWork), activeId)
            return mode == WorkSaveResult.Saved
        }
        override fun saveResult(folderUri: Uri?, works: List<Work>, activeId: String): WorkSaveResult {
            save(folderUri, works, activeId)
            return mode
        }
    }
    private fun session(store: Store) = EditorSessionViewModel().apply { initialize(store.value.works, "a") }
    private fun vm(session: EditorSessionViewModel, store: Store, scope: CoroutineScope) =
        WorkManagementViewModel(session, store, io = Dispatchers.Unconfined, operationScope = scope)

    @Test fun sampleLanguageChangesKeepUnsavedUserWorkAndCopiedComments() = runBlocking {
        val code = "// Tap to change the palette.\nconst value = 1;\n"
        val store = Store()
        val session = EditorSessionViewModel()
        val vm = vm(session, store, this)
        vm.initialize { listOf(Work("shapes", "Palette", code)) }
        session.editorValueState.value = TextFieldValue("unsaved user edit")
        vm.updateSampleLanguage("ja")
        assertEquals("unsaved user edit", session.editorValueState.value.text)
        assertEquals("saved", session.worksState.value.first { it.id == "a" }.code)
        val sample = vm.officialSamples.single()
        assertTrue(sample.code.contains("タップで配色"))
        session.activateWorkEditor(sample.id, sample.code, false, session.worksState.value.map { it.id }.toSet())
        vm.requestSampleCopy()
        vm.copySample("My palette", "")!!.join()
        val copy = session.worksState.value.first { it.id == session.activeWorkIdState.value }
        assertFalse(copy.isSample)
        val copiedCode = copy.code
        vm.updateSampleLanguage("zh")
        assertEquals(copiedCode, copy.code)
        assertEquals(copiedCode, session.editorValueState.value.text)
        session.activateWorkEditor(sample.id, sample.code, false, session.worksState.value.map { it.id }.toSet())
        vm.updateSampleLanguage("en")
        assertEquals(code, sample.code)
        assertEquals(code, session.editorValueState.value.text)
        assertEquals(code, session.lastSavedTextState.value)
    }

    @Test fun unchangedSavePreservesTimestampAndDeliversNavigationWithoutWriting() = runBlocking {
        val store = Store(); val session = session(store); val vm = vm(session, store, this)
        vm.saveCurrentWork(WorkEvent(openSettings = true))!!.join()
        assertEquals(0, store.calls)
        assertEquals(123L, session.worksState.value.first().updatedAt)
        assertTrue(vm.events.first().openSettings)
        assertEquals(WorkOperationState.SAVED, vm.operationState)
    }

    @Test fun lateInputWhileDuplicatingIsPreservedInOriginalWork() = runBlocking {
        val store = Store(); val session = session(store); val vm = vm(session, store, this)
        session.editorValueState.value = TextFieldValue("initial edit")
        store.callback = { session.editorValueState.value = TextFieldValue("late input") }
        val job = vm.duplicateWork()!!
        assertTrue(session.editorInputLocked)
        job.join()
        assertEquals("late input", store.value.works.first { it.id == "a" }.code)
        assertEquals("initial edit", store.value.works.last().code)
        assertFalse(session.editorInputLocked)
    }

    @Test fun unreadableStoreDoesNotLookLikeAnEmptySampleGallery() = runBlocking {
        val store = object : Store() {
            override fun loadResult(folderUri: Uri?, eager: Boolean) = WorkLoadResult.Failed(WorkLoadFailure.CORRUPT, "damaged")
        }
        val session = EditorSessionViewModel(); val vm = vm(session, store, this)
        vm.initialize { listOf(Work("sample", "Sample", "example")) }
        assertTrue(session.worksState.value.isEmpty())
        assertNotNull(vm.loadFailure)
        assertEquals(0, store.calls)
    }

    @Test fun restoreRequiresConfirmationAndKeepsInputUntilConfirmed() = runBlocking {
        val store = Store(); val session = session(store); val vm = vm(session, store, this)
        session.editorValueState.value = TextFieldValue("keep me")
        vm.requestRestoreCurrentWork()
        assertTrue(vm.showRestoreConfirmation)
        assertEquals("keep me", session.editorValueState.value.text)
        vm.restoreCurrentWork()!!.join()
        assertEquals("saved", session.editorValueState.value.text)
        assertFalse(vm.showRestoreConfirmation)
    }

    @Test fun lazySelectionLoadsTheBodyAndLocksInputDuringIo() = runBlocking {
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val store = object : Store() {
            override fun loadResult(folderUri: Uri?, eager: Boolean): WorkLoadResult = WorkLoadResult.Loaded(
                WorkStore(listOf(value.works.first(), Work("b", "B", "").also { it.bodyLoaded = false }), "a"),
                deferredWorkIds = setOf("b"))
            override fun loadWork(folderUri: Uri?, workId: String): Work? {
                entered.complete(Unit)
                runBlocking { release.await() }
                return value.works.find { it.id == workId }
            }
        }
        val session = EditorSessionViewModel()
        val vm = WorkManagementViewModel(session, store, io = Dispatchers.IO, operationScope = this)
        vm.initialize { emptyList() }
        assertFalse(session.worksState.value.last().bodyLoaded)
        val switch = vm.selectWork("b", false)!!
        entered.await()
        assertEquals("a", session.activeWorkIdState.value)
        assertTrue(session.editorInputLocked)
        release.complete(Unit); switch.join()
        assertEquals("other", session.editorValueState.value.text)
        assertFalse(session.editorInputLocked)
        assertEquals(0, store.calls)
    }

    @Test fun conflictRetryAndFailedMergeCannotOverwriteExternalWork() = runBlocking {
        val store = Store(); val session = session(store); val vm = vm(session, store, this)
        session.editorValueState.value = TextFieldValue("edited A")
        store.mode = WorkSaveResult.Conflict
        vm.saveCurrentWork()!!.join()
        store.value = WorkStore(session.worksState.value.map { snapshotWork(it).apply { if (id == "b") code = "external B" } }, "a")
        store.mode = WorkSaveResult.Failed
        vm.mergeExternalChanges()!!.join()
        val attempted = store.calls
        store.mode = WorkSaveResult.Saved
        vm.saveCurrentWork()!!.join()
        assertEquals(attempted, store.calls)
        assertEquals("external B", store.value.works.last().code)
        assertEquals("edited A", session.editorValueState.value.text)
        vm.mergeExternalChanges()!!.join()
        assertEquals("external B", store.value.works.first { it.id == "b" }.code)
        assertEquals("edited A", store.value.works.first { it.id == "a" }.code)
    }

    @Test fun mergingConcurrentEditsKeepsBothVersionsAndGalleryMetadata() {
        val base = Work("a", "A", "before", isPinned = true, tags = listOf("tag"))
        val mine = snapshotWork(base).apply { code = "mine" }
        val theirs = snapshotWork(base).apply { code = "theirs" }
        val result = mergeConcurrentWorks(listOf(base), listOf(mine), listOf(theirs), "a")
        assertEquals(1, result.copies)
        assertEquals(setOf("mine", "theirs"), result.works.map { it.code }.toSet())
        assertNotEquals("a", result.activeId)
        assertTrue(result.works.last().isPinned)
        assertEquals(listOf("tag"), result.works.last().tags.toList())
    }

    @Test fun explicitRecoveryKeepsAuxiliaryDraftsFromEveryWorkWithoutReplacingIds() {
        val snapshot = DraftSnapshot("a", "main draft", 1)
        val recovered = recoveredDraftWorks(snapshot, mapOf("a/helper.js" to "helper draft", "b/other.js" to "other draft"),
            listOf(Work("a", "A", "saved"), Work("b", "B", "other")), sameStore = false)
        assertEquals(2, recovered.size)
        assertTrue(recovered.none { it.id == "a" || it.id == "b" })
        assertEquals("main draft", recovered.first().code)
        assertEquals("other draft", recovered.last().files["other.js"])
    }
}
